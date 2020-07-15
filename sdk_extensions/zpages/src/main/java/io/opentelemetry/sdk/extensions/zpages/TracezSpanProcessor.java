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

import io.opentelemetry.sdk.common.export.ConfigBuilder;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.trace.SpanId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A {@link SpanProcessor} implementation for the traceZ zPage.
 *
 * <p>Configuration options for {@link TracezSpanProcessor} can be read from system properties,
 * environment variables, or {@link java.util.Properties} objects.
 *
 * <p>For system properties and {@link java.util.Properties} objects, {@link TracezSpanProcessor}
 * will look for the following names:
 *
 * <ul>
 *   <li>{@code otel.zpages.export.sampled}: sets whether only sampled spans should be exported.
 * </ul>
 *
 * <p>For environment variables, {@link TracezSpanProcessor} will look for the following names:
 *
 * <ul>
 *   <li>{@code OTEL_ZPAGES_EXPORT_SAMPLED}: sets whether only sampled spans should be exported.
 * </ul>
 */
@ThreadSafe
final class TracezSpanProcessor implements SpanProcessor {
  private final ConcurrentMap<SpanId, ReadableSpan> runningSpanCache;
  private final ConcurrentMap<String, TracezSpanBuckets> completedSpanCache;
  private final boolean sampled;

  /**
   * Constructor for {@link TracezSpanProcessor}.
   *
   * @param sampled report only sampled spans.
   */
  TracezSpanProcessor(boolean sampled) {
    runningSpanCache = new ConcurrentHashMap<>();
    completedSpanCache = new ConcurrentHashMap<>();
    this.sampled = sampled;
  }

  @Override
  public void onStart(ReadableSpan span) {
    runningSpanCache.put(span.getSpanContext().getSpanId(), span);
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(ReadableSpan span) {
    runningSpanCache.remove(span.getSpanContext().getSpanId());
    if (!sampled || span.getSpanContext().getTraceFlags().isSampled()) {
      completedSpanCache.putIfAbsent(span.getName(), new TracezSpanBuckets());
      completedSpanCache.get(span.getName()).addToBucket(span);
    }
  }

  @Override
  public boolean isEndRequired() {
    return true;
  }

  @Override
  public void shutdown() {
    // Do nothing.
  }

  @Override
  public void forceFlush() {
    // Do nothing.
  }

  /**
   * Returns a Collection of all running spans for {@link TracezSpanProcessor}.
   *
   * @return a Collection of {@link ReadableSpan}.
   */
  Collection<ReadableSpan> getRunningSpans() {
    return runningSpanCache.values();
  }

  /**
   * Returns a Collection of all completed spans for {@link TracezSpanProcessor}.
   *
   * @return a Collection of {@link ReadableSpan}.
   */
  Collection<ReadableSpan> getCompletedSpans() {
    Collection<ReadableSpan> completedSpans = new ArrayList<>();
    for (TracezSpanBuckets buckets : completedSpanCache.values()) {
      completedSpans.addAll(buckets.getSpans());
    }
    return completedSpans;
  }

  /**
   * Returns the completed span cache for {@link TracezSpanProcessor}.
   *
   * @return a Map of String to {@link TracezSpanBuckets}.
   */
  Map<String, TracezSpanBuckets> getCompletedSpanCache() {
    return completedSpanCache;
  }

  /**
   * Returns a new Builder for {@link TracezSpanProcessor}.
   *
   * @return a new {@link TracezSpanProcessor}.
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  /** Builder class for {@link TracezSpanProcessor}. */
  public static final class Builder extends ConfigBuilder<Builder> {

    private static final String KEY_SAMPLED = "otel.zpages.export.sampled";
    private static final boolean DEFAULT_EXPORT_ONLY_SAMPLED = true;
    private boolean sampled = DEFAULT_EXPORT_ONLY_SAMPLED;

    private Builder() {}

    /**
     * Sets the configuration values from the given configuration map for only the available keys.
     * This method looks for the following keys:
     *
     * <ul>
     *   <li>{@code otel.zpages.export.sampled}: to set whether only sampled spans should be
     *       exported.
     * </ul>
     *
     * @param configMap {@link Map} holding the configuration values.
     * @return this.
     */
    @Override
    protected Builder fromConfigMap(
        Map<String, String> configMap, NamingConvention namingConvention) {
      configMap = namingConvention.normalize(configMap);
      Boolean boolValue = getBooleanProperty(KEY_SAMPLED, configMap);
      if (boolValue != null) {
        return this.setExportOnlySampled(boolValue);
      }
      return this;
    }

    /**
     * Sets whether only sampled spans should be exported.
     *
     * <p>Default value is {@code true}.
     *
     * @see Builder#DEFAULT_EXPORT_ONLY_SAMPLED
     * @param sampled report only sampled spans.
     * @return this.
     */
    public Builder setExportOnlySampled(boolean sampled) {
      this.sampled = sampled;
      return this;
    }

    /**
     * Returns a new {@link TracezSpanProcessor}.
     *
     * @return a new {@link TracezSpanProcessor}.
     */
    public TracezSpanProcessor build() {
      return new TracezSpanProcessor(sampled);
    }
  }
}
