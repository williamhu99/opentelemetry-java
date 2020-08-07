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

package io.opentelemetry.extensions.trace.propagation;

import io.grpc.Context;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.TracingContextUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.concurrent.Immutable;

/**
 * Implementation of the Lightstep propagation protocol. Context is propagated through 3 headers,
 * ot-tracer-traceid, ot-tracer-span-id, and ot-tracer-sampled. Baggage is not supported in this
 * implementation. IDs are sent as hex strings and sampled is sent as true or false. See <a
 * href=https://github.com/lightstep/lightstep-tracer-java-common/blob/master/common/src/main/java/com/lightstep/tracer/shared/TextMapPropagator.java>Lightstep
 * TextMapPropagator</a>.
 */
@Immutable
public class OtTracerPropagator implements HttpTextFormat {

  static final String TRACE_ID_HEADER = "ot-tracer-traceid";
  static final String SPAN_ID_HEADER = "ot-tracer-spanid";
  static final String SAMPLED_HEADER = "ot-tracer-sampled";
  private static final List<String> FIELDS =
      Collections.unmodifiableList(Arrays.asList(TRACE_ID_HEADER, SPAN_ID_HEADER, SAMPLED_HEADER));

  private static final OtTracerPropagator INSTANCE = new OtTracerPropagator();

  private OtTracerPropagator() {
    // singleton
  }

  public static OtTracerPropagator getInstance() {
    return INSTANCE;
  }

  @Override
  public List<String> fields() {
    return FIELDS;
  }

  @Override
  public <C> void inject(Context context, C carrier, Setter<C> setter) {
    if (context == null || setter == null) {
      return;
    }
    final Span span = TracingContextUtils.getSpanWithoutDefault(context);
    if (span == null) {
      return;
    }
    final SpanContext spanContext = span.getContext();
    if (!spanContext.isValid()) {
      return;
    }
    setter.set(carrier, TRACE_ID_HEADER, spanContext.getTraceId().toLowerBase16());
    setter.set(carrier, SPAN_ID_HEADER, spanContext.getSpanId().toLowerBase16());
    setter.set(carrier, SAMPLED_HEADER, String.valueOf(spanContext.getTraceFlags().isSampled()));
  }

  @Override
  public <C> Context extract(Context context, C carrier, Getter<C> getter) {
    if (context == null || getter == null) {
      return context;
    }
    String traceId = getter.get(carrier, TRACE_ID_HEADER);
    String spanId = getter.get(carrier, SPAN_ID_HEADER);
    String sampled = getter.get(carrier, SAMPLED_HEADER);
    SpanContext spanContext = buildSpanContext(traceId, spanId, sampled);
    if (!spanContext.isValid()) {
      return context;
    }
    return TracingContextUtils.withSpan(DefaultSpan.create(spanContext), context);
  }

  static SpanContext buildSpanContext(String traceId, String spanId, String sampled) {
    if (!Common.isTraceIdValid(traceId) || !Common.isSpanIdValid(spanId)) {
      return SpanContext.getInvalid();
    }
    return Common.buildSpanContext(traceId, spanId, sampled);
  }
}
