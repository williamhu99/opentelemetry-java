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

package io.opentelemetry.sdk.extensions.zpages;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An {@link HttpHanlder} that will be used to render HTML pages using any {@code ZPageHandler}. */
final class ZPageHttpHandler implements HttpHandler {
  // The corresponding ZPageHandler for the zPage (e.g. TracezZPageHandler)
  private final ZPageHandler zpageHandler;

  /** Constructs a new {@code ZPageHttpHandler}. */
  ZPageHttpHandler(ZPageHandler zpageHandler) {
    this.zpageHandler = zpageHandler;
  }

  /**
   * Build a query map from the {@code uri}.
   *
   * @param uri the {@link URI} for buiding the query map
   * @return the query map built based on the @{code uri}
   */
  @VisibleForTesting
  static ImmutableMap<String, String> queryMapBuilder(URI uri) {
    String queryStrings = uri.getQuery();
    if (queryStrings == null) {
      return ImmutableMap.of();
    }
    Map<String, String> queryMap = new HashMap<String, String>();
    for (String param : Splitter.on("&").split(queryStrings)) {
      List<String> keyValuePair = Splitter.on("=").splitToList(param);
      if (keyValuePair.size() > 1) {
        queryMap.put(keyValuePair.get(0), keyValuePair.get(1));
      } else {
        queryMap.put(keyValuePair.get(0), "");
      }
    }
    return ImmutableMap.copyOf(queryMap);
  }

  @Override
  public final void handle(HttpExchange httpExchange) throws IOException {
    try {
      httpExchange.sendResponseHeaders(200, 0);
      zpageHandler.emitHtml(
          queryMapBuilder(httpExchange.getRequestURI()), httpExchange.getResponseBody());
    } finally {
      httpExchange.close();
    }
  }
}