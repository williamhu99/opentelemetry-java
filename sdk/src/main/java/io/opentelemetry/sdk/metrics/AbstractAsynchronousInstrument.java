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

import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.AsynchronousInstrument;
import io.opentelemetry.sdk.metrics.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

abstract class AbstractAsynchronousInstrument<T extends AsynchronousInstrument.Result>
    extends AbstractInstrument implements AsynchronousInstrument<T> {
  @Nullable private volatile Callback<T> metricUpdater = null;
  private final ReentrantLock collectLock = new ReentrantLock();

  AbstractAsynchronousInstrument(
      InstrumentDescriptor descriptor,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      ActiveBatcher activeBatcher) {
    super(descriptor, meterProviderSharedState, meterSharedState, activeBatcher);
  }

  @Override
  List<MetricData> collectAll() {
    Callback<T> currentMetricUpdater = metricUpdater;
    if (currentMetricUpdater == null) {
      return Collections.emptyList();
    }
    collectLock.lock();
    try {
      final ActiveBatcher activeBatcher = getActiveBatcher();
      currentMetricUpdater.update(newResult(activeBatcher));
      return activeBatcher.completeCollectionCycle();
    } finally {
      collectLock.unlock();
    }
  }

  @Override
  public void setCallback(Callback<T> callback) {
    this.metricUpdater = Objects.requireNonNull(callback, "callback");
  }

  abstract T newResult(ActiveBatcher activeBatcher);

  abstract static class Builder<B extends AbstractInstrument.Builder<?>>
      extends AbstractInstrument.Builder<B> {
    Builder(
        String name,
        MeterProviderSharedState meterProviderSharedState,
        MeterSharedState meterSharedState,
        MeterSdk meterSdk) {
      super(name, meterProviderSharedState, meterSharedState, meterSdk);
    }
  }

  static class AbstractLongAsynchronousInstrument
      extends AbstractAsynchronousInstrument<LongResult> {
    AbstractLongAsynchronousInstrument(
        InstrumentDescriptor descriptor,
        MeterProviderSharedState meterProviderSharedState,
        MeterSharedState meterSharedState,
        ActiveBatcher activeBatcher) {
      super(descriptor, meterProviderSharedState, meterSharedState, activeBatcher);
    }

    @Override
    LongResultSdk newResult(ActiveBatcher activeBatcher) {
      return new LongResultSdk(activeBatcher);
    }

    private static final class LongResultSdk implements LongResult {

      private final ActiveBatcher activeBatcher;

      private LongResultSdk(ActiveBatcher activeBatcher) {
        this.activeBatcher = activeBatcher;
      }

      @Override
      public void observe(long sum, Labels labels) {
        Aggregator aggregator = activeBatcher.getAggregator();
        aggregator.recordLong(sum);
        activeBatcher.batch(labels, aggregator, /* mappedAggregator= */ false);
      }
    }
  }

  static class AbstractDoubleAsynchronousInstrument
      extends AbstractAsynchronousInstrument<DoubleResult> {
    AbstractDoubleAsynchronousInstrument(
        InstrumentDescriptor descriptor,
        MeterProviderSharedState meterProviderSharedState,
        MeterSharedState meterSharedState,
        ActiveBatcher activeBatcher) {
      super(descriptor, meterProviderSharedState, meterSharedState, activeBatcher);
    }

    @Override
    DoubleResultSdk newResult(ActiveBatcher activeBatcher) {
      return new DoubleResultSdk(activeBatcher);
    }

    private static final class DoubleResultSdk implements DoubleResult {

      private final ActiveBatcher activeBatcher;

      private DoubleResultSdk(ActiveBatcher activeBatcher) {
        this.activeBatcher = activeBatcher;
      }

      @Override
      public void observe(double sum, Labels labels) {
        Aggregator aggregator = activeBatcher.getAggregator();
        aggregator.recordDouble(sum);
        activeBatcher.batch(labels, aggregator, /* mappedAggregator= */ false);
      }
    }
  }
}
