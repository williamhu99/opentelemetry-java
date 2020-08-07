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

package io.opentelemetry.opentracingshim.testbed.activespanreplacement;

import static io.opentelemetry.opentracingshim.testbed.TestUtils.finishedSpansSize;
import static io.opentelemetry.opentracingshim.testbed.TestUtils.sleep;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
class ActiveSpanReplacementTest {

  private final TracerSdkProvider sdk = TracerSdkProvider.builder().build();
  private final InMemoryTracing inMemoryTracing =
      InMemoryTracing.builder().setTracerProvider(sdk).build();
  private final Tracer tracer = TraceShim.createTracerShim(sdk, new CorrelationContextManagerSdk());
  private final ExecutorService executor = Executors.newCachedThreadPool();

  @Test
  void test() throws Exception {
    // Start an isolated task and query for its result in another task/thread
    Span span = tracer.buildSpan("initial").start();
    try (Scope scope = tracer.scopeManager().activate(span)) {
      // Explicitly pass a Span to be finished once a late calculation is done.
      submitAnotherTask(span);
    }

    await()
        .atMost(15, TimeUnit.SECONDS)
        .until(finishedSpansSize(inMemoryTracing.getSpanExporter()), equalTo(3));

    List<SpanData> spans = inMemoryTracing.getSpanExporter().getFinishedSpanItems();
    assertEquals(3, spans.size());
    assertEquals("initial", spans.get(0).getName()); // Isolated task
    assertEquals("subtask", spans.get(1).getName());
    assertEquals("task", spans.get(2).getName());

    // task/subtask are part of the same trace, and subtask is a child of task
    assertEquals(spans.get(1).getTraceId(), spans.get(2).getTraceId());
    assertEquals(spans.get(2).getSpanId(), spans.get(1).getParentSpanId());

    // initial task is not related in any way to those two tasks
    assertNotEquals(spans.get(0).getTraceId(), spans.get(1).getTraceId());
    assertFalse(spans.get(0).getParentSpanId().isValid());

    assertNull(tracer.scopeManager().activeSpan());
  }

  private void submitAnotherTask(final Span initialSpan) {

    executor.submit(
        () -> {
          // Create a new Span for this task
          Span taskSpan = tracer.buildSpan("task").start();
          try (Scope scope = tracer.scopeManager().activate(taskSpan)) {

            // Simulate work strictly related to the initial Span
            // and finish it.
            try (Scope initialScope = tracer.scopeManager().activate(initialSpan)) {
              sleep(50);
            } finally {
              initialSpan.finish();
            }

            // Restore the span for this task and create a subspan
            Span subTaskSpan = tracer.buildSpan("subtask").start();
            try (Scope subTaskScope = tracer.scopeManager().activate(subTaskSpan)) {
              sleep(50);
            } finally {
              subTaskSpan.finish();
            }
          } finally {
            taskSpan.finish();
          }
        });
  }
}
