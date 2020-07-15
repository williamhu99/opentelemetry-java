package io.opentelemetry.example;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.exporters.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class ZipkinExample {

  // Zipkin Hostname or ip and PORT
  private String ip; // hostname or ip of the zipkin backend server
  private int port; // port of the zipkin backend server

  // Zipkin API Endpoints for uploading spans
  private static final String ENDPOINT_V1_SPANS = "/api/v1/spans";
  private static final String ENDPOINT_V2_SPANS = "/api/v2/spans";

  // Name of the service
  private static final String SERVICE_NAME = "myExampleService";

  private Tracer tracer = OpenTelemetry.getTracer("io.opentelemetry.example.ZipkinExample");
  private ZipkinSpanExporter zipkinExporter;

  public ZipkinExample(String ip, int port) {
    this.ip = ip;
    this.port = port;
  }

  // This method adds SimpleSpanProcessor initialized with ZipkinSpanExporter to the
  // TracerSdkProvider
  public void setupZipkinExporter() {
    String httpUrl = String.format("http://%s:%s", ip, port);
    this.zipkinExporter =
        ZipkinSpanExporter.newBuilder()
            .setEndpoint(httpUrl + ENDPOINT_V2_SPANS)
            .setServiceName(SERVICE_NAME)
            .build();

    // Set to process the spans by the Zipkin Exporter
    OpenTelemetrySdk.getTracerProvider()
        .addSpanProcessor(SimpleSpanProcessor.newBuilder(zipkinExporter).build());
  }

  // This method instruments doWork() method
  public void myWonderfulUseCase() {
    // Generate span
    Span span = tracer.spanBuilder("Start my wonderful use case").startSpan();
    // Add some Event to the span
    span.addEvent("Event 0");
    // execute my use case - here we simulate a wait
    doWork();
    // Add some Event to the span
    span.addEvent("Event 1");
    span.end();
  }

  public void doWork() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
    }
  }

  // graceful shutdown
  public void shutdown() {
    OpenTelemetrySdk.getTracerProvider().shutdown();
  }

  public static void main(String[] args) {
    // Parsing the input
    if (args.length < 2) {
      System.out.println("Missing [hostname] [port]");
      System.exit(1);
    }

    String ip = args[0];
    int port = Integer.parseInt(args[1]);

    // start example
    ZipkinExample example = new ZipkinExample(ip, port);
    example.setupZipkinExporter();
    example.myWonderfulUseCase();

    // shutdown example
    example.shutdown();

    System.out.println("Bye");
  }
}
