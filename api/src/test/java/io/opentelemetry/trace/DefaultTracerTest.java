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

package io.opentelemetry.trace;

import static com.google.common.truth.Truth.assertThat;

import io.grpc.Context;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DefaultTracer}. */
@RunWith(JUnit4.class)
// Need to suppress warnings for MustBeClosed because Android 14 does not support
// try-with-resources.
@SuppressWarnings("MustBeClosedChecker")
public class DefaultTracerTest {
  private static final Tracer defaultTracer = DefaultTracer.getInstance();
  private static final String SPAN_NAME = "MySpanName";
  private static final byte[] firstBytes =
      new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 'a'};
  private static final SpanContext spanContext =
      SpanContext.create(
          TraceId.fromBytes(firstBytes, 0),
          SpanId.fromBytes(firstBytes, 8),
          TraceFlags.getDefault(),
          TraceState.getDefault());

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void defaultGetCurrentSpan() {
    assertThat(defaultTracer.getCurrentSpan()).isInstanceOf(DefaultSpan.class);
  }

  @Test
  public void getCurrentSpan_WithSpan() {
    assertThat(defaultTracer.getCurrentSpan()).isInstanceOf(DefaultSpan.class);
    try (Scope ws = defaultTracer.withSpan(DefaultSpan.getInvalid())) {
      assertThat(defaultTracer.getCurrentSpan()).isInstanceOf(DefaultSpan.class);
    }
    assertThat(defaultTracer.getCurrentSpan()).isInstanceOf(DefaultSpan.class);
  }

  @Test
  public void spanBuilderWithName_NullName() {
    thrown.expect(NullPointerException.class);
    defaultTracer.spanBuilder(null);
  }

  @Test
  public void defaultSpanBuilderWithName() {
    assertThat(defaultTracer.spanBuilder(SPAN_NAME).startSpan()).isInstanceOf(DefaultSpan.class);
  }

  @Test
  public void testInProcessContext() {
    Span span = defaultTracer.spanBuilder(SPAN_NAME).startSpan();
    try (Scope scope = defaultTracer.withSpan(span)) {
      assertThat(defaultTracer.getCurrentSpan()).isEqualTo(span);
      Span secondSpan = defaultTracer.spanBuilder(SPAN_NAME).startSpan();
      try (Scope secondScope = defaultTracer.withSpan(secondSpan)) {
        assertThat(defaultTracer.getCurrentSpan()).isEqualTo(secondSpan);
      } finally {
        assertThat(defaultTracer.getCurrentSpan()).isEqualTo(span);
      }
    }
    assertThat(defaultTracer.getCurrentSpan()).isInstanceOf(DefaultSpan.class);
  }

  @Test
  public void testSpanContextPropagationExplicitParent() {
    Span span = defaultTracer.spanBuilder(SPAN_NAME).setParent(spanContext).startSpan();
    assertThat(span.getContext()).isSameInstanceAs(spanContext);
  }

  @Test
  public void testSpanContextPropagation() {
    DefaultSpan parent = new DefaultSpan(spanContext);

    Span span = defaultTracer.spanBuilder(SPAN_NAME).setParent(parent).startSpan();
    assertThat(span.getContext()).isSameInstanceAs(spanContext);
  }

  @Test
  public void noSpanContextMakesInvalidSpans() {
    Span span = defaultTracer.spanBuilder(SPAN_NAME).startSpan();
    assertThat(span.getContext()).isSameInstanceAs(SpanContext.getInvalid());
  }

  @Test
  public void testSpanContextPropagation_nullContext() {
    thrown.expect(NullPointerException.class);
    defaultTracer.spanBuilder(SPAN_NAME).setParent((Context) null);
  }

  @Test
  public void testSpanContextPropagation_fromContext() {
    Context context = TracingContextUtils.withSpan(new DefaultSpan(spanContext), Context.current());

    Span span = defaultTracer.spanBuilder(SPAN_NAME).setParent(context).startSpan();
    assertThat(span.getContext()).isSameInstanceAs(spanContext);
  }

  @Test
  public void testSpanContextPropagationCurrentSpan() {
    DefaultSpan parent = new DefaultSpan(spanContext);
    try (Scope scope = defaultTracer.withSpan(parent)) {
      Span span = defaultTracer.spanBuilder(SPAN_NAME).startSpan();
      assertThat(span.getContext()).isSameInstanceAs(spanContext);
    }
  }

  @Test
  public void testSpanContextPropagationCurrentSpanContext() {
    Context context =
        TracingContextUtils.withSpan(DefaultSpan.create(spanContext), Context.current());
    try (Scope scope = ContextUtils.withScopedContext(context)) {
      Span span = defaultTracer.spanBuilder(SPAN_NAME).startSpan();
      assertThat(span.getContext()).isSameInstanceAs(spanContext);
    }
  }
}
