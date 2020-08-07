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

package io.opentelemetry.exporters.prometheus;

import static io.prometheus.client.Collector.doubleToGoString;

import io.opentelemetry.common.ReadableKeyValuePairs.KeyValueConsumer;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricData.Descriptor;
import io.opentelemetry.sdk.metrics.data.MetricData.DoublePoint;
import io.opentelemetry.sdk.metrics.data.MetricData.LongPoint;
import io.opentelemetry.sdk.metrics.data.MetricData.Point;
import io.opentelemetry.sdk.metrics.data.MetricData.SummaryPoint;
import io.opentelemetry.sdk.metrics.data.MetricData.ValueAtPercentile;
import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Util methods to convert OpenTelemetry Metrics data models to Prometheus data models.
 *
 * <p>Each OpenTelemetry {@link MetricData} will be converted to a Prometheus {@link
 * MetricFamilySamples}, and each {@code Point} of the {@link MetricData} will be converted to
 * Prometheus {@link Sample}s.
 *
 * <p>{@code DoublePoint}, {@code LongPoint} will be converted to a single {@link Sample}. {@code
 * Summary} will be converted to two {@link Sample}s (sum and count) plus the number of Percentile
 * values {@code Sample}s
 *
 * <p>Please note that Prometheus Metric and Label name can only have alphanumeric characters and
 * underscore. All other characters will be sanitized by underscores.
 */
final class MetricAdapter {

  static final String SAMPLE_SUFFIX_COUNT = "_count";
  static final String SAMPLE_SUFFIX_SUM = "_sum";
  static final String LABEL_NAME_QUANTILE = "quantile";

  // Converts a MetricData to a Prometheus MetricFamilySamples.
  static MetricFamilySamples toMetricFamilySamples(MetricData metricData) {
    Descriptor descriptor = metricData.getDescriptor();
    String cleanMetricName = cleanMetricName(descriptor.getName());
    Collector.Type type = toMetricFamilyType(descriptor.getType());

    return new MetricFamilySamples(
        cleanMetricName,
        type,
        descriptor.getDescription(),
        toSamples(cleanMetricName, descriptor, metricData.getPoints()));
  }

  private static String cleanMetricName(String descriptorMetricName) {
    return Collector.sanitizeMetricName(descriptorMetricName);
  }

  static Collector.Type toMetricFamilyType(MetricData.Descriptor.Type type) {
    switch (type) {
      case NON_MONOTONIC_LONG:
      case NON_MONOTONIC_DOUBLE:
        return Collector.Type.GAUGE;
      case MONOTONIC_LONG:
      case MONOTONIC_DOUBLE:
        return Collector.Type.COUNTER;
      case SUMMARY:
        return Collector.Type.SUMMARY;
    }
    return Collector.Type.UNTYPED;
  }

  // Converts a list of points from MetricData to a list of Prometheus Samples.
  static List<Sample> toSamples(String name, Descriptor descriptor, Collection<Point> points) {
    final List<Sample> samples =
        new ArrayList<>(estimateNumSamples(points.size(), descriptor.getType()));

    List<String> constLabelNames = Collections.emptyList();
    List<String> constLabelValues = Collections.emptyList();
    if (descriptor.getConstantLabels().size() != 0) {
      constLabelNames = new ArrayList<>(descriptor.getConstantLabels().size());
      constLabelValues = new ArrayList<>(descriptor.getConstantLabels().size());
      descriptor.getConstantLabels().forEach(new Consumer(constLabelNames, constLabelValues));
    }

    for (Point point : points) {
      List<String> labelNames = Collections.emptyList();
      List<String> labelValues = Collections.emptyList();
      if (constLabelNames.size() + point.getLabels().size() != 0) {
        labelNames =
            new ArrayList<>(descriptor.getConstantLabels().size() + point.getLabels().size());
        labelNames.addAll(constLabelNames);
        labelValues =
            new ArrayList<>(descriptor.getConstantLabels().size() + point.getLabels().size());
        labelValues.addAll(constLabelValues);

        // TODO: Use a cache(map) of converted label names to avoid sanitization multiple times
        point.getLabels().forEach(new Consumer(labelNames, labelValues));
      }

      switch (descriptor.getType()) {
        case MONOTONIC_DOUBLE:
        case NON_MONOTONIC_DOUBLE:
          DoublePoint doublePoint = (DoublePoint) point;
          samples.add(new Sample(name, labelNames, labelValues, doublePoint.getValue()));
          break;
        case MONOTONIC_LONG:
        case NON_MONOTONIC_LONG:
          LongPoint longPoint = (LongPoint) point;
          samples.add(new Sample(name, labelNames, labelValues, longPoint.getValue()));
          break;
        case SUMMARY:
          addSummarySamples((SummaryPoint) point, name, labelNames, labelValues, samples);
          break;
      }
    }
    return samples;
  }

  // Converts a label keys to a label names. Sanitizes the label keys.
  static String toLabelName(String labelKey) {
    return Collector.sanitizeMetricName(labelKey);
  }

  private static final class Consumer implements KeyValueConsumer<String> {
    final List<String> labelNames;
    final List<String> labelValues;

    private Consumer(List<String> labelNames, List<String> labelValues) {
      this.labelNames = labelNames;
      this.labelValues = labelValues;
    }

    @Override
    public void consume(String key, String value) {
      labelNames.add(toLabelName(key));
      labelValues.add(value == null ? "" : value);
    }
  }

  private static void addSummarySamples(
      SummaryPoint summaryPoint,
      String name,
      List<String> labelNames,
      List<String> labelValues,
      List<Sample> samples) {
    samples.add(
        new Sample(name + SAMPLE_SUFFIX_COUNT, labelNames, labelValues, summaryPoint.getCount()));
    samples.add(
        new Sample(name + SAMPLE_SUFFIX_SUM, labelNames, labelValues, summaryPoint.getSum()));
    List<ValueAtPercentile> valueAtPercentiles = summaryPoint.getPercentileValues();
    List<String> labelNamesWithQuantile = new ArrayList<>(labelNames.size());
    labelNamesWithQuantile.addAll(labelNames);
    labelNamesWithQuantile.add(LABEL_NAME_QUANTILE);
    for (ValueAtPercentile valueAtPercentile : valueAtPercentiles) {
      List<String> labelValuesWithQuantile = new ArrayList<>(labelValues.size());
      labelValuesWithQuantile.addAll(labelValues);
      labelValuesWithQuantile.add(doubleToGoString(valueAtPercentile.getPercentile()));
      samples.add(
          new Sample(
              name, labelNamesWithQuantile, labelValuesWithQuantile, valueAtPercentile.getValue()));
    }
  }

  private static int estimateNumSamples(int numPoints, Descriptor.Type type) {
    switch (type) {
      case NON_MONOTONIC_LONG:
      case NON_MONOTONIC_DOUBLE:
      case MONOTONIC_LONG:
      case MONOTONIC_DOUBLE:
        return numPoints;
      case SUMMARY:
        // count + sum + estimated 2 percentiles (default MinMaxSumCount aggregator).
        return numPoints * 4;
    }
    return numPoints;
  }

  private MetricAdapter() {}
}
