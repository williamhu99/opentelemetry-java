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

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** Benchmark class for {@link TracezDataAggregator}. */
@State(Scope.Benchmark)
public class TracezDataAggregatorBenchmark {

  private static final String runningSpan = "RUNNING_SPAN";
  private static final String latencySpan = "LATENCY_SPAN";
  private static final String errorSpan = "ERROR_SPAN";
  private final Tracer tracer =
      OpenTelemetrySdk.getTracerProvider().get("TracezDataAggregatorBenchmark");
  private final TracezSpanProcessor spanProcessor = TracezSpanProcessor.newBuilder().build();
  private final TracezDataAggregator dataAggregator = new TracezDataAggregator(spanProcessor);

  @Param({"1", "10", "1000", "1000000"})
  private int numberOfSpans;

  @Setup(Level.Trial)
  public final void setup() {
    for (int i = 0; i < numberOfSpans; i++) {
      tracer.spanBuilder(runningSpan).startSpan();
      tracer.spanBuilder(latencySpan).startSpan().end();
      Span error = tracer.spanBuilder(errorSpan).startSpan();
      error.setStatus(Status.UNKNOWN);
      error.end();
    }
  }

  /** Get span counts with 1 thread. */
  @Benchmark
  @Threads(value = 1)
  @Fork(1)
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 10, time = 1)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void getCounts_01Thread(Blackhole blackhole) {
    blackhole.consume(dataAggregator.getRunningSpanCounts());
    blackhole.consume(dataAggregator.getSpanLatencyCounts());
    blackhole.consume(dataAggregator.getErrorSpanCounts());
  }

  /** Get span counts with 5 threads. */
  @Benchmark
  @Threads(value = 5)
  @Fork(1)
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 10, time = 1)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void getCounts_05Threads(Blackhole blackhole) {
    blackhole.consume(dataAggregator.getRunningSpanCounts());
    blackhole.consume(dataAggregator.getSpanLatencyCounts());
    blackhole.consume(dataAggregator.getErrorSpanCounts());
  }

  /** Get span counts with 10 threads. */
  @Benchmark
  @Threads(value = 10)
  @Fork(1)
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 10, time = 1)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void getCounts_10Threads(Blackhole blackhole) {
    blackhole.consume(dataAggregator.getRunningSpanCounts());
    blackhole.consume(dataAggregator.getSpanLatencyCounts());
    blackhole.consume(dataAggregator.getErrorSpanCounts());
  }

  /** Get span counts with 20 threads. */
  @Benchmark
  @Threads(value = 20)
  @Fork(1)
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 10, time = 1)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void getCounts_20Threads(Blackhole blackhole) {
    blackhole.consume(dataAggregator.getRunningSpanCounts());
    blackhole.consume(dataAggregator.getSpanLatencyCounts());
    blackhole.consume(dataAggregator.getErrorSpanCounts());
  }

  /** Get spans with 1 thread. */
  @Benchmark
  @Threads(value = 1)
  @Fork(1)
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 10, time = 1)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void getSpans_01Thread(Blackhole blackhole) {
    blackhole.consume(dataAggregator.getRunningSpans(runningSpan));
    blackhole.consume(dataAggregator.getOkSpans(latencySpan, 0, Long.MAX_VALUE));
    blackhole.consume(dataAggregator.getErrorSpans(errorSpan));
  }

  /** Get spans with 5 threads. */
  @Benchmark
  @Threads(value = 5)
  @Fork(1)
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 10, time = 1)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void getSpans_05Threads(Blackhole blackhole) {
    blackhole.consume(dataAggregator.getRunningSpans(runningSpan));
    blackhole.consume(dataAggregator.getOkSpans(latencySpan, 0, Long.MAX_VALUE));
    blackhole.consume(dataAggregator.getErrorSpans(errorSpan));
  }

  /** Get spans with 10 threads. */
  @Benchmark
  @Threads(value = 10)
  @Fork(1)
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 10, time = 1)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void getSpans_10Threads(Blackhole blackhole) {
    blackhole.consume(dataAggregator.getRunningSpans(runningSpan));
    blackhole.consume(dataAggregator.getOkSpans(latencySpan, 0, Long.MAX_VALUE));
    blackhole.consume(dataAggregator.getErrorSpans(errorSpan));
  }

  /** Get spans with 20 threads. */
  @Benchmark
  @Threads(value = 20)
  @Fork(1)
  @Warmup(iterations = 5, time = 1)
  @Measurement(iterations = 10, time = 1)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void getSpans_20Threads(Blackhole blackhole) {
    blackhole.consume(dataAggregator.getRunningSpans(runningSpan));
    blackhole.consume(dataAggregator.getOkSpans(latencySpan, 0, Long.MAX_VALUE));
    blackhole.consume(dataAggregator.getErrorSpans(errorSpan));
  }
}
