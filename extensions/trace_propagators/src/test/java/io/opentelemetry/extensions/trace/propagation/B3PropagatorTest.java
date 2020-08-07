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

package io.opentelemetry.extensions.trace.propagation;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Context;
import io.opentelemetry.context.propagation.HttpTextFormat.Getter;
import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.TraceFlags;
import io.opentelemetry.trace.TraceId;
import io.opentelemetry.trace.TraceState;
import io.opentelemetry.trace.TracingContextUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link io.opentelemetry.trace.propagation.HttpTraceContext}. */
class B3PropagatorTest {

  private static final TraceState TRACE_STATE_DEFAULT = TraceState.builder().build();
  private static final String TRACE_ID_BASE16 = "ff000000000000000000000000000041";
  private static final String TRACE_ID_ALL_ZERO = "00000000000000000000000000000000";
  private static final TraceId TRACE_ID = TraceId.fromLowerBase16(TRACE_ID_BASE16, 0);
  private static final String SHORT_TRACE_ID_BASE16 = "ff00000000000000";
  private static final TraceId SHORT_TRACE_ID =
      TraceId.fromLowerBase16(StringUtils.padLeft(SHORT_TRACE_ID_BASE16, 32), 0);
  private static final String SPAN_ID_BASE16 = "ff00000000000041";
  private static final String SPAN_ID_ALL_ZERO = "0000000000000000";
  private static final SpanId SPAN_ID = SpanId.fromLowerBase16(SPAN_ID_BASE16, 0);
  private static final byte SAMPLED_TRACE_OPTIONS_BYTES = 1;
  private static final TraceFlags SAMPLED_TRACE_OPTIONS =
      TraceFlags.fromByte(SAMPLED_TRACE_OPTIONS_BYTES);
  private static final Setter<Map<String, String>> setter = Map::put;
  private static final Getter<Map<String, String>> getter =
      new Getter<Map<String, String>>() {
        @Nullable
        @Override
        public String get(Map<String, String> carrier, String key) {
          return carrier.get(key);
        }
      };
  private final B3Propagator b3Propagator = B3Propagator.getMultipleHeaderPropagator();
  private final B3Propagator b3PropagatorSingleHeader = B3Propagator.getSingleHeaderPropagator();

  private static SpanContext getSpanContext(Context context) {
    return TracingContextUtils.getSpan(context).getContext();
  }

  private static Context withSpanContext(SpanContext spanContext, Context context) {
    return TracingContextUtils.withSpan(DefaultSpan.create(spanContext), context);
  }

  @Test
  void inject_invalidContext() {
    Map<String, String> carrier = new LinkedHashMap<>();
    b3Propagator.inject(
        withSpanContext(
            SpanContext.create(
                TraceId.getInvalid(),
                SpanId.getInvalid(),
                SAMPLED_TRACE_OPTIONS,
                TraceState.builder().set("foo", "bar").build()),
            Context.current()),
        carrier,
        setter);
    assertThat(carrier).hasSize(0);
  }

  @Test
  void inject_SampledContext() {
    Map<String, String> carrier = new LinkedHashMap<>();
    b3Propagator.inject(
        withSpanContext(
            SpanContext.create(TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT),
            Context.current()),
        carrier,
        setter);
    assertThat(carrier).containsEntry(B3Propagator.TRACE_ID_HEADER, TRACE_ID_BASE16);
    assertThat(carrier).containsEntry(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16);
    assertThat(carrier).containsEntry(B3Propagator.SAMPLED_HEADER, "1");
  }

  @Test
  void inject_SampledContext_nullCarrierUsage() {
    final Map<String, String> carrier = new LinkedHashMap<>();
    b3Propagator.inject(
        withSpanContext(
            SpanContext.create(TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT),
            Context.current()),
        null,
        (Setter<Map<String, String>>) (ignored, key, value) -> carrier.put(key, value));
    assertThat(carrier).containsEntry(B3Propagator.TRACE_ID_HEADER, TRACE_ID_BASE16);
    assertThat(carrier).containsEntry(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16);
    assertThat(carrier).containsEntry(B3Propagator.SAMPLED_HEADER, "1");
  }

  @Test
  void inject_NotSampledContext() {
    Map<String, String> carrier = new LinkedHashMap<>();
    b3Propagator.inject(
        withSpanContext(
            SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TRACE_STATE_DEFAULT),
            Context.current()),
        carrier,
        setter);
    assertThat(carrier).containsEntry(B3Propagator.TRACE_ID_HEADER, TRACE_ID_BASE16);
    assertThat(carrier).containsEntry(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16);
    assertThat(carrier).containsEntry(B3Propagator.SAMPLED_HEADER, "0");
  }

  @Test
  void extract_Nothing() {
    // Context remains untouched.
    assertThat(
            b3Propagator.extract(
                Context.current(), Collections.<String, String>emptyMap(), Map::get))
        .isSameAs(Context.current());
  }

  @Test
  void extract_SampledContext_Int() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(B3Propagator.TRACE_ID_HEADER, TRACE_ID_BASE16);
    carrier.put(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16);
    carrier.put(B3Propagator.SAMPLED_HEADER, Common.TRUE_INT);

    assertThat(getSpanContext(b3Propagator.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_SampledContext_Bool() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(B3Propagator.TRACE_ID_HEADER, TRACE_ID_BASE16);
    carrier.put(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16);
    carrier.put(B3Propagator.SAMPLED_HEADER, "true");

    assertThat(getSpanContext(b3Propagator.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_NotSampledContext() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(B3Propagator.TRACE_ID_HEADER, TRACE_ID_BASE16);
    carrier.put(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16);
    carrier.put(B3Propagator.SAMPLED_HEADER, Common.FALSE_INT);

    assertThat(getSpanContext(b3Propagator.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_SampledContext_Int_Short_TraceId() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(B3Propagator.TRACE_ID_HEADER, SHORT_TRACE_ID_BASE16);
    carrier.put(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16);
    carrier.put(B3Propagator.SAMPLED_HEADER, Common.TRUE_INT);

    assertThat(getSpanContext(b3Propagator.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                SHORT_TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_SampledContext_Bool_Short_TraceId() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(B3Propagator.TRACE_ID_HEADER, SHORT_TRACE_ID_BASE16);
    carrier.put(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16);
    carrier.put(B3Propagator.SAMPLED_HEADER, "true");

    assertThat(getSpanContext(b3Propagator.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                SHORT_TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_NotSampledContext_Short_TraceId() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(B3Propagator.TRACE_ID_HEADER, SHORT_TRACE_ID_BASE16);
    carrier.put(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16);
    carrier.put(B3Propagator.SAMPLED_HEADER, Common.FALSE_INT);

    assertThat(getSpanContext(b3Propagator.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                SHORT_TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_InvalidTraceId_NotHex() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(B3Propagator.TRACE_ID_HEADER, "g" + TRACE_ID_BASE16.substring(1));
    invalidHeaders.put(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16);
    invalidHeaders.put(B3Propagator.SAMPLED_HEADER, Common.TRUE_INT);
    assertThat(getSpanContext(b3Propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_InvalidTraceId_TooShort() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(B3Propagator.TRACE_ID_HEADER, TRACE_ID_BASE16.substring(2));
    invalidHeaders.put(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16);
    invalidHeaders.put(B3Propagator.SAMPLED_HEADER, Common.TRUE_INT);
    assertThat(getSpanContext(b3Propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_InvalidTraceId_TooLong() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(B3Propagator.TRACE_ID_HEADER, TRACE_ID_BASE16 + "00");
    invalidHeaders.put(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16);
    invalidHeaders.put(B3Propagator.SAMPLED_HEADER, Common.TRUE_INT);
    assertThat(getSpanContext(b3Propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_InvalidTraceId_AllZero() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(B3Propagator.TRACE_ID_HEADER, TRACE_ID_ALL_ZERO);
    invalidHeaders.put(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16);
    invalidHeaders.put(B3Propagator.SAMPLED_HEADER, Common.TRUE_INT);
    assertThat(getSpanContext(b3Propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_InvalidSpanId_NotHex() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(B3Propagator.TRACE_ID_HEADER, TRACE_ID_BASE16);
    invalidHeaders.put(B3Propagator.SPAN_ID_HEADER, "g" + SPAN_ID_BASE16.substring(1));
    invalidHeaders.put(B3Propagator.SAMPLED_HEADER, Common.TRUE_INT);
    assertThat(getSpanContext(b3Propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_InvalidSpanId_TooShort() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(B3Propagator.TRACE_ID_HEADER, TRACE_ID_BASE16);
    invalidHeaders.put(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16.substring(2));
    invalidHeaders.put(B3Propagator.SAMPLED_HEADER, Common.TRUE_INT);
    assertThat(getSpanContext(b3Propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_InvalidSpanId_TooLong() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(B3Propagator.TRACE_ID_HEADER, TRACE_ID_BASE16);
    invalidHeaders.put(B3Propagator.SPAN_ID_HEADER, SPAN_ID_BASE16 + "00");
    invalidHeaders.put(B3Propagator.SAMPLED_HEADER, Common.TRUE_INT);
    assertThat(getSpanContext(b3Propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_InvalidSpanId_AllZeros() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(B3Propagator.TRACE_ID_HEADER, TRACE_ID_BASE16);
    invalidHeaders.put(B3Propagator.SPAN_ID_HEADER, SPAN_ID_ALL_ZERO);
    invalidHeaders.put(B3Propagator.SAMPLED_HEADER, Common.TRUE_INT);
    assertThat(getSpanContext(b3Propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void inject_invalidContext_SingleHeader() {
    Map<String, String> carrier = new LinkedHashMap<>();
    b3PropagatorSingleHeader.inject(
        withSpanContext(
            SpanContext.create(
                TraceId.getInvalid(),
                SpanId.getInvalid(),
                SAMPLED_TRACE_OPTIONS,
                TraceState.builder().set("foo", "bar").build()),
            Context.current()),
        carrier,
        setter);
    assertThat(carrier).hasSize(0);
  }

  @Test
  void inject_SampledContext_SingleHeader() {
    Map<String, String> carrier = new LinkedHashMap<>();
    b3PropagatorSingleHeader.inject(
        withSpanContext(
            SpanContext.create(TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT),
            Context.current()),
        carrier,
        setter);

    assertThat(carrier)
        .containsEntry(
            B3Propagator.COMBINED_HEADER, TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-" + "1");
  }

  @Test
  void inject_NotSampledContext_SingleHeader() {
    Map<String, String> carrier = new LinkedHashMap<>();
    b3PropagatorSingleHeader.inject(
        withSpanContext(
            SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TRACE_STATE_DEFAULT),
            Context.current()),
        carrier,
        setter);

    assertThat(carrier)
        .containsEntry(
            B3Propagator.COMBINED_HEADER, TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-" + "0");
  }

  @Test
  void extract_Nothing_SingleHeader() {
    // Context remains untouched.
    assertThat(
            b3PropagatorSingleHeader.extract(
                Context.current(), Collections.<String, String>emptyMap(), Map::get))
        .isSameAs(Context.current());
  }

  @Test
  void extract_SampledContext_Int_SingleHeader() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(
        B3Propagator.COMBINED_HEADER,
        TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-" + Common.TRUE_INT);

    assertThat(getSpanContext(b3PropagatorSingleHeader.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_SampledContext_DebugFlag_SingleHeader() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(
        B3Propagator.COMBINED_HEADER,
        TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-" + Common.TRUE_INT + "-" + "0");

    assertThat(getSpanContext(b3PropagatorSingleHeader.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_SampledContext_Bool_SingleHeader() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(
        B3Propagator.COMBINED_HEADER, TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-" + "true");

    assertThat(getSpanContext(b3PropagatorSingleHeader.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_SampledContext_Bool_DebugFlag_SingleHeader() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(
        B3Propagator.COMBINED_HEADER,
        TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-" + "true" + "-" + "0");

    assertThat(getSpanContext(b3PropagatorSingleHeader.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_NotSampledContext_SingleHeader() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(
        B3Propagator.COMBINED_HEADER,
        TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-" + Common.FALSE_INT);

    assertThat(getSpanContext(b3PropagatorSingleHeader.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_SampledContext_Int_SingleHeader_Short_TraceId() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(
        B3Propagator.COMBINED_HEADER,
        SHORT_TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-" + Common.TRUE_INT);

    assertThat(getSpanContext(b3PropagatorSingleHeader.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                SHORT_TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_SampledContext_DebugFlag_SingleHeader_Short_TraceId() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(
        B3Propagator.COMBINED_HEADER,
        SHORT_TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-" + Common.TRUE_INT + "-" + "0");

    assertThat(getSpanContext(b3PropagatorSingleHeader.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                SHORT_TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_SampledContext_Bool_SingleHeader_Short_TraceId() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(
        B3Propagator.COMBINED_HEADER, SHORT_TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-" + "true");

    assertThat(getSpanContext(b3PropagatorSingleHeader.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                SHORT_TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_SampledContext_Bool_DebugFlag_SingleHeader_Short_TraceId() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(
        B3Propagator.COMBINED_HEADER,
        SHORT_TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-" + "true" + "-" + "0");

    assertThat(getSpanContext(b3PropagatorSingleHeader.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                SHORT_TRACE_ID, SPAN_ID, SAMPLED_TRACE_OPTIONS, TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_NotSampledContext_SingleHeader_Short_TraceId() {
    Map<String, String> carrier = new LinkedHashMap<>();
    carrier.put(
        B3Propagator.COMBINED_HEADER,
        SHORT_TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-" + Common.FALSE_INT);

    assertThat(getSpanContext(b3PropagatorSingleHeader.extract(Context.current(), carrier, getter)))
        .isEqualTo(
            SpanContext.createFromRemoteParent(
                SHORT_TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TRACE_STATE_DEFAULT));
  }

  @Test
  void extract_Null_SingleHeader() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(B3Propagator.COMBINED_HEADER, null);

    assertThat(
            getSpanContext(
                b3PropagatorSingleHeader.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_Empty_SingleHeader() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(B3Propagator.COMBINED_HEADER, "");

    assertThat(
            getSpanContext(
                b3PropagatorSingleHeader.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_InvalidTraceId_SingleHeader() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(
        B3Propagator.COMBINED_HEADER,
        "abcdefghijklmnopabcdefghijklmnop" + "-" + SPAN_ID_BASE16 + "-" + Common.TRUE_INT);

    assertThat(
            getSpanContext(
                b3PropagatorSingleHeader.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_InvalidTraceId_Size_SingleHeader() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(
        B3Propagator.COMBINED_HEADER,
        "abcdefghijklmnopabcdefghijklmnop" + "00" + "-" + SPAN_ID_BASE16 + "-" + Common.TRUE_INT);

    assertThat(
            getSpanContext(
                b3PropagatorSingleHeader.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_InvalidSpanId_SingleHeader() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(
        B3Propagator.COMBINED_HEADER,
        TRACE_ID_BASE16 + "-" + "abcdefghijklmnop" + "-" + Common.TRUE_INT);

    assertThat(
            getSpanContext(
                b3PropagatorSingleHeader.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_InvalidSpanId_Size_SingleHeader() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(
        B3Propagator.COMBINED_HEADER,
        TRACE_ID_BASE16 + "-" + "abcdefghijklmnop" + "00" + "-" + Common.TRUE_INT);

    assertThat(getSpanContext(b3Propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_TooFewParts_SingleHeader() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(B3Propagator.COMBINED_HEADER, TRACE_ID_BASE16);

    assertThat(getSpanContext(b3Propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void extract_TooManyParts_SingleHeader() {
    Map<String, String> invalidHeaders = new LinkedHashMap<>();
    invalidHeaders.put(
        B3Propagator.COMBINED_HEADER,
        TRACE_ID_BASE16 + "-" + SPAN_ID_BASE16 + "-" + Common.TRUE_INT + "-extra");

    assertThat(getSpanContext(b3Propagator.extract(Context.current(), invalidHeaders, getter)))
        .isSameAs(SpanContext.getInvalid());
  }

  @Test
  void fieldsList() {
    assertThat(b3Propagator.fields())
        .containsExactly(
            B3Propagator.TRACE_ID_HEADER, B3Propagator.SPAN_ID_HEADER, B3Propagator.SAMPLED_HEADER);
  }

  @Test
  void headerNames() {
    assertThat(B3Propagator.TRACE_ID_HEADER).isEqualTo("X-B3-TraceId");
    assertThat(B3Propagator.SPAN_ID_HEADER).isEqualTo("X-B3-SpanId");
    assertThat(B3Propagator.SAMPLED_HEADER).isEqualTo("X-B3-Sampled");
  }

  @Test
  void extract_emptyCarrier() {
    Map<String, String> emptyHeaders = new HashMap<>();
    assertThat(getSpanContext(b3Propagator.extract(Context.current(), emptyHeaders, getter)))
        .isEqualTo(SpanContext.getInvalid());
  }
}
