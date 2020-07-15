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

import io.opentelemetry.metrics.MeterProvider;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.ComponentRegistry;
import io.opentelemetry.sdk.internal.MillisClock;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.resources.EnvVarResource;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * {@code Meter} provider implementation for {@link MeterProvider}.
 *
 * <p>This class is not intended to be used in application code and it is used only by {@link
 * io.opentelemetry.OpenTelemetry}.
 */
public final class MeterSdkProvider implements MeterProvider {

  private final MeterSdkComponentRegistry registry;
  private final MetricProducer metricProducer;

  private MeterSdkProvider(Clock clock, Resource resource) {
    this.registry =
        new MeterSdkComponentRegistry(
            MeterProviderSharedState.create(clock, resource), new ViewRegistry());
    this.metricProducer = new MetricProducerSdk(this.registry);
  }

  @Override
  public MeterSdk get(String instrumentationName) {
    return registry.get(instrumentationName);
  }

  @Override
  public MeterSdk get(String instrumentationName, String instrumentationVersion) {
    return registry.get(instrumentationName, instrumentationVersion);
  }

  /**
   * Returns the {@link MetricProducer} that can be used to retrieve metrics from this {@code
   * MeterSdkProvider}.
   *
   * @return the {@link MetricProducer} that can be used to retrieve metrics from this {@code
   *     MeterSdkProvider}.
   */
  public MetricProducer getMetricProducer() {
    return metricProducer;
  }

  /**
   * Returns a new {@link Builder} for {@link MeterSdkProvider}.
   *
   * @return a new {@link Builder} for {@link MeterSdkProvider}.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder class for the {@link MeterSdkProvider}. Has fully functional default implementations of
   * all three required interfaces.
   *
   * @since 0.4.0
   */
  public static final class Builder {

    private Clock clock = MillisClock.getInstance();
    private Resource resource = Resource.getTelemetrySdk().merge(EnvVarResource.getResource());

    private Builder() {}

    /**
     * Assign a {@link Clock}.
     *
     * @param clock The clock to use for all temporal needs.
     * @return this
     */
    public Builder setClock(@Nonnull Clock clock) {
      Objects.requireNonNull(clock, "clock");
      this.clock = clock;
      return this;
    }

    /**
     * Assign a {@link Resource} to be attached to all Spans created by Tracers.
     *
     * @param resource A Resource implementation.
     * @return this
     */
    public Builder setResource(@Nonnull Resource resource) {
      Objects.requireNonNull(resource, "resource");
      this.resource = resource;
      return this;
    }

    /**
     * Create a new TracerSdkFactory instance.
     *
     * @return An initialized TracerSdkFactory.
     */
    public MeterSdkProvider build() {
      return new MeterSdkProvider(clock, resource);
    }
  }

  private static final class MeterSdkComponentRegistry extends ComponentRegistry<MeterSdk> {
    private final MeterProviderSharedState meterProviderSharedState;
    private final ViewRegistry viewRegistry;

    private MeterSdkComponentRegistry(
        MeterProviderSharedState meterProviderSharedState, ViewRegistry viewRegistry) {
      this.meterProviderSharedState = meterProviderSharedState;
      this.viewRegistry = viewRegistry;
    }

    @Override
    public MeterSdk newComponent(InstrumentationLibraryInfo instrumentationLibraryInfo) {
      return new MeterSdk(meterProviderSharedState, instrumentationLibraryInfo, viewRegistry);
    }
  }

  private static final class MetricProducerSdk implements MetricProducer {
    private final MeterSdkComponentRegistry registry;

    private MetricProducerSdk(MeterSdkComponentRegistry registry) {
      this.registry = registry;
    }

    @Override
    public Collection<MetricData> collectAllMetrics() {
      Collection<MeterSdk> meters = registry.getComponents();
      List<MetricData> result = new ArrayList<>(meters.size());
      for (MeterSdk meter : meters) {
        result.addAll(meter.collectAll());
      }
      return Collections.unmodifiableCollection(result);
    }
  }
}
