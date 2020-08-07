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

package io.opentelemetry.metrics;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BatchRecorderTest {
  private static final Meter meter = DefaultMeter.getInstance();

  @Test
  void testNewBatchRecorder_badLabelSet() {
    assertThrows(IllegalArgumentException.class, () -> meter.newBatchRecorder("key"), "key/value");
  }

  @Test
  void preventNull_MeasureLong() {
    assertThrows(
        NullPointerException.class,
        () -> meter.newBatchRecorder().put((LongValueRecorder) null, 5L).record(),
        "valueRecorder");
  }

  @Test
  void preventNull_MeasureDouble() {
    assertThrows(
        NullPointerException.class,
        () -> meter.newBatchRecorder().put((DoubleValueRecorder) null, 5L).record(),
        "valueRecorder");
  }

  @Test
  void preventNull_LongCounter() {
    assertThrows(
        NullPointerException.class,
        () -> meter.newBatchRecorder().put((LongCounter) null, 5L).record(),
        "counter");
  }

  @Test
  void preventNull_DoubleCounter() {
    assertThrows(
        NullPointerException.class,
        () -> meter.newBatchRecorder().put((DoubleCounter) null, 5L).record(),
        "counter");
  }

  @Test
  void preventNull_LongUpDownCounter() {
    assertThrows(
        NullPointerException.class,
        () -> meter.newBatchRecorder().put((LongUpDownCounter) null, 5L).record(),
        "upDownCounter");
  }

  @Test
  void preventNull_DoubleUpDownCounter() {
    assertThrows(
        NullPointerException.class,
        () -> meter.newBatchRecorder().put((DoubleUpDownCounter) null, 5L).record(),
        "upDownCounter");
  }

  @Test
  void doesNotThrow() {
    BatchRecorder batchRecorder = meter.newBatchRecorder();
    batchRecorder.put(meter.longValueRecorderBuilder("longValueRecorder").build(), 44L);
    batchRecorder.put(meter.longValueRecorderBuilder("negativeLongValueRecorder").build(), -44L);
    batchRecorder.put(meter.doubleValueRecorderBuilder("doubleValueRecorder").build(), 77.556d);
    batchRecorder.put(
        meter.doubleValueRecorderBuilder("negativeDoubleValueRecorder").build(), -77.556d);
    batchRecorder.put(meter.longCounterBuilder("longCounter").build(), 44L);
    batchRecorder.put(meter.doubleCounterBuilder("doubleCounter").build(), 77.556d);
    batchRecorder.put(meter.longUpDownCounterBuilder("longUpDownCounter").build(), -44L);
    batchRecorder.put(meter.doubleUpDownCounterBuilder("doubleUpDownCounter").build(), -77.556d);
    batchRecorder.record();
  }

  @Test
  void negativeValue_DoubleCounter() {
    BatchRecorder batchRecorder = meter.newBatchRecorder();
    assertThrows(
        IllegalArgumentException.class,
        () -> batchRecorder.put(meter.doubleCounterBuilder("doubleCounter").build(), -77.556d),
        "Counters can only increase");
  }

  @Test
  void negativeValue_LongCounter() {
    BatchRecorder batchRecorder = meter.newBatchRecorder();
    assertThrows(
        IllegalArgumentException.class,
        () -> batchRecorder.put(meter.longCounterBuilder("longCounter").build(), -44L),
        "Counters can only increase");
  }
}
