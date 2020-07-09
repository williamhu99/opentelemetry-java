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

import io.opentelemetry.sdk.trace.Samplers;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;

final class TraceConfigzZPageHandler extends ZPageHandler {
  private static final String TRACE_CONFIGZ_URL = "/traceconfigz";
  private static final String QUERY_STRING_ACTION = "action";
  private static final String QUERY_STRING_ACTION_CHANGE = "change";
  private static final String QUERY_STRING_ACTION_DEFAULT = "default";
  private static final String QUERY_STRING_SAMPLING_PROBABILITY = "samplingprobability";
  private static final String QUERY_STRING_MAX_NUM_OF_ATTRIBUTES = "maxnumofattributes";
  private static final String QUERY_STRING_MAX_NUM_OF_EVENTS = "maxnumbofevents";
  private static final String QUERY_STRING_MAX_NUM_OF_LINKS = "maxnumoflinks";
  private static final String QUERY_STRING_MAX_NUM_OF_ATTRIBUTES_PER_EVENT =
      "maxnumofattributesperevent";
  private static final String QUERY_STRING_MAX_NUM_OF_ATTRIBUTES_PER_LINK =
      "maxnumofattributesperlink";
  // Background color used for zebra striping rows in table
  private static final String ZEBRA_STRIPE_COLOR = "#e6e6e6";
  private final TracerSdkProvider tracerProvider;

  TraceConfigzZPageHandler(TracerSdkProvider tracerProvider) {
    this.tracerProvider = tracerProvider;
  }

  @Override
  public String getUrlPath() {
    return TRACE_CONFIGZ_URL;
  }

  /**
   * Emits CSS styles to the {@link PrintStream} {@code out}. Content emited by this function should
   * be enclosed by <head></head> tag. s
   *
   * @param out the {@link PrintStream} {@code out}.
   */
  private static void emitHtmlStyle(PrintStream out) {
    out.print("<style>");
    out.print(ZPageStyle.style);
    out.print("</style>");
  }

  /**
   * Emits the change tracing parameter table to the {@link PrintStream} {@code out}.
   *
   * @param out the {@link PrintStream} {@code out}.
   */
  private static void emitChangeTable(PrintStream out) {
    out.print("<table style=\"border-spacing: 0; border: 1px solid #363636;\">");
    out.print("<tr class=\"bg-color\">");
    out.print(
        "<th colspan=2 style=\"text-align: left;\" class=\"header-text\">"
            + "<b>Permanently change</b></th>");
    out.print("<th colspan=1 class=\"header-text border-left-white\"><b>Default</b></th>");
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setRowName("SamplingProbability to")
        .setParamName(QUERY_STRING_SAMPLING_PROBABILITY)
        .setParamDefaultValue(TraceConfig.getDefault().getSampler().getDescription())
        .setZebraStripeColor(ZEBRA_STRIPE_COLOR)
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setRowName("MaxNumberOfAttributes to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_ATTRIBUTES)
        .setParamDefaultValue(Integer.toString(TraceConfig.getDefault().getMaxNumberOfAttributes()))
        .setZebraStripeColor(ZEBRA_STRIPE_COLOR)
        .setZebraStripe(true)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setRowName("MaxNumberOfEvents to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_EVENTS)
        .setParamDefaultValue(Integer.toString(TraceConfig.getDefault().getMaxNumberOfEvents()))
        .setZebraStripeColor(ZEBRA_STRIPE_COLOR)
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setRowName("MaxNumberOfLinks to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_LINKS)
        .setParamDefaultValue(Integer.toString(TraceConfig.getDefault().getMaxNumberOfLinks()))
        .setZebraStripeColor(ZEBRA_STRIPE_COLOR)
        .setZebraStripe(true)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setRowName("MaxNumberOfAttributesPerEvent to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_ATTRIBUTES_PER_EVENT)
        .setParamDefaultValue(
            Integer.toString(TraceConfig.getDefault().getMaxNumberOfAttributesPerEvent()))
        .setZebraStripeColor(ZEBRA_STRIPE_COLOR)
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setRowName("MaxNumberOfAttributesPerLink to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_ATTRIBUTES_PER_LINK)
        .setParamDefaultValue(
            Integer.toString(TraceConfig.getDefault().getMaxNumberOfAttributesPerLink()))
        .setZebraStripeColor(ZEBRA_STRIPE_COLOR)
        .setZebraStripe(true)
        .build()
        .emitHtml();
    out.print("</table>");
  }

  /**
   * Emits the active tracing parameters table to the {@link PrintStream} {@code out}.
   *
   * @param out the {@link PrintStream} {@code out}.
   */
  private void emitActiveTable(PrintStream out) {
    out.print("<table style=\"border-spacing: 0; border: 1px solid #363636;\">");
    out.print("<tr class=\"bg-color\">");
    out.print("<th class=\"header-text\"><b>Name</b></th>");
    out.print("<th class=\"header-text border-left-white\"><b>Value</b></th>");
    out.print("</tr>");
    ActiveTableRow.builder()
        .setPrintStream(out)
        .setParamName("Sampler")
        .setParamValue(this.tracerProvider.getActiveTraceConfig().getSampler().getDescription())
        .setZebraStripeColor(ZEBRA_STRIPE_COLOR)
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ActiveTableRow.builder()
        .setPrintStream(out)
        .setParamName("MaxNumOfAttributes")
        .setParamValue(
            Integer.toString(this.tracerProvider.getActiveTraceConfig().getMaxNumberOfAttributes()))
        .setZebraStripeColor(ZEBRA_STRIPE_COLOR)
        .setZebraStripe(true)
        .build()
        .emitHtml();
    ActiveTableRow.builder()
        .setPrintStream(out)
        .setParamName("MaxNumOfEvents")
        .setParamValue(
            Integer.toString(this.tracerProvider.getActiveTraceConfig().getMaxNumberOfEvents()))
        .setZebraStripeColor(ZEBRA_STRIPE_COLOR)
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ActiveTableRow.builder()
        .setPrintStream(out)
        .setParamName("MaxNumOfLinks")
        .setParamValue(
            Integer.toString(this.tracerProvider.getActiveTraceConfig().getMaxNumberOfLinks()))
        .setZebraStripeColor(ZEBRA_STRIPE_COLOR)
        .setZebraStripe(true)
        .build()
        .emitHtml();
    ActiveTableRow.builder()
        .setPrintStream(out)
        .setParamName("MaxNumOfAttributesPerEvent")
        .setParamValue(
            Integer.toString(
                this.tracerProvider.getActiveTraceConfig().getMaxNumberOfAttributesPerEvent()))
        .setZebraStripeColor(ZEBRA_STRIPE_COLOR)
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ActiveTableRow.builder()
        .setPrintStream(out)
        .setParamName("MaxNumOfAttributesPerLink")
        .setParamValue(
            Integer.toString(
                this.tracerProvider.getActiveTraceConfig().getMaxNumberOfAttributesPerLink()))
        .setZebraStripeColor(ZEBRA_STRIPE_COLOR)
        .setZebraStripe(true)
        .build()
        .emitHtml();
    out.print("</table>");
  }

  private void appleActionOnTracer(Map<String, String> queryMap) {
    String action = queryMap.get(QUERY_STRING_ACTION);
    if (action.equals(QUERY_STRING_ACTION_CHANGE)) {
      TraceConfig.Builder newConfigBuilder = this.tracerProvider.getActiveTraceConfig().toBuilder();
      String samplingProbabilityStr = queryMap.get(QUERY_STRING_SAMPLING_PROBABILITY);
      if (!samplingProbabilityStr.isEmpty()) {
        double samplingProbability = Double.parseDouble(samplingProbabilityStr);
        newConfigBuilder.setSampler(Samplers.probability(samplingProbability));
      }
      String maxNumOfAttributesStr = queryMap.get(QUERY_STRING_MAX_NUM_OF_ATTRIBUTES);
      if (!maxNumOfAttributesStr.isEmpty()) {
        int maxNumOfAttributes = Integer.parseInt(maxNumOfAttributesStr);
        newConfigBuilder.setMaxNumberOfAttributes(maxNumOfAttributes);
      }
      String maxNumOfEventsStr = queryMap.get(QUERY_STRING_MAX_NUM_OF_EVENTS);
      if (!maxNumOfEventsStr.isEmpty()) {
        int maxNumOfEvents = Integer.parseInt(maxNumOfEventsStr);
        newConfigBuilder.setMaxNumberOfEvents(maxNumOfEvents);
      }
      String maxNumOfLinksStr = queryMap.get(QUERY_STRING_MAX_NUM_OF_LINKS);
      if (!maxNumOfLinksStr.isEmpty()) {
        int maxNumOfLinks = Integer.parseInt(maxNumOfLinksStr);
        newConfigBuilder.setMaxNumberOfLinks(maxNumOfLinks);
      }
      String maxNumOfAttributesPerEventStr =
          queryMap.get(QUERY_STRING_MAX_NUM_OF_ATTRIBUTES_PER_EVENT);
      if (!maxNumOfAttributesPerEventStr.isEmpty()) {
        int maxNumOfAttributesPerEvent = Integer.parseInt(maxNumOfAttributesPerEventStr);
        newConfigBuilder.setMaxNumberOfAttributesPerEvent(maxNumOfAttributesPerEvent);
      }
      String maxNumOfAttributesPerLinkStr =
          queryMap.get(QUERY_STRING_MAX_NUM_OF_ATTRIBUTES_PER_EVENT);
      if (!maxNumOfAttributesPerLinkStr.isEmpty()) {
        int maxNumOfAttributesPerLink = Integer.parseInt(maxNumOfAttributesPerLinkStr);
        newConfigBuilder.setMaxNumberOfAttributesPerLink(maxNumOfAttributesPerLink);
      }
      this.tracerProvider.updateActiveTraceConfig(newConfigBuilder.build());
    } else if (action.equals(QUERY_STRING_ACTION_DEFAULT)) {
      TraceConfig defaultConfig = TraceConfig.getDefault().toBuilder().build();
      this.tracerProvider.updateActiveTraceConfig(defaultConfig);
    }
  }

  /**
   * Emits HTML body content to the {@link PrintStream} {@code out}. Content emitted by this
   * function should be enclosed by <body></body> tag.
   *
   * @param queryMap the map containing URL query parameters
   * @param out the {@link PrintStream} {@code out}.
   */
  private void emitHtmlBody(Map<String, String> queryMap, PrintStream out)
      throws UnsupportedEncodingException {
    out.print(
        "<img style=\"height: 90px;\" src=\"data:image/png;base64,"
            + ZPageLogo.logoBase64
            + "\" />");
    out.print("<h1>Trace Configuration</h1>");
    out.print("<form class=\"form-flex\" action=\"" + TRACE_CONFIGZ_URL + "\" method=\"get\">");
    out.print("<input type=\"hidden\" name=\"action\" value=\"change\" />");
    emitChangeTable(out);
    // Button for submit
    out.print("<button class=\"button\" type=\"submit\" value=\"Submit\">Submit</button>");
    out.print("</form>");
    // Button for restore default
    out.print("<form class=\"form-flex\" action=\"" + TRACE_CONFIGZ_URL + "\" method=\"get\">");
    out.print("<input type=\"hidden\" name=\"action\" value=\"default\" />");
    out.print("<button class=\"button\" type=\"submit\" value=\"Submit\">Restore Default</button>");
    out.print("</form>");
    out.print("<h2>Active Tracing Parameters</h2>");
    emitActiveTable(out);
    // Apply action based on queryMap
    appleActionOnTracer(queryMap);
  }

  @Override
  public void emitHtml(Map<String, String> queryMap, OutputStream outputStream) {
    // PrintStream for emiting HTML contents
    try (PrintStream out = new PrintStream(outputStream, /* autoFlush= */ false, "UTF-8")) {
      out.print("<!DOCTYPE html>");
      out.print("<html lang=\"en\">");
      out.print("<head>");
      out.print("<meta charset=\"UTF-8\">");
      out.print(
          "<link rel=\"shortcut icon\" href=\"data:image/png;base64,"
              + ZPageLogo.faviconBase64
              + "\" type=\"image/png\">");
      out.print(
          "<link href=\"https://fonts.googleapis.com/css?family=Open+Sans:300\""
              + "rel=\"stylesheet\">");
      out.print(
          "<link href=\"https://fonts.googleapis.com/css?family=Roboto\" rel=\"stylesheet\">");
      out.print("<title>TraceConfigZ</title>");
      emitHtmlStyle(out);
      out.print("</head>");
      out.print("<body>");
      try {
        emitHtmlBody(queryMap, out);
      } catch (Throwable t) {
        out.print("Error while generating HTML: " + t.toString());
      }
      out.print("</body>");
      out.print("</html>");
    } catch (Throwable t) {
      System.err.print("Error while generating HTML: " + t.toString());
    }
  }
}
