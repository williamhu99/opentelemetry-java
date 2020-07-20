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

import static com.google.common.truth.Truth.assertThat;

import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SpanBucket}. */
@RunWith(JUnit4.class)
public final class SpanBucketTest {
  private static final String SPAN_NAME = "span";
  private static final int LATENCY_BUCKET_SIZE = 16;
  private static final int ERROR_BUCKET_SIZE = 8;
  private final TracerSdkProvider tracerSdkProvider = TracerSdkProvider.builder().build();
  private final Tracer tracer = tracerSdkProvider.get("SpanBucketTest");

  @Test
  public void verifyLatencyBucketSizeLimit() {
    SpanBucket latencyBucket = new SpanBucket(true);
    Span[] spans = new Span[LATENCY_BUCKET_SIZE + 1];
    for (int i = 0; i < LATENCY_BUCKET_SIZE + 1; i++) {
      spans[i] = tracer.spanBuilder(SPAN_NAME).startSpan();
      latencyBucket.add((ReadableSpan) spans[i]);
      spans[i].end();
    }
    List<ReadableSpan> bucketSpans = new ArrayList<>();
    latencyBucket.addTo(bucketSpans);
    /* The latency SpanBucket should have the most recent LATENCY_BUCKET_SIZE spans */
    assertThat(latencyBucket.size()).isEqualTo(LATENCY_BUCKET_SIZE);
    assertThat(bucketSpans.size()).isEqualTo(LATENCY_BUCKET_SIZE);
    assertThat(bucketSpans).doesNotContain(spans[0]);
    for (int i = 1; i < LATENCY_BUCKET_SIZE + 1; i++) {
      assertThat(bucketSpans).contains(spans[i]);
    }
  }

  @Test
  public void verifyErrorBucketSizeLimit() {
    SpanBucket errorBucket = new SpanBucket(false);
    Span[] spans = new Span[ERROR_BUCKET_SIZE + 1];
    for (int i = 0; i < ERROR_BUCKET_SIZE + 1; i++) {
      spans[i] = tracer.spanBuilder(SPAN_NAME).startSpan();
      errorBucket.add((ReadableSpan) spans[i]);
      spans[i].end();
    }
    List<ReadableSpan> bucketSpans = new ArrayList<>();
    errorBucket.addTo(bucketSpans);
    /* The error SpanBucket should have the most recent ERROR_BUCKET_SIZE spans */
    assertThat(errorBucket.size()).isEqualTo(ERROR_BUCKET_SIZE);
    assertThat(bucketSpans.size()).isEqualTo(ERROR_BUCKET_SIZE);
    assertThat(bucketSpans).doesNotContain(spans[0]);
    for (int i = 1; i < ERROR_BUCKET_SIZE + 1; i++) {
      assertThat(bucketSpans).contains(spans[i]);
    }
  }

  @Test(timeout = 1000)
  public void verifyThreadSafety() throws InterruptedException {
    int numberOfThreads = 4;
    int numberOfSpans = 4;
    SpanBucket spanBucket = new SpanBucket(true);
    final CountDownLatch startSignal = new CountDownLatch(1);
    final CountDownLatch endSignal = new CountDownLatch(numberOfThreads);
    for (int i = 0; i < numberOfThreads; i++) {
      new Thread(
              () -> {
                try {
                  startSignal.await();
                  for (int j = 0; j < numberOfSpans; j++) {
                    Span span = tracer.spanBuilder(SPAN_NAME).startSpan();
                    spanBucket.add((ReadableSpan) span);
                    span.end();
                  }
                  endSignal.countDown();
                } catch (InterruptedException e) {
                  return;
                }
              })
          .start();
    }
    startSignal.countDown();
    endSignal.await();
    /* The SpanBucket should have exactly 16 spans */
    assertThat(spanBucket.size()).isEqualTo(numberOfThreads * numberOfSpans);
  }
}
