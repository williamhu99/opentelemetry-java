/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.sdk.contrib.zpages;

import static com.google.common.base.Preconditions.checkState;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import java.io.IOException;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A collection of HTML pages to display stats and trace data and allow library configuration
 * control.
 *
 * <p>Example usage with private {@link HttpServer}
 *
 * <pre>{@code
 * public class Main {
 *   public static void main(String[] args) throws Exception {
 *     ZPageServer.startHttpServerAndRegisterAllPages(8000);
 *     ... // do work
 *   }
 * }
 * }</pre>
 *
 * <p>Example usage with shared {@link HttpServer}
 *
 * <pre>{@code
 * public class Main {
 *   public static void main(String[] args) throws Exception {
 *     HttpServer server = HttpServer.create(new InetSocketAddress(8000), 10);
 *     ZPageServer.registerAllPagesToHttpServer(server);
 *     server.start();
 *     ... // do work
 *   }
 * }
 * }</pre>
 */
@ThreadSafe
public final class ZPageServer {
  // The maximum number of queued incoming connections allowed on the HttpServer listening socket.
  private static final int HTTPSERVER_BACKLOG = 5;
  // Length of time to wait for the HttpServer to stop
  private static final int HTTPSERVER_STOP_DELAY = 1;
  // Tracez SpanProcessor and DataAggregator for constructing TracezZPageHandler
  private static final TracezSpanProcessor tracezSpanProcessor =
      TracezSpanProcessor.newBuilder().build();
  private static final TracezDataAggregator tracezDataAggregator =
      new TracezDataAggregator(tracezSpanProcessor);
  // Handler for /tracez page
  private static final ZPageHandler tracezZPageHandler =
      TracezZPageHandler.create(tracezDataAggregator);

  private static final Object mutex = new Object();
  private static volatile boolean isTracezSpanProcesserAdded = false;

  @GuardedBy("mutex")
  @Nullable
  private static HttpServer server;

  /** Function that adds the {@link TracezSpanProcessor} to the {@link tracerSdkProvider}. */
  private static void addTracezSpanProcessor() {
    if (!isTracezSpanProcesserAdded) {
      synchronized (mutex) {
        if (isTracezSpanProcesserAdded) {
          return;
        }
        TracerSdkProvider tracerProvider = OpenTelemetrySdk.getTracerProvider();
        tracerProvider.addSpanProcessor(tracezSpanProcessor);
        isTracezSpanProcesserAdded = true;
      }
    }
  }

  /**
   * Returns a {@code ZPageHandler} for tracing debug. The page displays information about all
   * running spans and all sampled spans based on latency and error.
   *
   * <p>It displays a summary table which contains one row for each span name and data about number
   * of running and sampled spans.
   *
   * <p>Clicking on a cell in the table with a number that is greater than zero will display
   * detailed information about that span.
   *
   * @return a {@code ZPageHandler} for tracing debug.
   */
  public static ZPageHandler getTracezZPageHandler() {
    addTracezSpanProcessor();
    return tracezZPageHandler;
  }

  /**
   * Registers all zPages to the given {@link HttpServer} {@code server}.
   *
   * @param server the server that exports the zPages.
   */
  public static void registerAllPagesToHttpServer(HttpServer server) {
    // For future zPages, register them to the server in here
    server.createContext(
        tracezZPageHandler.getUrlPath(), new ZPageHttpHandler(getTracezZPageHandler()));
  }

  /** Method for stopping the {@link HttpServer} {@code server}. */
  private static void stop() {
    synchronized (mutex) {
      checkState(server != null, "The HttpServer is already stopped.");
      server.stop(HTTPSERVER_STOP_DELAY);
      server = null;
    }
  }

  /**
   * Starts a private {@link HttpServer} and registers all zPages to it. When the JVM shuts down the
   * server is stopped.
   *
   * <p>Users can only call this function once per process.
   *
   * @param port the port used to bind the {@link HttpServer} {@code server}
   * @throws IllegalStateException if the server is already started.
   * @throws IOException if the server cannot bind to the specified port.
   */
  public static void startHttpServerAndRegisterAllPages(int port) throws IOException {
    synchronized (mutex) {
      checkState(server == null, "The HttpServer is already started.");
      server = HttpServer.create(new InetSocketAddress(port), HTTPSERVER_BACKLOG);
      ZPageServer.registerAllPagesToHttpServer(server);
      server.start();
    }

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                ZPageServer.stop();
              }
            });
  }

  /** For testing purpose only. */
  public static boolean getIsTracezSpanProcesserAdded() {
    return isTracezSpanProcesserAdded;
  }

  private ZPageServer() {}
}
