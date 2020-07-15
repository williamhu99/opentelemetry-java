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

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.metrics.Meter;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** {@link MeterSdk} is SDK implementation of {@link Meter}. */
final class MeterSdk implements Meter {
  private final MeterProviderSharedState meterProviderSharedState;
  private final MeterSharedState meterSharedState;
  private final ViewRegistry viewRegistry;

  MeterSdk(
      MeterProviderSharedState meterProviderSharedState,
      InstrumentationLibraryInfo instrumentationLibraryInfo,
      ViewRegistry viewRegistry) {
    this.meterProviderSharedState = meterProviderSharedState;
    this.meterSharedState = MeterSharedState.create(instrumentationLibraryInfo);
    this.viewRegistry = viewRegistry;
  }

  InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
    return meterSharedState.getInstrumentationLibraryInfo();
  }

  @Override
  public DoubleCounterSdk.Builder doubleCounterBuilder(String name) {
    return new DoubleCounterSdk.Builder(name, meterProviderSharedState, meterSharedState, this);
  }

  @Override
  public LongCounterSdk.Builder longCounterBuilder(String name) {
    return new LongCounterSdk.Builder(name, meterProviderSharedState, meterSharedState, this);
  }

  @Override
  public DoubleUpDownCounterSdk.Builder doubleUpDownCounterBuilder(String name) {
    return new DoubleUpDownCounterSdk.Builder(
        name, meterProviderSharedState, meterSharedState, this);
  }

  @Override
  public LongUpDownCounterSdk.Builder longUpDownCounterBuilder(String name) {
    return new LongUpDownCounterSdk.Builder(name, meterProviderSharedState, meterSharedState, this);
  }

  @Override
  public DoubleValueRecorderSdk.Builder doubleValueRecorderBuilder(String name) {
    return new DoubleValueRecorderSdk.Builder(
        name, meterProviderSharedState, meterSharedState, this);
  }

  @Override
  public LongValueRecorderSdk.Builder longValueRecorderBuilder(String name) {
    return new LongValueRecorderSdk.Builder(name, meterProviderSharedState, meterSharedState, this);
  }

  @Override
  public DoubleSumObserverSdk.Builder doubleSumObserverBuilder(String name) {
    return new DoubleSumObserverSdk.Builder(name, meterProviderSharedState, meterSharedState, this);
  }

  @Override
  public LongSumObserverSdk.Builder longSumObserverBuilder(String name) {
    return new LongSumObserverSdk.Builder(name, meterProviderSharedState, meterSharedState, this);
  }

  @Override
  public DoubleUpDownSumObserverSdk.Builder doubleUpDownSumObserverBuilder(String name) {
    return new DoubleUpDownSumObserverSdk.Builder(
        name, meterProviderSharedState, meterSharedState, this);
  }

  @Override
  public LongUpDownSumObserverSdk.Builder longUpDownSumObserverBuilder(String name) {
    return new LongUpDownSumObserverSdk.Builder(
        name, meterProviderSharedState, meterSharedState, this);
  }

  @Override
  public DoubleValueObserverSdk.Builder doubleValueObserverBuilder(String name) {
    return new DoubleValueObserverSdk.Builder(
        name, meterProviderSharedState, meterSharedState, this);
  }

  @Override
  public LongValueObserverSdk.Builder longValueObserverBuilder(String name) {
    return new LongValueObserverSdk.Builder(name, meterProviderSharedState, meterSharedState, this);
  }

  @Override
  public BatchRecorderSdk newBatchRecorder(String... keyValuePairs) {
    return new BatchRecorderSdk(keyValuePairs);
  }

  /**
   * Collects all the metric recordings that changed since the last {@link MeterSdk#collectAll()}
   * call.
   */
  Collection<MetricData> collectAll() {
    InstrumentRegistry instrumentRegistry = meterSharedState.getInstrumentRegistry();
    Collection<AbstractInstrument> instruments = instrumentRegistry.getInstruments();
    List<MetricData> result = new ArrayList<>(instruments.size());
    for (AbstractInstrument instrument : instruments) {
      result.addAll(instrument.collectAll());
    }
    return result;
  }

  /** Creates a {@link Batcher}, by using the {@link ViewRegistry} to do the actual work. */
  Batcher createBatcher(
      InstrumentDescriptor descriptor,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState) {

    return viewRegistry.createBatcher(meterProviderSharedState, meterSharedState, descriptor);
  }
}
