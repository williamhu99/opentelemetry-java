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

package io.opentelemetry.sdk.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.DoubleValueRecorder;
import io.opentelemetry.metrics.DoubleValueRecorder.BoundDoubleValueRecorder;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.TestClock;
import io.opentelemetry.sdk.metrics.StressTestRunner.OperationUpdater;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricData.Descriptor;
import io.opentelemetry.sdk.metrics.data.MetricData.SummaryPoint;
import io.opentelemetry.sdk.metrics.data.MetricData.ValueAtPercentile;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DoubleValueRecorderSdk}. */
class DoubleValueRecorderSdkTest {
  private static final long SECOND_NANOS = 1_000_000_000;
  private static final Resource RESOURCE =
      Resource.create(
          Attributes.of("resource_key", AttributeValue.stringAttributeValue("resource_value")));
  private static final InstrumentationLibraryInfo INSTRUMENTATION_LIBRARY_INFO =
      InstrumentationLibraryInfo.create(
          "io.opentelemetry.sdk.metrics.DoubleValueRecorderSdkTest", null);
  private final TestClock testClock = TestClock.create();
  private final MeterProviderSharedState meterProviderSharedState =
      MeterProviderSharedState.create(testClock, RESOURCE);
  private final MeterSdk testSdk =
      new MeterSdk(meterProviderSharedState, INSTRUMENTATION_LIBRARY_INFO, new ViewRegistry());

  @Test
  void record_PreventNullLabels() {
    assertThrows(
        NullPointerException.class,
        () -> testSdk.doubleValueRecorderBuilder("testRecorder").build().record(1.0, null),
        "labels");
  }

  @Test
  void bound_PreventNullLabels() {
    assertThrows(
        NullPointerException.class,
        () -> testSdk.doubleValueRecorderBuilder("testRecorder").build().bind(null),
        "labels");
  }

  @Test
  void collectMetrics_NoRecords() {
    DoubleValueRecorderSdk doubleMeasure =
        testSdk
            .doubleValueRecorderBuilder("testRecorder")
            .setConstantLabels(Labels.of("sk1", "sv1"))
            .setDescription("My very own measure")
            .setUnit("ms")
            .build();
    assertThat(doubleMeasure).isInstanceOf(DoubleValueRecorderSdk.class);
    List<MetricData> metricDataList = doubleMeasure.collectAll();
    assertThat(metricDataList)
        .containsExactly(
            MetricData.create(
                Descriptor.create(
                    "testRecorder",
                    "My very own measure",
                    "ms",
                    Descriptor.Type.SUMMARY,
                    Labels.of("sk1", "sv1")),
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                Collections.emptyList()));
  }

  @Test
  void collectMetrics_EmptyCollectionCycle() {
    DoubleValueRecorderSdk doubleMeasure =
        testSdk
            .doubleValueRecorderBuilder("testRecorder")
            .setConstantLabels(Labels.of("sk1", "sv1"))
            .setDescription("My very own measure")
            .setUnit("ms")
            .build();
    doubleMeasure.bind(Labels.of("key", "value"));
    testClock.advanceNanos(SECOND_NANOS);

    List<MetricData> metricDataList = doubleMeasure.collectAll();
    assertThat(metricDataList)
        .containsExactly(
            MetricData.create(
                Descriptor.create(
                    "testRecorder",
                    "My very own measure",
                    "ms",
                    Descriptor.Type.SUMMARY,
                    Labels.of("sk1", "sv1")),
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                Collections.emptyList()));
  }

  @Test
  void collectMetrics_WithOneRecord() {
    DoubleValueRecorderSdk doubleMeasure =
        testSdk.doubleValueRecorderBuilder("testRecorder").build();
    testClock.advanceNanos(SECOND_NANOS);
    doubleMeasure.record(12.1d, Labels.empty());
    List<MetricData> metricDataList = doubleMeasure.collectAll();
    assertThat(metricDataList)
        .containsExactly(
            MetricData.create(
                Descriptor.create("testRecorder", "", "1", Descriptor.Type.SUMMARY, Labels.empty()),
                RESOURCE,
                INSTRUMENTATION_LIBRARY_INFO,
                Collections.singletonList(
                    SummaryPoint.create(
                        testClock.now() - SECOND_NANOS,
                        testClock.now(),
                        Labels.empty(),
                        1,
                        12.1d,
                        valueAtPercentiles(12.1d, 12.1d)))));
  }

  @Test
  void collectMetrics_WithMultipleCollects() {
    long startTime = testClock.now();
    DoubleValueRecorderSdk doubleMeasure =
        testSdk.doubleValueRecorderBuilder("testRecorder").build();
    BoundDoubleValueRecorder boundMeasure = doubleMeasure.bind(Labels.of("K", "V"));
    try {
      // Do some records using bounds and direct calls and bindings.
      doubleMeasure.record(12.1d, Labels.empty());
      boundMeasure.record(123.3d);
      doubleMeasure.record(-13.1d, Labels.empty());
      // Advancing time here should not matter.
      testClock.advanceNanos(SECOND_NANOS);
      boundMeasure.record(321.5d);
      doubleMeasure.record(-121.5d, Labels.of("K", "V"));

      long firstCollect = testClock.now();
      List<MetricData> metricDataList = doubleMeasure.collectAll();
      assertThat(metricDataList).hasSize(1);
      MetricData metricData = metricDataList.get(0);
      assertThat(metricData.getPoints())
          .containsExactlyInAnyOrder(
              SummaryPoint.create(
                  startTime,
                  firstCollect,
                  Labels.empty(),
                  2,
                  -1.0d,
                  valueAtPercentiles(-13.1d, 12.1d)),
              SummaryPoint.create(
                  startTime,
                  firstCollect,
                  Labels.of("K", "V"),
                  3,
                  323.3d,
                  valueAtPercentiles(-121.5d, 321.5d)));

      // Repeat to prove we don't keep previous values.
      testClock.advanceNanos(SECOND_NANOS);
      boundMeasure.record(222d);
      doubleMeasure.record(17d, Labels.empty());

      long secondCollect = testClock.now();
      metricDataList = doubleMeasure.collectAll();
      assertThat(metricDataList).hasSize(1);
      metricData = metricDataList.get(0);
      assertThat(metricData.getPoints())
          .containsExactlyInAnyOrder(
              SummaryPoint.create(
                  startTime + SECOND_NANOS,
                  secondCollect,
                  Labels.empty(),
                  1,
                  17.0d,
                  valueAtPercentiles(17d, 17d)),
              SummaryPoint.create(
                  startTime + SECOND_NANOS,
                  secondCollect,
                  Labels.of("K", "V"),
                  1,
                  222.0d,
                  valueAtPercentiles(222.0, 222.0d)));
    } finally {
      boundMeasure.unbind();
    }
  }

  @Test
  void sameBound_ForSameLabelSet() {
    DoubleValueRecorderSdk doubleMeasure =
        testSdk.doubleValueRecorderBuilder("testRecorder").build();
    BoundDoubleValueRecorder boundMeasure = doubleMeasure.bind(Labels.of("K", "V"));
    BoundDoubleValueRecorder duplicateBoundMeasure = doubleMeasure.bind(Labels.of("K", "V"));
    try {
      assertThat(duplicateBoundMeasure).isEqualTo(boundMeasure);
    } finally {
      boundMeasure.unbind();
      duplicateBoundMeasure.unbind();
    }
  }

  @Test
  void sameBound_ForSameLabelSet_InDifferentCollectionCycles() {
    DoubleValueRecorderSdk doubleMeasure =
        testSdk.doubleValueRecorderBuilder("testRecorder").build();
    BoundDoubleValueRecorder boundMeasure = doubleMeasure.bind(Labels.of("K", "V"));
    try {
      doubleMeasure.collectAll();
      BoundDoubleValueRecorder duplicateBoundMeasure = doubleMeasure.bind(Labels.of("K", "V"));
      try {
        assertThat(duplicateBoundMeasure).isEqualTo(boundMeasure);
      } finally {
        duplicateBoundMeasure.unbind();
      }
    } finally {
      boundMeasure.unbind();
    }
  }

  @Test
  void stressTest() {
    final DoubleValueRecorderSdk doubleMeasure =
        testSdk.doubleValueRecorderBuilder("testRecorder").build();

    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setInstrument(doubleMeasure).setCollectionIntervalMs(100);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              1_000,
              2,
              new DoubleValueRecorderSdkTest.OperationUpdaterDirectCall(doubleMeasure, "K", "V")));
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              1_000, 2, new OperationUpdaterWithBinding(doubleMeasure.bind(Labels.of("K", "V")))));
    }

    stressTestBuilder.build().run();
    List<MetricData> metricDataList = doubleMeasure.collectAll();
    assertThat(metricDataList).hasSize(1);
    assertThat(metricDataList.get(0).getPoints())
        .containsExactly(
            SummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of("K", "V"),
                8_000,
                80_000,
                valueAtPercentiles(9.0, 11.0)));
  }

  @Test
  void stressTest_WithDifferentLabelSet() {
    final String[] keys = {"Key_1", "Key_2", "Key_3", "Key_4"};
    final String[] values = {"Value_1", "Value_2", "Value_3", "Value_4"};
    final DoubleValueRecorderSdk doubleMeasure =
        testSdk.doubleValueRecorderBuilder("testRecorder").build();

    StressTestRunner.Builder stressTestBuilder =
        StressTestRunner.builder().setInstrument(doubleMeasure).setCollectionIntervalMs(100);

    for (int i = 0; i < 4; i++) {
      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              2_000,
              1,
              new DoubleValueRecorderSdkTest.OperationUpdaterDirectCall(
                  doubleMeasure, keys[i], values[i])));

      stressTestBuilder.addOperation(
          StressTestRunner.Operation.create(
              2_000,
              1,
              new OperationUpdaterWithBinding(doubleMeasure.bind(Labels.of(keys[i], values[i])))));
    }

    stressTestBuilder.build().run();
    List<MetricData> metricDataList = doubleMeasure.collectAll();
    assertThat(metricDataList).hasSize(1);
    assertThat(metricDataList.get(0).getPoints())
        .containsExactly(
            SummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of(keys[0], values[0]),
                4_000,
                40_000d,
                valueAtPercentiles(9.0, 11.0)),
            SummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of(keys[1], values[1]),
                4_000,
                40_000d,
                valueAtPercentiles(9.0, 11.0)),
            SummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of(keys[2], values[2]),
                4_000,
                40_000d,
                valueAtPercentiles(9.0, 11.0)),
            SummaryPoint.create(
                testClock.now(),
                testClock.now(),
                Labels.of(keys[3], values[3]),
                4_000,
                40_000d,
                valueAtPercentiles(9.0, 11.0)));
  }

  private static class OperationUpdaterWithBinding extends OperationUpdater {
    private final BoundDoubleValueRecorder boundDoubleValueRecorder;

    private OperationUpdaterWithBinding(BoundDoubleValueRecorder boundDoubleValueRecorder) {
      this.boundDoubleValueRecorder = boundDoubleValueRecorder;
    }

    @Override
    void update() {
      boundDoubleValueRecorder.record(11.0);
    }

    @Override
    void cleanup() {
      boundDoubleValueRecorder.unbind();
    }
  }

  private static class OperationUpdaterDirectCall extends OperationUpdater {
    private final DoubleValueRecorder doubleValueRecorder;
    private final String key;
    private final String value;

    private OperationUpdaterDirectCall(
        DoubleValueRecorder doubleValueRecorder, String key, String value) {
      this.doubleValueRecorder = doubleValueRecorder;
      this.key = key;
      this.value = value;
    }

    @Override
    void update() {
      doubleValueRecorder.record(9.0, Labels.of(key, value));
    }

    @Override
    void cleanup() {}
  }

  private static List<ValueAtPercentile> valueAtPercentiles(double min, double max) {
    return Arrays.asList(ValueAtPercentile.create(0, min), ValueAtPercentile.create(100, max));
  }
}
