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

package io.opentelemetry.sdk.trace.config;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.opentelemetry.internal.Utils;
import io.opentelemetry.sdk.common.export.ConfigBuilder;
import io.opentelemetry.sdk.trace.Sampler;
import io.opentelemetry.sdk.trace.Samplers;
import io.opentelemetry.trace.Event;
import io.opentelemetry.trace.Link;
import io.opentelemetry.trace.Span;
import java.util.Map;
import java.util.Properties;
import javax.annotation.concurrent.Immutable;

/**
 * Class that holds global trace parameters.
 *
 * <p>Note: To update the TraceConfig associated with a {@link
 * io.opentelemetry.sdk.trace.TracerSdkProvider}, you should use the {@link #toBuilder()} method on
 * the TraceConfig currently assigned to the provider, make the changes desired to the {@link
 * Builder} instance, then use the {@link
 * io.opentelemetry.sdk.trace.TracerSdkProvider#updateActiveTraceConfig(TraceConfig)} with the
 * resulting TraceConfig instance.
 *
 * <p>Configuration options for {@link TraceConfig} can be read from system properties, environment
 * variables, or {@link java.util.Properties} objects.
 *
 * <p>For system properties and {@link java.util.Properties} objects, {@link TraceConfig} will look
 * for the following names:
 *
 * <ul>
 *   <li>{@code otel.config.sampler.probability}: to set the global default sampler which is used
 *       when constructing a new {@code Span}.
 *   <li>{@code otel.config.max.attrs}: to set the global default max number of attributes per
 *       {@link Span}.
 *   <li>{@code otel.config.max.events}: to set the global default max number of {@link Event}s per
 *       {@link Span}.
 *   <li>{@code otel.config.max.links}: to set the global default max number of {@link Link} entries
 *       per {@link Span}.
 *   <li>{@code otel.config.max.event.attrs}: to set the global default max number of attributes per
 *       {@link Event}.
 *   <li>{@code otel.config.max.link.attrs}: to set the global default max number of attributes per
 *       {@link Link}.
 *   <li>{@code otel.config.max.attr.length}: to set the global default max length of string
 *       attribute value in characters.
 * </ul>
 *
 * <p>For environment variables, {@link TraceConfig} will look for the following names:
 *
 * <ul>
 *   <li>{@code OTEL_CONFIG_SAMPLER_PROBABILITY}: to set the global default sampler which is used
 *       when constructing a new {@code Span}.
 *   <li>{@code OTEL_CONFIG_MAX_ATTRS}: to set the global default max number of attributes per
 *       {@link Span}.
 *   <li>{@code OTEL_CONFIG_MAX_EVENTS}: to set the global default max number of {@link Event}s per
 *       {@link Span}.
 *   <li>{@code OTEL_CONFIG_MAX_LINKS}: to set the global default max number of {@link Link} entries
 *       per {@link Span}.
 *   <li>{@code OTEL_CONFIG_MAX_EVENT_ATTRS}: to set the global default max number of attributes per
 *       {@link Event}.
 *   <li>{@code OTEL_CONFIG_MAX_LINK_ATTRS}: to set the global default max number of attributes per
 *       {@link Link}.
 *   <li>{@code OTEL_CONFIG_MAX_ATTR_LENGTH}: to set the global default max length of string
 *       attribute value in characters.
 * </ul>
 */
@AutoValue
@Immutable
public abstract class TraceConfig {
  // These values are the default values for all the global parameters.
  // TODO: decide which default sampler to use
  private static final Sampler DEFAULT_SAMPLER = Samplers.parentOrElse(Samplers.alwaysOn());
  private static final int DEFAULT_SPAN_MAX_NUM_ATTRIBUTES = 32;
  private static final int DEFAULT_SPAN_MAX_NUM_EVENTS = 128;
  private static final int DEFAULT_SPAN_MAX_NUM_LINKS = 32;
  private static final int DEFAULT_SPAN_MAX_NUM_ATTRIBUTES_PER_EVENT = 32;
  private static final int DEFAULT_SPAN_MAX_NUM_ATTRIBUTES_PER_LINK = 32;

  public static final int UNLIMITED_ATTRIBUTE_LENGTH = -1;
  private static final int DEFAULT_MAX_ATTRIBUTE_LENGTH = UNLIMITED_ATTRIBUTE_LENGTH;

  /**
   * Returns the default {@code TraceConfig}.
   *
   * @return the default {@code TraceConfig}.
   * @since 0.1.0
   */
  public static TraceConfig getDefault() {
    return DEFAULT;
  }

  private static final TraceConfig DEFAULT = TraceConfig.newBuilder().build();

  /**
   * Returns the global default {@code Sampler} which is used when constructing a new {@code Span}.
   *
   * @return the global default {@code Sampler}.
   */
  public abstract Sampler getSampler();

  /**
   * Returns the global default max number of attributes per {@link Span}.
   *
   * @return the global default max number of attributes per {@link Span}.
   */
  public abstract int getMaxNumberOfAttributes();

  /**
   * Returns the global default max number of {@link Event}s per {@link Span}.
   *
   * @return the global default max number of {@code Event}s per {@code Span}.
   */
  public abstract int getMaxNumberOfEvents();

  /**
   * Returns the global default max number of {@link Link} entries per {@link Span}.
   *
   * @return the global default max number of {@code Link} entries per {@code Span}.
   */
  public abstract int getMaxNumberOfLinks();

  /**
   * Returns the global default max number of attributes per {@link Event}.
   *
   * @return the global default max number of attributes per {@link Event}.
   */
  public abstract int getMaxNumberOfAttributesPerEvent();

  /**
   * Returns the global default max number of attributes per {@link Link}.
   *
   * @return the global default max number of attributes per {@link Link}.
   */
  public abstract int getMaxNumberOfAttributesPerLink();

  /**
   * Returns the global default max length of string attribute value in characters.
   *
   * @return the global default max length of string attribute value in characters.
   * @see #shouldTruncateStringAttributeValues()
   */
  public abstract int getMaxLengthOfAttributeValues();

  public boolean shouldTruncateStringAttributeValues() {
    return getMaxLengthOfAttributeValues() != UNLIMITED_ATTRIBUTE_LENGTH;
  }

  /**
   * Returns a new {@link Builder}.
   *
   * @return a new {@link Builder}.
   */
  private static Builder newBuilder() {
    return new AutoValue_TraceConfig.Builder()
        .setSampler(DEFAULT_SAMPLER)
        .setMaxNumberOfAttributes(DEFAULT_SPAN_MAX_NUM_ATTRIBUTES)
        .setMaxNumberOfEvents(DEFAULT_SPAN_MAX_NUM_EVENTS)
        .setMaxNumberOfLinks(DEFAULT_SPAN_MAX_NUM_LINKS)
        .setMaxNumberOfAttributesPerEvent(DEFAULT_SPAN_MAX_NUM_ATTRIBUTES_PER_EVENT)
        .setMaxNumberOfAttributesPerLink(DEFAULT_SPAN_MAX_NUM_ATTRIBUTES_PER_LINK)
        .setMaxLengthOfAttributeValues(DEFAULT_MAX_ATTRIBUTE_LENGTH);
  }

  /**
   * Returns a {@link Builder} initialized to the same property values as the current instance.
   *
   * @return a {@link Builder} initialized to the same property values as the current instance.
   */
  public abstract Builder toBuilder();

  /** Builder for {@link TraceConfig}. */
  @AutoValue.Builder
  public abstract static class Builder extends ConfigBuilder<Builder> {
    private static final String KEY_SAMPLER_PROBABILITY = "otel.config.sampler.probability";
    private static final String KEY_SPAN_MAX_NUM_ATTRIBUTES = "otel.config.max.attrs";
    private static final String KEY_SPAN_MAX_NUM_EVENTS = "otel.config.max.events";
    private static final String KEY_SPAN_MAX_NUM_LINKS = "otel.config.max.links";
    private static final String KEY_SPAN_MAX_NUM_ATTRIBUTES_PER_EVENT =
        "otel.config.max.event.attrs";
    private static final String KEY_SPAN_MAX_NUM_ATTRIBUTES_PER_LINK = "otel.config.max.link.attrs";
    private static final String KEY_SPAN_ATTRIBUTE_MAX_VALUE_LENGTH = "otel.config.max.attr.length";

    Builder() {}

    /**
     * Sets the configuration values from the given configuration map for only the available keys.
     *
     * @param configMap {@link Map} holding the configuration values.
     * @return this
     */
    @VisibleForTesting
    @Override
    protected Builder fromConfigMap(
        Map<String, String> configMap, Builder.NamingConvention namingConvention) {
      configMap = namingConvention.normalize(configMap);
      Double doubleValue = getDoubleProperty(KEY_SAMPLER_PROBABILITY, configMap);
      if (doubleValue != null) {
        this.setSamplerProbability(doubleValue);
      }
      Integer intValue = getIntProperty(KEY_SPAN_MAX_NUM_ATTRIBUTES, configMap);
      if (intValue != null) {
        this.setMaxNumberOfAttributes(intValue);
      }
      intValue = getIntProperty(KEY_SPAN_MAX_NUM_EVENTS, configMap);
      if (intValue != null) {
        this.setMaxNumberOfEvents(intValue);
      }
      intValue = getIntProperty(KEY_SPAN_MAX_NUM_LINKS, configMap);
      if (intValue != null) {
        this.setMaxNumberOfLinks(intValue);
      }
      intValue = getIntProperty(KEY_SPAN_MAX_NUM_ATTRIBUTES_PER_EVENT, configMap);
      if (intValue != null) {
        this.setMaxNumberOfAttributesPerEvent(intValue);
      }
      intValue = getIntProperty(KEY_SPAN_MAX_NUM_ATTRIBUTES_PER_LINK, configMap);
      if (intValue != null) {
        this.setMaxNumberOfAttributesPerLink(intValue);
      }
      intValue = getIntProperty(KEY_SPAN_ATTRIBUTE_MAX_VALUE_LENGTH, configMap);
      if (intValue != null) {
        this.setMaxLengthOfAttributeValues(intValue);
      }
      return this;
    }

    /**
     * * Sets the configuration values from the given properties object for only the available keys.
     *
     * @param properties {@link Properties} holding the configuration values.
     * @return this
     */
    @Override
    public Builder readProperties(Properties properties) {
      return super.readProperties(properties);
    }

    /**
     * * Sets the configuration values from environment variables for only the available keys.
     *
     * @return this.
     */
    @Override
    public Builder readEnvironmentVariables() {
      return super.readEnvironmentVariables();
    }

    /**
     * * Sets the configuration values from system properties for only the available keys.
     *
     * @return this.
     */
    @Override
    public Builder readSystemProperties() {
      return super.readSystemProperties();
    }

    /**
     * Sets the global default {@code Sampler}. It must be not {@code null} otherwise {@link
     * #build()} will throw an exception.
     *
     * @param sampler the global default {@code Sampler}.
     * @return this.
     */
    public abstract Builder setSampler(Sampler sampler);

    /**
     * Sets the global default {@code Sampler}. It must be not {@code null} otherwise {@link
     * #build()} will throw an exception.
     *
     * @param samplerProbability the global default probability used to make decisions on {@link
     *     Span} sampling.
     * @return this.
     */
    public Builder setSamplerProbability(double samplerProbability) {
      Utils.checkArgument(
          samplerProbability >= 0, "samplerProbability must be greater than or equal to 0.");
      Utils.checkArgument(
          samplerProbability <= 1, "samplerProbability must be lesser than or equal to 1.");
      if (samplerProbability == 1) {
        setSampler(Samplers.alwaysOn());
      } else if (samplerProbability == 0) {
        setSampler(Samplers.alwaysOff());
      } else {
        setSampler(Samplers.probability(samplerProbability));
      }
      return this;
    }

    /**
     * Sets the global default max number of attributes per {@link Span}.
     *
     * @param maxNumberOfAttributes the global default max number of attributes per {@link Span}. It
     *     must be positive otherwise {@link #build()} will throw an exception.
     * @return this.
     */
    public abstract Builder setMaxNumberOfAttributes(int maxNumberOfAttributes);

    /**
     * Sets the global default max number of {@link Event}s per {@link Span}.
     *
     * @param maxNumberOfEvents the global default max number of {@link Event}s per {@link Span}. It
     *     must be positive otherwise {@link #build()} will throw an exception.
     * @return this.
     */
    public abstract Builder setMaxNumberOfEvents(int maxNumberOfEvents);

    /**
     * Sets the global default max number of {@link Link} entries per {@link Span}.
     *
     * @param maxNumberOfLinks the global default max number of {@link Link} entries per {@link
     *     Span}. It must be positive otherwise {@link #build()} will throw an exception.
     * @return this.
     */
    public abstract Builder setMaxNumberOfLinks(int maxNumberOfLinks);

    /**
     * Sets the global default max number of attributes per {@link Event}.
     *
     * @param maxNumberOfAttributesPerEvent the global default max number of attributes per {@link
     *     Event}. It must be positive otherwise {@link #build()} will throw an exception.
     * @return this.
     */
    public abstract Builder setMaxNumberOfAttributesPerEvent(int maxNumberOfAttributesPerEvent);

    /**
     * Sets the global default max number of attributes per {@link Link}.
     *
     * @param maxNumberOfAttributesPerLink the global default max number of attributes per {@link
     *     Link}. It must be positive otherwise {@link #build()} will throw an exception.
     * @return this.
     */
    public abstract Builder setMaxNumberOfAttributesPerLink(int maxNumberOfAttributesPerLink);

    /**
     * Sets the global default max length of string attribute value in characters.
     *
     * @param maxLengthOfAttributeValues the global default max length of string attribute value in
     *     characters. It must be non-negative (or {@link #UNLIMITED_ATTRIBUTE_LENGTH}) otherwise
     *     {@link #build()} will throw an exception.
     * @return this.
     */
    public abstract Builder setMaxLengthOfAttributeValues(int maxLengthOfAttributeValues);

    abstract TraceConfig autoBuild();

    /**
     * Builds and returns a {@code TraceConfig} with the desired values.
     *
     * @return a {@code TraceConfig} with the desired values.
     * @throws IllegalArgumentException if any of the max numbers are not positive.
     */
    public TraceConfig build() {
      TraceConfig traceConfig = autoBuild();
      Preconditions.checkArgument(
          traceConfig.getMaxNumberOfAttributes() > 0, "maxNumberOfAttributes");
      Preconditions.checkArgument(traceConfig.getMaxNumberOfEvents() > 0, "maxNumberOfEvents");
      Preconditions.checkArgument(traceConfig.getMaxNumberOfLinks() > 0, "maxNumberOfLinks");
      Preconditions.checkArgument(
          traceConfig.getMaxNumberOfAttributesPerEvent() > 0, "maxNumberOfAttributesPerEvent");
      Preconditions.checkArgument(
          traceConfig.getMaxNumberOfAttributesPerLink() > 0, "maxNumberOfAttributesPerLink");
      Preconditions.checkArgument(
          traceConfig.getMaxLengthOfAttributeValues() >= -1, "maxLengthOfAttributeValues");
      return traceConfig;
    }
  }
}
