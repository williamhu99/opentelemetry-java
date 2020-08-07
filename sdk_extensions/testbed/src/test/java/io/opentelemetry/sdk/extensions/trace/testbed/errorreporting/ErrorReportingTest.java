/*
 * Copyright 2019, OpenTelemetry Authors
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

package io.opentelemetry.sdk.extensions.trace.testbed.errorreporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.context.Scope;
import io.opentelemetry.exporters.inmemory.InMemoryTracing;
import io.opentelemetry.sdk.extensions.trace.testbed.TestUtils;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.SpanData.Event;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@SuppressWarnings("FutureReturnValueIgnored")
public final class ErrorReportingTest {

  private final TracerSdkProvider sdk = TracerSdkProvider.builder().build();
  private final InMemoryTracing inMemoryTracing =
      InMemoryTracing.builder().setTracerProvider(sdk).build();
  private final Tracer tracer = sdk.get(ErrorReportingTest.class.getName());
  private final ExecutorService executor = Executors.newCachedThreadPool();

  /* Very simple error handling **/
  @Test
  void testSimpleError() {
    Span span = tracer.spanBuilder("one").startSpan();
    try (Scope ignored = tracer.withSpan(span)) {
      throw new RuntimeException("Invalid state");
    } catch (Exception e) {
      span.setStatus(Status.UNKNOWN);
    } finally {
      span.end();
    }

    assertThat(tracer.getCurrentSpan()).isSameAs(DefaultSpan.getInvalid());

    List<SpanData> spans = inMemoryTracing.getSpanExporter().getFinishedSpanItems();
    assertThat(spans).hasSize(1);
    assertThat(spans.get(0).getStatus().getCanonicalCode().value())
        .isEqualTo(Status.UNKNOWN.getCanonicalCode().value());
  }

  /* Error handling in a callback capturing/activating the Span */
  @Test
  void testCallbackError() {
    final Span span = tracer.spanBuilder("one").startSpan();
    executor.submit(
        () -> {
          try (Scope ignored = tracer.withSpan(span)) {
            throw new RuntimeException("Invalid state");
          } catch (Exception exc) {
            span.setStatus(Status.UNKNOWN);
          } finally {
            span.end();
          }
        });

    await()
        .atMost(5, TimeUnit.SECONDS)
        .until(TestUtils.finishedSpansSize(inMemoryTracing.getSpanExporter()), equalTo(1));

    List<SpanData> spans = inMemoryTracing.getSpanExporter().getFinishedSpanItems();
    assertThat(spans).hasSize(1);
    assertThat(spans.get(0).getStatus().getCanonicalCode())
        .isEqualTo(Status.UNKNOWN.getCanonicalCode());
  }

  /* Error handling for a max-retries task (such as url fetching).
   * We log the error at each retry. */
  @Test
  void testErrorRecovery() {
    final int maxRetries = 1;
    int retries = 0;
    Span span = tracer.spanBuilder("one").startSpan();
    try (Scope ignored = tracer.withSpan(span)) {
      while (retries++ < maxRetries) {
        try {
          throw new RuntimeException("No url could be fetched");
        } catch (final Exception exc) {
          span.addEvent("error");
        }
      }
    }

    span.setStatus(Status.UNKNOWN); // Could not fetch anything.
    span.end();

    assertThat(tracer.getCurrentSpan()).isSameAs(DefaultSpan.getInvalid());

    List<SpanData> spans = inMemoryTracing.getSpanExporter().getFinishedSpanItems();
    assertThat(spans).hasSize(1);
    assertThat(spans.get(0).getStatus().getCanonicalCode())
        .isEqualTo(Status.UNKNOWN.getCanonicalCode());

    List<Event> events = spans.get(0).getEvents();
    assertEquals(events.size(), maxRetries);
    assertEquals(events.get(0).getName(), "error");
  }

  /* Error handling for a mocked layer automatically capturing/activating
   * the Span for a submitted Runnable. */
  @Test
  void testInstrumentationLayer() {
    Span span = tracer.spanBuilder("one").startSpan();
    try (Scope ignored = tracer.withSpan(span)) {
      // ScopedRunnable captures the active Span at this time.
      executor.submit(
          new ScopedRunnable(
              () -> {
                try {
                  throw new RuntimeException("Invalid state");
                } catch (Exception exc) {
                  tracer.getCurrentSpan().setStatus(Status.UNKNOWN);
                } finally {
                  tracer.getCurrentSpan().end();
                }
              },
              tracer));
    }

    await()
        .atMost(5, TimeUnit.SECONDS)
        .until(TestUtils.finishedSpansSize(inMemoryTracing.getSpanExporter()), equalTo(1));

    List<SpanData> spans = inMemoryTracing.getSpanExporter().getFinishedSpanItems();
    assertEquals(spans.size(), 1);
    assertEquals(spans.get(0).getStatus().getCanonicalCode(), Status.UNKNOWN.getCanonicalCode());
  }

  private static class ScopedRunnable implements Runnable {
    Runnable runnable;
    Tracer tracer;
    Span span;

    private ScopedRunnable(Runnable runnable, Tracer tracer) {
      this.runnable = runnable;
      this.tracer = tracer;
      this.span = tracer.getCurrentSpan();
    }

    @Override
    public void run() {
      // No error reporting is done, as we are a simple wrapper.
      try (Scope ignored = tracer.withSpan(span)) {
        runnable.run();
      }
    }
  }
}
