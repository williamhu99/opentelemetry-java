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

import com.google.common.primitives.UnsignedInts;
import io.opentelemetry.sdk.trace.ReadableSpan;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

final class SpanBucket {
  // A power of 2 means Integer.MAX_VALUE % bucketSize = bucketSize - 1, so the index will always
  // loop back to 0.
  private static final int LATENCY_BUCKET_SIZE = 16;
  private static final int ERROR_BUCKET_SIZE = 8;

  private final AtomicReferenceArray<ReadableSpan> spans;
  private final AtomicInteger index;
  private final int bucketSize;

  SpanBucket(boolean isLatencyBucket) {
    bucketSize = isLatencyBucket ? LATENCY_BUCKET_SIZE : ERROR_BUCKET_SIZE;
    spans = new AtomicReferenceArray<>(bucketSize);
    index = new AtomicInteger();
  }

  void add(ReadableSpan span) {
    spans.set(UnsignedInts.remainder(index.getAndIncrement(), bucketSize), span);
  }

  int size() {
    for (int i = bucketSize - 1; i >= 0; i--) {
      if (spans.get(i) != null) {
        return i + 1;
      }
    }
    return 0;
  }

  void addTo(List<ReadableSpan> result) {
    for (int i = 0; i < bucketSize; i++) {
      ReadableSpan span = spans.get(i);
      if (span != null) {
        result.add(span);
      } else {
        break;
      }
    }
  }
}
