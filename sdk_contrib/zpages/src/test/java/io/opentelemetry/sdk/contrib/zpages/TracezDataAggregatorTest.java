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

package io.opentelemetry.sdk.contrib.zpages;

import static com.google.common.truth.Truth.assertThat;

import io.opentelemetry.sdk.contrib.zpages.TracezDataAggregator.LatencyBoundaries;
import io.opentelemetry.sdk.internal.TestClock;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link TracezDataAggregator}. */
@RunWith(JUnit4.class)
public final class TracezDataAggregatorTest {
  private final TestClock testClock = TestClock.create();
  private final TracerSdkProvider tracerSdkProvider =
      TracerSdkProvider.builder().setClock(testClock).build();
  private final Tracer tracer = tracerSdkProvider.get("TracezDataAggregatorTest");
  private final TracezSpanProcessor spanProcessor = TracezSpanProcessor.newBuilder().build();
  private final TracezDataAggregator dataAggregator = new TracezDataAggregator(spanProcessor);
  private static final String SPAN_NAME_ONE = "one";
  private static final String SPAN_NAME_TWO = "two";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    tracerSdkProvider.addSpanProcessor(spanProcessor);
  }

  @Test
  public void getSpanNames_noSpans() {
    /* getSpanNames should return a an empty set initially */
    Set<String> names = dataAggregator.getSpanNames();
    assertThat(names.size()).isEqualTo(0);
  }

  @Test
  public void getSpanNames_twoSpanNames() {
    /* getSpanNames should return a set with 2 span names */
    Span span1 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span2 = tracer.spanBuilder(SPAN_NAME_TWO).startSpan();
    Span span3 = tracer.spanBuilder(SPAN_NAME_TWO).startSpan();
    Set<String> names = dataAggregator.getSpanNames();
    assertThat(names.size()).isEqualTo(2);
    assertThat(names).contains(SPAN_NAME_ONE);
    assertThat(names).contains(SPAN_NAME_TWO);

    /* getSpanNames should still return a set with 2 span names */
    span1.end();
    span2.end();
    span3.end();
    names = dataAggregator.getSpanNames();
    assertThat(names.size()).isEqualTo(2);
    assertThat(names).contains(SPAN_NAME_ONE);
    assertThat(names).contains(SPAN_NAME_TWO);
  }

  @Test
  public void getRunningSpanCounts_noSpans() {
    /* getRunningSpanCounts should return a an empty map */
    Map<String, Integer> counts = dataAggregator.getRunningSpanCounts();
    assertThat(counts.size()).isEqualTo(0);
    assertThat(counts.get(SPAN_NAME_ONE)).isNull();
    assertThat(counts.get(SPAN_NAME_TWO)).isNull();
  }

  @Test
  public void getRunningSpanCounts_oneSpanName() {
    Span span1 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span2 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span3 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    /* getRunningSpanCounts should return a map with 1 span name */
    Map<String, Integer> counts = dataAggregator.getRunningSpanCounts();
    assertThat(counts.size()).isEqualTo(1);
    assertThat(counts.get(SPAN_NAME_ONE)).isEqualTo(3);
    span1.end();
    span2.end();
    span3.end();
    /* getRunningSpanCounts should return a map with no span names */
    counts = dataAggregator.getRunningSpanCounts();
    assertThat(counts.size()).isEqualTo(0);
    assertThat(counts.get(SPAN_NAME_ONE)).isNull();
  }

  @Test
  public void getRunningSpanCounts_twoSpanNames() {
    Span span1 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span2 = tracer.spanBuilder(SPAN_NAME_TWO).startSpan();
    /* getRunningSpanCounts should return a map with 2 different span names */
    Map<String, Integer> counts = dataAggregator.getRunningSpanCounts();
    assertThat(counts.size()).isEqualTo(2);
    assertThat(counts.get(SPAN_NAME_ONE)).isEqualTo(1);
    assertThat(counts.get(SPAN_NAME_TWO)).isEqualTo(1);

    span1.end();
    /* getRunningSpanCounts should return a map with 1 unique span name */
    counts = dataAggregator.getRunningSpanCounts();
    assertThat(counts.size()).isEqualTo(1);
    assertThat(counts.get(SPAN_NAME_ONE)).isNull();
    assertThat(counts.get(SPAN_NAME_TWO)).isEqualTo(1);

    span2.end();
    /* getRunningSpanCounts should return a map with no span names */
    counts = dataAggregator.getRunningSpanCounts();
    assertThat(counts.size()).isEqualTo(0);
    assertThat(counts.get(SPAN_NAME_ONE)).isNull();
    assertThat(counts.get(SPAN_NAME_TWO)).isNull();
  }

  @Test
  public void getRunningSpans_noSpans() {
    /* getRunningSpans should return an empty List */
    assertThat(dataAggregator.getRunningSpans(SPAN_NAME_ONE).size()).isEqualTo(0);
    assertThat(dataAggregator.getRunningSpans(SPAN_NAME_TWO).size()).isEqualTo(0);
  }

  @Test
  public void getRunningSpans_oneSpanName() {
    Span span1 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span2 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span3 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    /* getRunningSpans should return a List with all 3 spans */
    List<SpanData> spans = dataAggregator.getRunningSpans(SPAN_NAME_ONE);
    assertThat(spans.size()).isEqualTo(3);
    assertThat(spans).contains(((ReadableSpan) span1).toSpanData());
    assertThat(spans).contains(((ReadableSpan) span2).toSpanData());
    assertThat(spans).contains(((ReadableSpan) span3).toSpanData());
    span1.end();
    span2.end();
    span3.end();
    /* getRunningSpans should return an empty List */
    assertThat(dataAggregator.getRunningSpans(SPAN_NAME_ONE).size()).isEqualTo(0);
  }

  @Test
  public void getRunningSpans_twoSpanNames() {
    Span span1 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span2 = tracer.spanBuilder(SPAN_NAME_TWO).startSpan();
    /* getRunningSpans should return a List with only the corresponding span */
    assertThat(dataAggregator.getRunningSpans(SPAN_NAME_ONE))
        .containsExactly(((ReadableSpan) span1).toSpanData());
    assertThat(dataAggregator.getRunningSpans(SPAN_NAME_TWO))
        .containsExactly(((ReadableSpan) span2).toSpanData());
    span1.end();
    span2.end();
    /* getRunningSpans should return an empty List for each span name */
    assertThat(dataAggregator.getRunningSpans(SPAN_NAME_ONE).size()).isEqualTo(0);
    assertThat(dataAggregator.getRunningSpans(SPAN_NAME_TWO).size()).isEqualTo(0);
  }

  @Test
  public void getSpanLatencyCounts_noSpans() {
    /* getSpanLatencyCounts should return a an empty map */
    Map<String, Integer> counts = dataAggregator.getSpanLatencyCounts(0, Long.MAX_VALUE);
    assertThat(counts.size()).isEqualTo(0);
    assertThat(counts.get(SPAN_NAME_ONE)).isNull();
    assertThat(counts.get(SPAN_NAME_TWO)).isNull();
  }

  @Test
  public void getSpanLatencyCounts_oneSpanPerLatencyBucket() {
    for (LatencyBoundaries bucket : LatencyBoundaries.values()) {
      Span span = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
      testClock.advanceNanos(bucket.getLatencyLowerBound());
      span.end();
    }
    /* getSpanLatencyCounts should return 1 span per latency bucket */
    Map<String, Map<LatencyBoundaries, Integer>> allCounts = dataAggregator.getSpanLatencyCounts();
    for (LatencyBoundaries bucket : LatencyBoundaries.values()) {
      Map<String, Integer> counts =
          dataAggregator.getSpanLatencyCounts(
              bucket.getLatencyLowerBound(), bucket.getLatencyUpperBound());
      assertThat(counts.size()).isEqualTo(1);
      assertThat(counts.get(SPAN_NAME_ONE)).isEqualTo(1);
      for (Map.Entry<String, Integer> countsEntry : counts.entrySet()) {
        assertThat(countsEntry.getValue())
            .isEqualTo(allCounts.get(countsEntry.getKey()).get(bucket));
      }
    }
  }

  @Test
  public void getSpanLatencyCounts_upperBoundEdgeCase() {
    Span span = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    testClock.advanceNanos(1000);
    span.end();
    /* getSpanLatencyCounts(0, 1000) should not return the span */
    Map<String, Integer> counts = dataAggregator.getSpanLatencyCounts(0, 1000);
    assertThat(counts.size()).isEqualTo(0);
    assertThat(counts.get(SPAN_NAME_ONE)).isNull();
    /* getSpanLatencyCounts(1000, Long.MAX_VALUE) should return the span */
    counts = dataAggregator.getSpanLatencyCounts(1000, Long.MAX_VALUE);
    assertThat(counts.size()).isEqualTo(1);
    assertThat(counts.get(SPAN_NAME_ONE)).isEqualTo(1);
  }

  @Test
  public void getOkSpans_noSpans() {
    /* getOkSpans should return an empty List */
    assertThat(dataAggregator.getOkSpans(SPAN_NAME_ONE, 0, Long.MAX_VALUE).size()).isEqualTo(0);
    assertThat(dataAggregator.getOkSpans(SPAN_NAME_TWO, 0, Long.MAX_VALUE).size()).isEqualTo(0);
  }

  @Test
  public void getOkSpans_oneSpanNameWithDifferentLatencies() {
    Span span1 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span2 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    /* getOkSpans should return an empty List */
    assertThat(dataAggregator.getOkSpans(SPAN_NAME_ONE, 0, Long.MAX_VALUE).size()).isEqualTo(0);
    span1.end();
    testClock.advanceNanos(1000);
    span2.end();
    /* getOkSpans should return a List with both spans */
    List<SpanData> spans = dataAggregator.getOkSpans(SPAN_NAME_ONE, 0, Long.MAX_VALUE);
    assertThat(spans.size()).isEqualTo(2);
    assertThat(spans).contains(((ReadableSpan) span1).toSpanData());
    assertThat(spans).contains(((ReadableSpan) span2).toSpanData());
    /* getOkSpans should return a List with only the first span */
    spans = dataAggregator.getOkSpans(SPAN_NAME_ONE, 0, 1000);
    assertThat(spans.size()).isEqualTo(1);
    assertThat(spans).contains(((ReadableSpan) span1).toSpanData());
    /* getOkSpans should return a List with only the second span */
    spans = dataAggregator.getOkSpans(SPAN_NAME_ONE, 1000, Long.MAX_VALUE);
    assertThat(spans.size()).isEqualTo(1);
    assertThat(spans).contains(((ReadableSpan) span2).toSpanData());
  }

  @Test
  public void getOkSpans_twoSpanNames() {
    Span span1 = tracer.spanBuilder(SPAN_NAME_ONE).startSpan();
    Span span2 = tracer.spanBuilder(SPAN_NAME_TWO).startSpan();
    /* getOkSpans should return an empty List for each span name */
    assertThat(dataAggregator.getOkSpans(SPAN_NAME_ONE, 0, Long.MAX_VALUE).size()).isEqualTo(0);
    assertThat(dataAggregator.getOkSpans(SPAN_NAME_TWO, 0, Long.MAX_VALUE).size()).isEqualTo(0);
    span1.end();
    span2.end();
    /* getOkSpans should return a List with only the corresponding span */
    assertThat(dataAggregator.getOkSpans(SPAN_NAME_ONE, 0, Long.MAX_VALUE))
        .containsExactly(((ReadableSpan) span1).toSpanData());
    assertThat(dataAggregator.getOkSpans(SPAN_NAME_TWO, 0, Long.MAX_VALUE))
        .containsExactly(((ReadableSpan) span2).toSpanData());
  }
}