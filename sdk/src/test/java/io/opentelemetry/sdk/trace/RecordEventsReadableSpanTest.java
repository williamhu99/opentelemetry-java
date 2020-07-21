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

package io.opentelemetry.sdk.trace;

import static com.google.common.truth.Truth.assertThat;

import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.common.ReadableAttributes;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.TestClock;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.sdk.trace.data.EventImpl;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.SpanData.Event;
import io.opentelemetry.sdk.trace.data.SpanData.Link;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.SpanId;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.TraceFlags;
import io.opentelemetry.trace.TraceId;
import io.opentelemetry.trace.TraceState;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link RecordEventsReadableSpan}. */
@RunWith(JUnit4.class)
public class RecordEventsReadableSpanTest {
  private static final String SPAN_NAME = "MySpanName";
  private static final String SPAN_NEW_NAME = "NewName";
  private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);
  private static final long MILLIS_PER_SECOND = TimeUnit.SECONDS.toMillis(1);
  private static final boolean EXPECTED_HAS_REMOTE_PARENT = true;
  private static final long START_EPOCH_NANOS = 1000_123_789_654L;

  private final IdsGenerator idsGenerator = new RandomIdsGenerator();
  private final TraceId traceId = idsGenerator.generateTraceId();
  private final SpanId spanId = idsGenerator.generateSpanId();
  private final SpanId parentSpanId = idsGenerator.generateSpanId();
  private final SpanContext spanContext =
      SpanContext.create(traceId, spanId, TraceFlags.getDefault(), TraceState.getDefault());
  private final Resource resource = Resource.getEmpty();
  private final InstrumentationLibraryInfo instrumentationLibraryInfo =
      InstrumentationLibraryInfo.create("theName", null);
  private final Map<String, AttributeValue> attributes = new HashMap<>();
  private Attributes expectedAttributes;
  private final io.opentelemetry.trace.Link link = Link.create(spanContext);
  @Mock private SpanProcessor spanProcessor;
  @Rule public final ExpectedException thrown = ExpectedException.none();

  private TestClock testClock;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    attributes.put(
        "MyStringAttributeKey", AttributeValue.stringAttributeValue("MyStringAttributeValue"));
    attributes.put("MyLongAttributeKey", AttributeValue.longAttributeValue(123L));
    attributes.put("MyBooleanAttributeKey", AttributeValue.booleanAttributeValue(false));
    Attributes.Builder builder =
        Attributes.newBuilder()
            .setAttribute(
                "MySingleStringAttributeKey",
                AttributeValue.stringAttributeValue("MySingleStringAttributeValue"));
    for (Map.Entry<String, AttributeValue> entry : attributes.entrySet()) {
      builder.setAttribute(entry.getKey(), entry.getValue());
    }
    expectedAttributes = builder.build();
    testClock = TestClock.create(START_EPOCH_NANOS);
  }

  @Test
  public void nothingChangedAfterEnd() {
    RecordEventsReadableSpan span = createTestSpan(Kind.INTERNAL);
    span.end();
    // Check that adding trace events or update fields after Span#end() does not throw any thrown
    // and are ignored.
    spanDoWork(span, Status.CANCELLED);
    SpanData spanData = span.toSpanData();
    verifySpanData(
        spanData,
        Attributes.empty(),
        Collections.emptyList(),
        Collections.singletonList(link),
        SPAN_NAME,
        START_EPOCH_NANOS,
        START_EPOCH_NANOS,
        Status.OK,
        /*hasEnded=*/ true);
  }

  @Test
  public void lazyLinksAreResolved() {
    final Attributes attributes = Attributes.of("attr", AttributeValue.stringAttributeValue("val"));
    io.opentelemetry.trace.Link link =
        new io.opentelemetry.trace.Link() {
          @Override
          public SpanContext getContext() {
            return spanContext;
          }

          @Override
          public Attributes getAttributes() {
            return attributes;
          }
        };
    RecordEventsReadableSpan span =
        createTestSpan(
            Kind.CLIENT,
            TraceConfig.getDefault(),
            parentSpanId,
            null,
            Collections.singletonList(link));

    Link resultingLink = span.toSpanData().getLinks().get(0);
    assertThat(resultingLink.getTotalAttributeCount()).isEqualTo(1);
    assertThat(resultingLink.getContext()).isSameInstanceAs(spanContext);
    assertThat(resultingLink.getAttributes()).isEqualTo(attributes);
  }

  @Test
  public void endSpanTwice_DoNotCrash() {
    RecordEventsReadableSpan span = createTestSpan(Kind.INTERNAL);
    assertThat(span.hasEnded()).isFalse();
    span.end();
    assertThat(span.hasEnded()).isTrue();
    span.end();
    assertThat(span.hasEnded()).isTrue();
  }

  @Test
  public void toSpanData_ActiveSpan() {
    RecordEventsReadableSpan span = createTestSpan(Kind.INTERNAL);
    try {
      assertThat(span.hasEnded()).isFalse();
      spanDoWork(span, null);
      SpanData spanData = span.toSpanData();
      Event event =
          TimedEvent.create(START_EPOCH_NANOS + NANOS_PER_SECOND, "event2", Attributes.empty(), 0);
      verifySpanData(
          spanData,
          expectedAttributes,
          Collections.singletonList(event),
          Collections.singletonList(link),
          SPAN_NEW_NAME,
          START_EPOCH_NANOS,
          0,
          Status.OK,
          /*hasEnded=*/ false);
      assertThat(span.hasEnded()).isFalse();
    } finally {
      span.end();
    }
    assertThat(span.hasEnded()).isTrue();
  }

  @Test
  public void toSpanData_EndedSpan() {
    RecordEventsReadableSpan span = createTestSpan(Kind.INTERNAL);
    try {
      spanDoWork(span, Status.CANCELLED);
    } finally {
      span.end();
    }
    Mockito.verify(spanProcessor, Mockito.times(1)).onEnd(span);
    SpanData spanData = span.toSpanData();
    Event event =
        TimedEvent.create(START_EPOCH_NANOS + NANOS_PER_SECOND, "event2", Attributes.empty(), 0);
    verifySpanData(
        spanData,
        expectedAttributes,
        Collections.singletonList(event),
        Collections.singletonList(link),
        SPAN_NEW_NAME,
        START_EPOCH_NANOS,
        testClock.now(),
        Status.CANCELLED,
        /*hasEnded=*/ true);
  }

  @Test
  public void toSpanData_immutableLinks() {
    RecordEventsReadableSpan span = createTestSpan(Kind.INTERNAL);
    SpanData spanData = span.toSpanData();

    thrown.expect(UnsupportedOperationException.class);
    spanData.getLinks().add(Link.create(SpanContext.getInvalid()));
  }

  @Test
  public void toSpanData_immutableEvents() {
    RecordEventsReadableSpan span = createTestSpan(Kind.INTERNAL);
    SpanData spanData = span.toSpanData();

    thrown.expect(UnsupportedOperationException.class);
    spanData.getEvents().add(EventImpl.create(1000, "test", Attributes.empty()));
  }

  @Test
  public void toSpanData_RootSpan() {
    RecordEventsReadableSpan span = createTestRootSpan();
    try {
      spanDoWork(span, null);
    } finally {
      span.end();
    }
    SpanData spanData = span.toSpanData();
    assertThat(spanData.getParentSpanId().isValid()).isFalse();
  }

  @Test
  public void toSpanData_WithInitialAttributes() {
    RecordEventsReadableSpan span = createTestSpanWithAttributes(attributes);
    span.setAttribute("anotherKey", "anotherValue");
    span.end();
    SpanData spanData = span.toSpanData();
    assertThat(spanData.getAttributes().size()).isEqualTo(attributes.size() + 1);
    assertThat(spanData.getTotalAttributeCount()).isEqualTo(attributes.size() + 1);
  }

  @Test
  public void setStatus() {
    RecordEventsReadableSpan span = createTestSpan(Kind.CONSUMER);
    try {
      testClock.advanceMillis(MILLIS_PER_SECOND);
      assertThat(span.toSpanData().getStatus()).isEqualTo(Status.OK);
      span.setStatus(Status.CANCELLED);
      assertThat(span.toSpanData().getStatus()).isEqualTo(Status.CANCELLED);
    } finally {
      span.end();
    }
    assertThat(span.toSpanData().getStatus()).isEqualTo(Status.CANCELLED);
  }

  @Test
  public void getSpanKind() {
    RecordEventsReadableSpan span = createTestSpan(Kind.SERVER);
    try {
      assertThat(span.toSpanData().getKind()).isEqualTo(Kind.SERVER);
    } finally {
      span.end();
    }
  }

  @Test
  public void getInstrumentationLibraryInfo() {
    RecordEventsReadableSpan span = createTestSpan(Kind.CLIENT);
    try {
      assertThat(span.getInstrumentationLibraryInfo()).isEqualTo(instrumentationLibraryInfo);
    } finally {
      span.end();
    }
  }

  @Test
  public void getSpanHasRemoteParent() {
    RecordEventsReadableSpan span = createTestSpan(Kind.SERVER);
    try {
      assertThat(span.toSpanData().getHasRemoteParent()).isTrue();
    } finally {
      span.end();
    }
  }

  @Test
  public void getAndUpdateSpanName() {
    RecordEventsReadableSpan span = createTestRootSpan();
    try {
      assertThat(span.getName()).isEqualTo(SPAN_NAME);
      span.updateName(SPAN_NEW_NAME);
      assertThat(span.getName()).isEqualTo(SPAN_NEW_NAME);
    } finally {
      span.end();
    }
  }

  @Test
  public void getLatencyNs_ActiveSpan() {
    RecordEventsReadableSpan span = createTestSpan(Kind.INTERNAL);
    try {
      testClock.advanceMillis(MILLIS_PER_SECOND);
      long elapsedTimeNanos1 = testClock.now() - START_EPOCH_NANOS;
      assertThat(span.getLatencyNanos()).isEqualTo(elapsedTimeNanos1);
      testClock.advanceMillis(MILLIS_PER_SECOND);
      long elapsedTimeNanos2 = testClock.now() - START_EPOCH_NANOS;
      assertThat(span.getLatencyNanos()).isEqualTo(elapsedTimeNanos2);
    } finally {
      span.end();
    }
  }

  @Test
  public void getLatencyNs_EndedSpan() {
    RecordEventsReadableSpan span = createTestSpan(Kind.INTERNAL);
    testClock.advanceMillis(MILLIS_PER_SECOND);
    span.end();
    long elapsedTimeNanos = testClock.now() - START_EPOCH_NANOS;
    assertThat(span.getLatencyNanos()).isEqualTo(elapsedTimeNanos);
    testClock.advanceMillis(MILLIS_PER_SECOND);
    assertThat(span.getLatencyNanos()).isEqualTo(elapsedTimeNanos);
  }

  @Test
  public void setAttribute() {
    RecordEventsReadableSpan span = createTestRootSpan();
    try {
      span.setAttribute("StringKey", "StringVal");
      span.setAttribute("NullStringKey", (String) null);
      span.setAttribute("EmptyStringKey", "");
      span.setAttribute("NullStringAttributeValue", AttributeValue.stringAttributeValue(null));
      span.setAttribute("EmptyStringAttributeValue", AttributeValue.stringAttributeValue(""));
      span.setAttribute("LongKey", 1000L);
      span.setAttribute("DoubleKey", 10.0);
      span.setAttribute("BooleanKey", false);
      span.setAttribute(
          "ArrayStringKey",
          AttributeValue.arrayAttributeValue("StringVal", null, "", "StringVal2"));
      span.setAttribute("ArrayLongKey", AttributeValue.arrayAttributeValue(1L, 2L, 3L, 4L, 5L));
      span.setAttribute(
          "ArrayDoubleKey", AttributeValue.arrayAttributeValue(0.1, 2.3, 4.5, 6.7, 8.9));
      span.setAttribute(
          "ArrayBooleanKey", AttributeValue.arrayAttributeValue(true, false, false, true));
      span.setAttribute("NullArrayStringKey", AttributeValue.arrayAttributeValue((String[]) null));
      span.setAttribute("NullArrayLongKey", AttributeValue.arrayAttributeValue((Long[]) null));
      span.setAttribute("NullArrayDoubleKey", AttributeValue.arrayAttributeValue((Double[]) null));
      span.setAttribute(
          "NullArrayBooleanKey", AttributeValue.arrayAttributeValue((Boolean[]) null));
    } finally {
      span.end();
    }
    SpanData spanData = span.toSpanData();
    assertThat(spanData.getAttributes().size()).isEqualTo(14);
    assertThat(spanData.getAttributes().get("ArrayStringKey").getStringArrayValue().size())
        .isEqualTo(4);
    assertThat(spanData.getAttributes().get("ArrayLongKey").getLongArrayValue().size())
        .isEqualTo(5);
    assertThat(spanData.getAttributes().get("ArrayDoubleKey").getDoubleArrayValue().size())
        .isEqualTo(5);
    assertThat(spanData.getAttributes().get("ArrayBooleanKey").getBooleanArrayValue().size())
        .isEqualTo(4);
  }

  @Test
  public void setAttribute_emptyArrayAttributeValue() {
    RecordEventsReadableSpan span = createTestRootSpan();
    span.setAttribute("stringArrayAttribute", AttributeValue.arrayAttributeValue(new String[0]));
    span.setAttribute("boolArrayAttribute", AttributeValue.arrayAttributeValue(new Boolean[0]));
    span.setAttribute("longArrayAttribute", AttributeValue.arrayAttributeValue(new Long[0]));
    span.setAttribute("doubleArrayAttribute", AttributeValue.arrayAttributeValue(new Double[0]));
    assertThat(span.toSpanData().getAttributes().size()).isEqualTo(4);
  }

  @Test
  public void setAttribute_nullStringValue() {
    RecordEventsReadableSpan span = createTestRootSpan();
    span.setAttribute("nullString", (String) null);
    span.setAttribute("emptyString", "");
    span.setAttribute("nullStringAttributeValue", AttributeValue.stringAttributeValue(null));
    span.setAttribute("emptyStringAttributeValue", AttributeValue.stringAttributeValue(""));
    assertThat(span.toSpanData().getAttributes().size()).isEqualTo(2);
    span.setAttribute("emptyString", (String) null);
    span.setAttribute("emptyStringAttributeValue", (String) null);
    assertThat(span.toSpanData().getAttributes().isEmpty()).isTrue();
  }

  @Test
  public void setAttribute_nullAttributeValue() {
    RecordEventsReadableSpan span = createTestRootSpan();
    span.setAttribute("emptyString", "");
    span.setAttribute("nullString", (AttributeValue) null);
    span.setAttribute("nullStringAttributeValue", AttributeValue.stringAttributeValue(null));
    span.setAttribute("emptyStringAttributeValue", AttributeValue.stringAttributeValue(""));
    span.setAttribute("longAttribute", 0L);
    span.setAttribute("boolAttribute", false);
    span.setAttribute("doubleAttribute", 0.12345f);
    span.setAttribute("stringArrayAttribute", AttributeValue.arrayAttributeValue("", null));
    span.setAttribute("boolArrayAttribute", AttributeValue.arrayAttributeValue(true, null));
    span.setAttribute("longArrayAttribute", AttributeValue.arrayAttributeValue(12345L, null));
    span.setAttribute("doubleArrayAttribute", AttributeValue.arrayAttributeValue(1.2345, null));
    assertThat(span.toSpanData().getAttributes().size()).isEqualTo(9);
    span.setAttribute("emptyString", (AttributeValue) null);
    span.setAttribute("emptyStringAttributeValue", (AttributeValue) null);
    span.setAttribute("longAttribute", (AttributeValue) null);
    span.setAttribute("boolAttribute", (AttributeValue) null);
    span.setAttribute("doubleAttribute", (AttributeValue) null);
    span.setAttribute("stringArrayAttribute", (AttributeValue) null);
    span.setAttribute("boolArrayAttribute", (AttributeValue) null);
    span.setAttribute("longArrayAttribute", (AttributeValue) null);
    span.setAttribute("doubleArrayAttribute", (AttributeValue) null);
    assertThat(span.toSpanData().getAttributes().isEmpty()).isTrue();
  }

  @Test
  public void addEvent() {
    RecordEventsReadableSpan span = createTestRootSpan();
    io.opentelemetry.trace.Event customEvent =
        new io.opentelemetry.trace.Event() {
          @Override
          public String getName() {
            return "event3";
          }

          @Override
          public Attributes getAttributes() {
            return Attributes.empty();
          }
        };
    try {
      span.addEvent("event1");
      span.addEvent(
          "event2", Attributes.of("e1key", AttributeValue.stringAttributeValue("e1Value")));
      span.addEvent(customEvent);
    } finally {
      span.end();
    }
    List<Event> events = span.toSpanData().getEvents();
    assertThat(events.size()).isEqualTo(3);
    for (Event event : events) {
      // make sure that we aren't holding on to the memory from the custom event, in case it
      // references
      // some heavyweight thing.
      assertThat(event).isNotInstanceOf(TimedEvent.RawTimedEventWithEvent.class);
    }
  }

  @Test
  public void droppingAttributes() {
    final int maxNumberOfAttributes = 8;
    TraceConfig traceConfig =
        TraceConfig.getDefault()
            .toBuilder()
            .setMaxNumberOfAttributes(maxNumberOfAttributes)
            .build();
    RecordEventsReadableSpan span = createTestSpan(traceConfig);
    try {
      for (int i = 0; i < 2 * maxNumberOfAttributes; i++) {
        span.setAttribute("MyStringAttributeKey" + i, AttributeValue.longAttributeValue(i));
      }
      SpanData spanData = span.toSpanData();
      assertThat(spanData.getAttributes().size()).isEqualTo(maxNumberOfAttributes);
      assertThat(spanData.getTotalAttributeCount()).isEqualTo(2 * maxNumberOfAttributes);
    } finally {
      span.end();
    }
    SpanData spanData = span.toSpanData();
    assertThat(spanData.getAttributes().size()).isEqualTo(maxNumberOfAttributes);
    assertThat(spanData.getTotalAttributeCount()).isEqualTo(2 * maxNumberOfAttributes);
  }

  @Test
  public void droppingAndAddingAttributes() {
    final int maxNumberOfAttributes = 8;
    TraceConfig traceConfig =
        TraceConfig.getDefault()
            .toBuilder()
            .setMaxNumberOfAttributes(maxNumberOfAttributes)
            .build();
    RecordEventsReadableSpan span = createTestSpan(traceConfig);
    try {
      for (int i = 0; i < 2 * maxNumberOfAttributes; i++) {
        span.setAttribute("MyStringAttributeKey" + i, AttributeValue.longAttributeValue(i));
      }
      SpanData spanData = span.toSpanData();
      assertThat(spanData.getAttributes().size()).isEqualTo(maxNumberOfAttributes);
      assertThat(spanData.getTotalAttributeCount()).isEqualTo(2 * maxNumberOfAttributes);

      for (int i = 0; i < maxNumberOfAttributes / 2; i++) {
        int val = i + maxNumberOfAttributes * 3 / 2;
        span.setAttribute("MyStringAttributeKey" + i, AttributeValue.longAttributeValue(val));
      }
      spanData = span.toSpanData();
      assertThat(spanData.getAttributes().size()).isEqualTo(maxNumberOfAttributes);
      // Test that we still have in the attributes map the latest maxNumberOfAttributes / 2 entries.
      for (int i = 0; i < maxNumberOfAttributes / 2; i++) {
        int val = i + maxNumberOfAttributes * 3 / 2;
        AttributeValue expectedValue = AttributeValue.longAttributeValue(val);
        assertThat(spanData.getAttributes().get("MyStringAttributeKey" + i))
            .isEqualTo(expectedValue);
      }
      // Test that we have the newest re-added initial entries.
      for (int i = maxNumberOfAttributes / 2; i < maxNumberOfAttributes; i++) {
        AttributeValue expectedValue = AttributeValue.longAttributeValue(i);
        assertThat(spanData.getAttributes().get("MyStringAttributeKey" + i))
            .isEqualTo(expectedValue);
      }
    } finally {
      span.end();
    }
  }

  @Test
  public void droppingEvents() {
    final int maxNumberOfEvents = 8;
    TraceConfig traceConfig =
        TraceConfig.getDefault().toBuilder().setMaxNumberOfEvents(maxNumberOfEvents).build();
    RecordEventsReadableSpan span = createTestSpan(traceConfig);
    try {
      for (int i = 0; i < 2 * maxNumberOfEvents; i++) {
        span.addEvent("event2", Attributes.empty());
        testClock.advanceMillis(MILLIS_PER_SECOND);
      }
      SpanData spanData = span.toSpanData();

      assertThat(spanData.getEvents().size()).isEqualTo(maxNumberOfEvents);
      for (int i = 0; i < maxNumberOfEvents; i++) {
        Event expectedEvent =
            TimedEvent.create(
                START_EPOCH_NANOS + (maxNumberOfEvents + i) * NANOS_PER_SECOND,
                "event2",
                Attributes.empty(),
                0);
        assertThat(spanData.getEvents().get(i)).isEqualTo(expectedEvent);
        assertThat(spanData.getTotalRecordedEvents()).isEqualTo(2 * maxNumberOfEvents);
      }
    } finally {
      span.end();
    }
    SpanData spanData = span.toSpanData();
    assertThat(spanData.getEvents().size()).isEqualTo(maxNumberOfEvents);
    for (int i = 0; i < maxNumberOfEvents; i++) {
      Event expectedEvent =
          TimedEvent.create(
              START_EPOCH_NANOS + (maxNumberOfEvents + i) * NANOS_PER_SECOND,
              "event2",
              Attributes.empty(),
              0);
      assertThat(spanData.getEvents().get(i)).isEqualTo(expectedEvent);
    }
  }

  @Test
  public void badArgsIgnored() {
    RecordEventsReadableSpan span = createTestRootSpan();

    // Should be no exceptions
    span.setAttribute(null, 0);
    span.setStatus(null);
    span.updateName(null);
    span.addEvent((Event) null);
    span.addEvent((String) null);
    span.addEvent((Event) null, 0);
    span.addEvent((String) null, 0);
    span.addEvent(null, null);
    span.addEvent(null, null, 0);
    span.end(null);

    // Ignored the bad calls
    SpanData data = span.toSpanData();
    assertThat(data.getAttributes().isEmpty()).isTrue();
    assertThat(data.getStatus()).isEqualTo(Status.OK);
    assertThat(data.getName()).isEqualTo(SPAN_NAME);
  }

  private RecordEventsReadableSpan createTestSpanWithAttributes(
      Map<String, AttributeValue> attributes) {
    AttributesMap attributesMap =
        new AttributesMap(TraceConfig.getDefault().getMaxNumberOfAttributes());
    attributesMap.putAll(attributes);
    return createTestSpan(
        Kind.INTERNAL,
        TraceConfig.getDefault(),
        null,
        attributesMap,
        Collections.singletonList(link));
  }

  private RecordEventsReadableSpan createTestRootSpan() {
    return createTestSpan(
        Kind.INTERNAL, TraceConfig.getDefault(), null, null, Collections.singletonList(link));
  }

  private RecordEventsReadableSpan createTestSpan(Kind kind) {
    return createTestSpan(
        kind, TraceConfig.getDefault(), parentSpanId, null, Collections.singletonList(link));
  }

  private RecordEventsReadableSpan createTestSpan(TraceConfig config) {
    return createTestSpan(
        Kind.INTERNAL, config, parentSpanId, null, Collections.singletonList(link));
  }

  private RecordEventsReadableSpan createTestSpan(
      Kind kind,
      TraceConfig config,
      @Nullable SpanId parentSpanId,
      @Nullable AttributesMap attributes,
      List<io.opentelemetry.trace.Link> links) {

    RecordEventsReadableSpan span =
        RecordEventsReadableSpan.startSpan(
            spanContext,
            SPAN_NAME,
            instrumentationLibraryInfo,
            kind,
            parentSpanId,
            /* hasRemoteParent= */ true,
            config,
            spanProcessor,
            testClock,
            resource,
            attributes,
            links,
            1,
            0);
    Mockito.verify(spanProcessor, Mockito.times(1)).onStart(span);
    return span;
  }

  private void spanDoWork(RecordEventsReadableSpan span, @Nullable Status status) {
    span.setAttribute(
        "MySingleStringAttributeKey",
        AttributeValue.stringAttributeValue("MySingleStringAttributeValue"));
    for (Map.Entry<String, AttributeValue> attribute : attributes.entrySet()) {
      span.setAttribute(attribute.getKey(), attribute.getValue());
    }
    testClock.advanceMillis(MILLIS_PER_SECOND);
    span.addEvent("event2", Attributes.empty());
    testClock.advanceMillis(MILLIS_PER_SECOND);
    span.updateName(SPAN_NEW_NAME);
    if (status != null) {
      span.setStatus(status);
    }
  }

  private void verifySpanData(
      SpanData spanData,
      final ReadableAttributes attributes,
      List<Event> eventData,
      List<io.opentelemetry.trace.Link> links,
      String spanName,
      long startEpochNanos,
      long endEpochNanos,
      Status status,
      boolean hasEnded) {
    assertThat(spanData.getTraceId()).isEqualTo(traceId);
    assertThat(spanData.getSpanId()).isEqualTo(spanId);
    assertThat(spanData.getParentSpanId()).isEqualTo(parentSpanId);
    assertThat(spanData.getHasRemoteParent()).isEqualTo(EXPECTED_HAS_REMOTE_PARENT);
    assertThat(spanData.getTraceState()).isEqualTo(TraceState.getDefault());
    assertThat(spanData.getResource()).isEqualTo(resource);
    assertThat(spanData.getInstrumentationLibraryInfo()).isEqualTo(instrumentationLibraryInfo);
    assertThat(spanData.getName()).isEqualTo(spanName);
    assertThat(spanData.getEvents()).isEqualTo(eventData);
    assertThat(spanData.getLinks()).isEqualTo(links);
    assertThat(spanData.getStartEpochNanos()).isEqualTo(startEpochNanos);
    assertThat(spanData.getEndEpochNanos()).isEqualTo(endEpochNanos);
    assertThat(spanData.getStatus().getCanonicalCode()).isEqualTo(status.getCanonicalCode());
    assertThat(spanData.getHasEnded()).isEqualTo(hasEnded);

    // verify equality manually, since the implementations don't all equals with each other.
    ReadableAttributes spanDataAttributes = spanData.getAttributes();
    assertThat(spanDataAttributes.size()).isEqualTo(attributes.size());
    spanDataAttributes.forEach((key, value) -> assertThat(attributes.get(key)).isEqualTo(value));
  }

  @Test
  public void testAsSpanData() {
    String name = "GreatSpan";
    Kind kind = Kind.SERVER;
    TraceId traceId = this.traceId;
    SpanId spanId = this.spanId;
    SpanId parentSpanId = this.parentSpanId;
    TraceConfig traceConfig = TraceConfig.getDefault();
    SpanProcessor spanProcessor = NoopSpanProcessor.getInstance();
    TestClock clock = TestClock.create();
    Resource resource = this.resource;
    Attributes attributes = TestUtils.generateRandomAttributes();
    final AttributesMap attributesWithCapacity = new AttributesMap(32);
    attributes.forEach(attributesWithCapacity::put);
    Attributes event1Attributes = TestUtils.generateRandomAttributes();
    Attributes event2Attributes = TestUtils.generateRandomAttributes();
    SpanContext context =
        SpanContext.create(traceId, spanId, TraceFlags.getDefault(), TraceState.getDefault());
    Link link1 = Link.create(context, TestUtils.generateRandomAttributes());

    RecordEventsReadableSpan readableSpan =
        RecordEventsReadableSpan.startSpan(
            context,
            name,
            instrumentationLibraryInfo,
            kind,
            parentSpanId,
            /* hasRemoteParent= */ EXPECTED_HAS_REMOTE_PARENT,
            traceConfig,
            spanProcessor,
            clock,
            resource,
            attributesWithCapacity,
            Collections.singletonList(link1),
            1,
            0);
    long startEpochNanos = clock.now();
    clock.advanceMillis(4);
    long firstEventEpochNanos = clock.now();
    readableSpan.addEvent("event1", event1Attributes);
    clock.advanceMillis(6);
    long secondEventTimeNanos = clock.now();
    readableSpan.addEvent("event2", event2Attributes);

    clock.advanceMillis(100);
    readableSpan.end();
    long endEpochNanos = clock.now();

    List<Event> events =
        Arrays.asList(
            TimedEvent.create(
                firstEventEpochNanos, "event1", event1Attributes, event1Attributes.size()),
            TimedEvent.create(
                secondEventTimeNanos, "event2", event2Attributes, event2Attributes.size()));

    SpanData result = readableSpan.toSpanData();
    verifySpanData(
        result,
        attributesWithCapacity,
        events,
        Collections.singletonList(link1),
        name,
        startEpochNanos,
        endEpochNanos,
        Status.OK,
        /* hasEnded= */ true);
    assertThat(result.getTotalRecordedLinks()).isEqualTo(1);
    assertThat(result.getTraceFlags()).isEqualTo(TraceFlags.getDefault());
  }

  @Test
  public void testConcurrentModification() throws ExecutionException, InterruptedException {
    final RecordEventsReadableSpan span = createTestSpan(Kind.INTERNAL);
    ExecutorService es = Executors.newSingleThreadExecutor();
    Future<?> modifierFuture =
        es.submit(
            () -> {
              for (int i = 0; i < 5096 * 5; ++i) {
                span.setAttribute("hey" + i, "");
              }
            });
    try {
      for (int i = 0; i < 5096 * 5; ++i) {
        span.toSpanData();
      }
    } catch (Throwable t) {
      modifierFuture.cancel(true);
      throw t;
    }
    modifierFuture.get();
  }
}
