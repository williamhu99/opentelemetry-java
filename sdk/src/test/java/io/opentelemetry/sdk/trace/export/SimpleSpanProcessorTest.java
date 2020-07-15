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

package io.opentelemetry.sdk.trace.export;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.sdk.common.export.ConfigBuilderTest.ConfigTester;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.Samplers;
import io.opentelemetry.sdk.trace.TestUtils;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessorTest.WaitingSpanExporter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.TraceFlags;
import io.opentelemetry.trace.TraceId;
import io.opentelemetry.trace.TraceState;
import io.opentelemetry.trace.Tracer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link SimpleSpanProcessor}. */
@RunWith(JUnit4.class)
public class SimpleSpanProcessorTest {
  private static final long MAX_SCHEDULE_DELAY_MILLIS = 500;
  private static final String SPAN_NAME = "MySpanName";
  @Mock private ReadableSpan readableSpan;
  @Mock private SpanExporter spanExporter;
  private final TracerSdkProvider tracerSdkFactory = TracerSdkProvider.builder().build();
  private final Tracer tracer = tracerSdkFactory.get("SimpleSpanProcessor");
  private static final SpanContext SAMPLED_SPAN_CONTEXT =
      SpanContext.create(
          TraceId.getInvalid(),
          SpanId.getInvalid(),
          TraceFlags.builder().setIsSampled(true).build(),
          TraceState.builder().build());
  private static final SpanContext NOT_SAMPLED_SPAN_CONTEXT = SpanContext.getInvalid();

  private SimpleSpanProcessor simpleSampledSpansProcessor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    simpleSampledSpansProcessor = SimpleSpanProcessor.newBuilder(spanExporter).build();
  }

  @Test
  public void configTest() {
    Map<String, String> options = new HashMap<>();
    options.put("otel.ssp.export.sampled", "false");
    SimpleSpanProcessor.Builder config =
        SimpleSpanProcessor.newBuilder(spanExporter)
            .fromConfigMap(options, ConfigTester.getNamingDot());
    assertThat(config.getExportOnlySampled()).isEqualTo(false);
  }

  @Test
  public void configTest_EmptyOptions() {
    SimpleSpanProcessor.Builder config =
        SimpleSpanProcessor.newBuilder(spanExporter)
            .fromConfigMap(Collections.emptyMap(), ConfigTester.getNamingDot());
    assertThat(config.getExportOnlySampled())
        .isEqualTo(SimpleSpanProcessor.Builder.DEFAULT_EXPORT_ONLY_SAMPLED);
  }

  @Test
  public void onStartSync() {
    simpleSampledSpansProcessor.onStart(readableSpan);
    verifyNoInteractions(spanExporter);
  }

  @Test
  public void onEndSync_SampledSpan() {
    SpanData spanData = TestUtils.makeBasicSpan();
    when(readableSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);
    when(readableSpan.toSpanData()).thenReturn(spanData);
    simpleSampledSpansProcessor.onEnd(readableSpan);
    verify(spanExporter).export(Collections.singletonList(spanData));
  }

  @Test
  public void onEndSync_NotSampledSpan() {
    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    simpleSampledSpansProcessor.onEnd(readableSpan);
    verifyNoInteractions(spanExporter);
  }

  @Test
  public void onEndSync_OnlySampled_NotSampledSpan() {
    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    when(readableSpan.toSpanData())
        .thenReturn(TestUtils.makeBasicSpan())
        .thenThrow(new RuntimeException());
    SimpleSpanProcessor simpleSpanProcessor = SimpleSpanProcessor.newBuilder(spanExporter).build();
    simpleSpanProcessor.onEnd(readableSpan);
    verifyNoInteractions(spanExporter);
  }

  @Test
  public void onEndSync_OnlySampled_SampledSpan() {
    when(readableSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);
    when(readableSpan.toSpanData())
        .thenReturn(TestUtils.makeBasicSpan())
        .thenThrow(new RuntimeException());
    SimpleSpanProcessor simpleSpanProcessor = SimpleSpanProcessor.newBuilder(spanExporter).build();
    simpleSpanProcessor.onEnd(readableSpan);
    verify(spanExporter).export(Collections.singletonList(TestUtils.makeBasicSpan()));
  }

  @Test
  public void tracerSdk_NotSampled_Span() {
    WaitingSpanExporter waitingSpanExporter = new WaitingSpanExporter(1);

    tracerSdkFactory.addSpanProcessor(
        BatchSpanProcessor.newBuilder(waitingSpanExporter)
            .setScheduleDelayMillis(MAX_SCHEDULE_DELAY_MILLIS)
            .build());

    TestUtils.startSpanWithSampler(tracerSdkFactory, tracer, SPAN_NAME, Samplers.alwaysOff())
        .startSpan()
        .end();
    TestUtils.startSpanWithSampler(tracerSdkFactory, tracer, SPAN_NAME, Samplers.alwaysOff())
        .startSpan()
        .end();

    Span span = tracer.spanBuilder(SPAN_NAME).startSpan();
    span.end();

    // Spans are recorded and exported in the same order as they are ended, we test that a non
    // sampled span is not exported by creating and ending a sampled span after a non sampled span
    // and checking that the first exported span is the sampled span (the non sampled did not get
    // exported).
    List<SpanData> exported = waitingSpanExporter.waitForExport();
    // Need to check this because otherwise the variable span1 is unused, other option is to not
    // have a span1 variable.
    assertThat(exported).containsExactly(((ReadableSpan) span).toSpanData());
  }

  @Test
  public void tracerSdk_NotSampled_RecordingEventsSpan() {
    // TODO(bdrutu): Fix this when Sampler return RECORD option.
    /*
    tracer.addSpanProcessor(
        BatchSpanProcessor.newBuilder(waitingSpanExporter)
            .setScheduleDelayMillis(MAX_SCHEDULE_DELAY_MILLIS)
            .reportOnlySampled(false)
            .build());

    io.opentelemetry.trace.Span span =
        tracer
            .spanBuilder("FOO")
            .setSampler(Samplers.neverSample())
            .startSpanWithSampler();
    span.end();

    List<SpanData> exported = waitingSpanExporter.waitForExport(1);
    assertThat(exported).containsExactly(((ReadableSpan) span).toSpanData());
    */
  }

  @Test
  public void onEndSync_ExporterReturnError() {
    SpanData spanData = TestUtils.makeBasicSpan();
    when(readableSpan.getSpanContext()).thenReturn(SAMPLED_SPAN_CONTEXT);
    when(readableSpan.toSpanData()).thenReturn(spanData);
    simpleSampledSpansProcessor.onEnd(readableSpan);
    // Try again, now will no longer return error.
    simpleSampledSpansProcessor.onEnd(readableSpan);
    verify(spanExporter, times(2)).export(Collections.singletonList(spanData));
  }

  @Test
  public void shutdown() {
    simpleSampledSpansProcessor.shutdown();
    verify(spanExporter).shutdown();
  }

  @Test
  public void buildFromProperties_defaultSampledFlag() {
    Properties properties = new Properties();
    SimpleSpanProcessor spanProcessor =
        SimpleSpanProcessor.newBuilder(spanExporter).readProperties(properties).build();

    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    spanProcessor.onEnd(readableSpan);
    verifyNoInteractions(spanExporter);
  }

  @Test
  public void buildFromProperties_onlySampledTrue() {
    Properties properties = new Properties();
    properties.setProperty("otel.ssp.export.sampled", "true");
    SimpleSpanProcessor spanProcessor =
        SimpleSpanProcessor.newBuilder(spanExporter).readProperties(properties).build();

    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    spanProcessor.onEnd(readableSpan);
    verifyNoInteractions(spanExporter);
  }

  @Test
  public void buildFromProperties_onlySampledFalse() {
    Properties properties = new Properties();
    properties.setProperty("otel.ssp.export.sampled", "false");
    SimpleSpanProcessor spanProcessor =
        SimpleSpanProcessor.newBuilder(spanExporter).readProperties(properties).build();
    SpanData spanData = TestUtils.makeBasicSpan();

    when(readableSpan.getSpanContext()).thenReturn(NOT_SAMPLED_SPAN_CONTEXT);
    when(readableSpan.toSpanData()).thenReturn(spanData);

    spanProcessor.onEnd(readableSpan);
    verify(spanExporter).export(Collections.singletonList(spanData));
  }
}
