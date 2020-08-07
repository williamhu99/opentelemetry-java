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

package io.opentelemetry.opentracingshim.testbed.nestedcallbacks;

import static io.opentelemetry.opentracingshim.testbed.TestUtils.finishedSpansSize;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.opentelemetry.common.ReadableAttributes;
import io.opentelemetry.exporters.inmemory.InMemoryTracing;
import io.opentelemetry.opentracingshim.TraceShim;
import io.opentelemetry.sdk.correlationcontext.CorrelationContextManagerSdk;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@SuppressWarnings("FutureReturnValueIgnored")
public final class NestedCallbacksTest {

  private final TracerSdkProvider sdk = TracerSdkProvider.builder().build();
  private final InMemoryTracing inMemoryTracing =
      InMemoryTracing.builder().setTracerProvider(sdk).build();
  private final Tracer tracer = TraceShim.createTracerShim(sdk, new CorrelationContextManagerSdk());
  private final ExecutorService executor = Executors.newCachedThreadPool();

  @Test
  void test() {

    Span span = tracer.buildSpan("one").start();
    submitCallbacks(span);

    await()
        .atMost(15, TimeUnit.SECONDS)
        .until(finishedSpansSize(inMemoryTracing.getSpanExporter()), equalTo(1));

    List<SpanData> spans = inMemoryTracing.getSpanExporter().getFinishedSpanItems();
    assertEquals(1, spans.size());
    assertEquals("one", spans.get(0).getName());

    ReadableAttributes attrs = spans.get(0).getAttributes();
    assertEquals(3, attrs.size());
    for (int i = 1; i <= 3; i++) {
      assertEquals(
          Integer.toString(i), spans.get(0).getAttributes().get("key" + i).getStringValue());
    }

    assertNull(tracer.scopeManager().activeSpan());
  }

  private void submitCallbacks(final Span span) {

    executor.submit(
        () -> {
          try (Scope scope = tracer.scopeManager().activate(span)) {
            span.setTag("key1", "1");

            executor.submit(
                () -> {
                  try (Scope scope12 = tracer.scopeManager().activate(span)) {
                    span.setTag("key2", "2");

                    executor.submit(
                        () -> {
                          try (Scope scope1 = tracer.scopeManager().activate(span)) {
                            span.setTag("key3", "3");
                          } finally {
                            span.finish();
                          }
                        });
                  }
                });
          }
        });
  }
}
