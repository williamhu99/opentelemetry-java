package com.mycompany.otelexample;

// import io.opentelemetry.common.Attributes;
import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporters.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.extensions.zpages.ZPageServer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.io.IOException;

public class App
{
  private static final Logger logger =
      Logger.getLogger(App.class.getName());
  private static LoggingSpanExporter loggingExporter = new LoggingSpanExporter();

  public static void main( String[] args )
  {
    try {
      ZPageServer.startHttpServerAndRegisterAllPages(8080);
    }
    catch (IOException e) {
    }
    // Configure the LoggingExporter as our exporter.
    SpanProcessor spanProcessor =
        SimpleSpanProcessor.newBuilder(loggingExporter).build();
    OpenTelemetrySdk.getTracerProvider().addSpanProcessor(spanProcessor);

    // Add a single Span.
    Tracer tracer =
        OpenTelemetrySdk.getTracerProvider().get("otelexample");
    Span span = tracer.spanBuilder("foo bar").startSpan();
    // span.addEvent("operation.request_started", Attributes.of("id", AttributeValue.stringAttributeValue("XYZ")));
    span.addEvent("operation.request_started");
    span.setAttribute("operation.id", 7);
    span.setAttribute("operation.name", "app");

    span = tracer.spanBuilder("foo bar").startSpan();
    span.end();

    // Set it as the current Span.
    try (Scope scope = tracer.withSpan(span)) {

      // Add another Span, as an implicit child.
      Span childSpan = tracer.spanBuilder("bar").startSpan();
      childSpan.addEvent("operation.request_started");
      childSpan.setAttribute("operation.id", 9);

      try (Scope childScope = tracer.withSpan(childSpan)) {
        logger.info("Active Span: " + tracer.getCurrentSpan());
      } finally {
        childSpan.end();
      }
    }

    span = tracer.spanBuilder("foo").startSpan();
    span.setAttribute("operation.id", 7);
    span.addEvent("operation.request_started");

    // Set it as the current Span.
    try (Scope scope = tracer.withSpan(span)) {

      // Add another Span, as an implicit child.
      Span childSpan = tracer.spanBuilder("bar").startSpan();
      childSpan.setAttribute("operation.id", 9);
      childSpan.addEvent("operation.request_started");

      try (Scope childScope = tracer.withSpan(childSpan)) {
        logger.info("Active Span: " + tracer.getCurrentSpan());
      } finally {
        childSpan.end();
      }
    }

    span = tracer.spanBuilder("foo+bar").startSpan();
    // span.addEvent("operation.request_started", Attributes.of("id", AttributeValue.stringAttributeValue("XYZ")));
    span.addEvent("operation.request_started");
    span.setAttribute("operation.id", 7);
    span.setAttribute("operation.name", "app");

    span = tracer.spanBuilder("foo+bar").startSpan();
    span.end();

    span = tracer.spanBuilder("{foo/bar}").startSpan();
    // span.addEvent("operation.request_started", Attributes.of("id", AttributeValue.stringAttributeValue("XYZ")));
    span.addEvent("operation.request_started");
    span.setAttribute("operation.id", 7);
    span.setAttribute("operation.name", "app");

    span = tracer.spanBuilder("{foo/bar}").startSpan();
    span.end();

    spanProcessor.shutdown();
  }
}
