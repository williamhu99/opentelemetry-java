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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.TraceFlags;
import io.opentelemetry.trace.TraceId;
import io.opentelemetry.trace.TraceState;
import java.util.Collection;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Unit tests for {@link TracezSpanProcessor}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracezSpanProcessorTest {
  private static final String SPAN_NAME = "span";
  private static final SpanContext SAMPLED_SPAN_CONTEXT =
      SpanContext.create(
          TraceId.getInvalid(),
          SpanId.getInvalid(),
          TraceFlags.builder().setIsSampled(true).build(),
          TraceState.builder().build());
  private static final SpanContext NOT_SAMPLED_SPAN_CONTEXT = SpanContext.getInvalid();
  private static final Status SPAN_STATUS = Status.UNKNOWN;

  private static void assertSpanCacheSizes(
      TracezSpanProcessor spanProcessor, int runningSpanCacheSize, int completedSpanCacheSize) {
    Collection<ReadableSpan> runningSpans = spanProcessor.getRunningSpans();
    Collection<ReadableSpan> completedSpans = spanProcessor.getCompletedSpans();
    assertThat(runningSpans.size()).isEqualTo(runningSpanCacheSize);
    assertThat(completedSpans.size()).isEqualTo(completedSpanCacheSize);
  }

  @Mock private ReadableSpan readableSpan;
  @Mock private SpanData spanData;

  @Test
  void onStart_sampledSpan_inCache() {
    TracezSpanProcessor spanProcessor = TracezSpanProcessor.newBuilder().build();
    /* Return a sampled span, which should be added to the running cache by default */
    when(readableSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);
    spanProcessor.onStart(readableSpan);
    assertSpanCacheSizes(spanProcessor, 1, 0);
  }

  @Test
  void onEnd_sampledSpan_inCache() {
    TracezSpanProcessor spanProcessor = TracezSpanProcessor.newBuilder().build();
    /* Return a sampled span, which should be added to the completed cache upon ending */
    when(readableSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);
    when(readableSpan.getName()).thenReturn(SPAN_NAME);
    spanProcessor.onStart(readableSpan);
    when(readableSpan.toSpanData()).thenReturn(spanData);
    when(spanData.getStatus()).thenReturn(SPAN_STATUS);
    spanProcessor.onEnd(readableSpan);
    assertSpanCacheSizes(spanProcessor, 0, 1);
  }

  @Test
  void onStart_notSampledSpan_inCache() {
    TracezSpanProcessor spanProcessor = TracezSpanProcessor.newBuilder().build();
    /* Return a non-sampled span, which should not be added to the running cache by default */
    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    spanProcessor.onStart(readableSpan);
    assertSpanCacheSizes(spanProcessor, 1, 0);
  }

  @Test
  void onEnd_notSampledSpan_notInCache() {
    TracezSpanProcessor spanProcessor = TracezSpanProcessor.newBuilder().build();
    /* Return a non-sampled span, which should not be added to the running cache by default */
    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    spanProcessor.onStart(readableSpan);
    spanProcessor.onEnd(readableSpan);
    assertSpanCacheSizes(spanProcessor, 0, 0);
  }

  @Test
  void build_sampledFlagTrue_notInCache() {
    /* Initialize a TraceZSpanProcessor that only looks at sampled spans */
    Properties properties = new Properties();
    properties.setProperty("otel.zpages.export.sampled", "true");
    TracezSpanProcessor spanProcessor =
        TracezSpanProcessor.newBuilder().readProperties(properties).build();

    /* Return a non-sampled span, which should not be added to the completed cache */
    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    spanProcessor.onStart(readableSpan);
    assertSpanCacheSizes(spanProcessor, 1, 0);
    spanProcessor.onEnd(readableSpan);
    assertSpanCacheSizes(spanProcessor, 0, 0);
  }

  @Test
  void build_sampledFlagFalse_inCache() {
    /* Initialize a TraceZSpanProcessor that looks at all spans */
    Properties properties = new Properties();
    properties.setProperty("otel.zpages.export.sampled", "false");
    TracezSpanProcessor spanProcessor =
        TracezSpanProcessor.newBuilder().readProperties(properties).build();

    /* Return a non-sampled span, which should be added to the caches */
    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    when(readableSpan.getName()).thenReturn(SPAN_NAME);
    spanProcessor.onStart(readableSpan);
    assertSpanCacheSizes(spanProcessor, 1, 0);
    when(readableSpan.toSpanData()).thenReturn(spanData);
    when(spanData.getStatus()).thenReturn(SPAN_STATUS);
    spanProcessor.onEnd(readableSpan);
    assertSpanCacheSizes(spanProcessor, 0, 1);
  }
}
