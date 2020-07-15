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

import io.opentelemetry.common.Labels;
import io.opentelemetry.internal.StringUtils;
import io.opentelemetry.internal.Utils;
import io.opentelemetry.metrics.Instrument;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.common.InstrumentValueType;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.List;
import java.util.Objects;

abstract class AbstractInstrument implements Instrument {

  private final InstrumentDescriptor descriptor;
  private final MeterProviderSharedState meterProviderSharedState;
  private final MeterSharedState meterSharedState;
  private final ActiveBatcher activeBatcher;

  // All arguments cannot be null because they are checked in the abstract builder classes.
  AbstractInstrument(
      InstrumentDescriptor descriptor,
      MeterProviderSharedState meterProviderSharedState,
      MeterSharedState meterSharedState,
      ActiveBatcher activeBatcher) {
    this.descriptor = descriptor;
    this.meterProviderSharedState = meterProviderSharedState;
    this.meterSharedState = meterSharedState;
    this.activeBatcher = activeBatcher;
  }

  final InstrumentDescriptor getDescriptor() {
    return descriptor;
  }

  final MeterProviderSharedState getMeterProviderSharedState() {
    return meterProviderSharedState;
  }

  final MeterSharedState getMeterSharedState() {
    return meterSharedState;
  }

  final ActiveBatcher getActiveBatcher() {
    return activeBatcher;
  }

  /**
   * Collects records from all the entries (labelSet, Bound) that changed since the last {@link
   * AbstractInstrument#collectAll()} call.
   */
  abstract List<MetricData> collectAll();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AbstractInstrument)) {
      return false;
    }

    AbstractInstrument that = (AbstractInstrument) o;

    return descriptor.equals(that.descriptor);
  }

  @Override
  public int hashCode() {
    return descriptor.hashCode();
  }

  abstract static class Builder<B extends AbstractInstrument.Builder<?>>
      implements Instrument.Builder {
    /* VisibleForTesting */ static final int NAME_MAX_LENGTH = 255;
    /* VisibleForTesting */ static final String ERROR_MESSAGE_INVALID_NAME =
        "Name should be a ASCII string with a length no greater than "
            + NAME_MAX_LENGTH
            + " characters.";

    private final String name;
    private final MeterProviderSharedState meterProviderSharedState;
    private final MeterSharedState meterSharedState;
    private final MeterSdk meterSdk;
    private String description = "";
    private String unit = "1";
    private Labels constantLabels = Labels.empty();

    Builder(
        String name,
        MeterProviderSharedState meterProviderSharedState,
        MeterSharedState meterSharedState,
        MeterSdk meterSdk) {
      this.meterSdk = meterSdk;
      Objects.requireNonNull(name, "name");
      Utils.checkArgument(
          StringUtils.isValidMetricName(name) && name.length() <= NAME_MAX_LENGTH,
          ERROR_MESSAGE_INVALID_NAME);
      this.name = name;
      this.meterProviderSharedState = meterProviderSharedState;
      this.meterSharedState = meterSharedState;
    }

    @Override
    public final B setDescription(String description) {
      this.description = Objects.requireNonNull(description, "description");
      return getThis();
    }

    @Override
    public final B setUnit(String unit) {
      this.unit = Objects.requireNonNull(unit, "unit");
      return getThis();
    }

    @Override
    public final B setConstantLabels(Labels constantLabels) {
      this.constantLabels = constantLabels;
      return getThis();
    }

    final MeterProviderSharedState getMeterProviderSharedState() {
      return meterProviderSharedState;
    }

    final MeterSharedState getMeterSharedState() {
      return meterSharedState;
    }

    final InstrumentDescriptor getInstrumentDescriptor(
        InstrumentType type, InstrumentValueType valueType) {
      return InstrumentDescriptor.create(name, description, unit, constantLabels, type, valueType);
    }

    abstract B getThis();

    final <I extends AbstractInstrument> I register(I instrument) {
      return getMeterSharedState().getInstrumentRegistry().register(instrument);
    }

    protected Batcher getBatcher(InstrumentDescriptor descriptor) {
      return meterSdk.createBatcher(descriptor, meterProviderSharedState, meterSharedState);
    }
  }
}
