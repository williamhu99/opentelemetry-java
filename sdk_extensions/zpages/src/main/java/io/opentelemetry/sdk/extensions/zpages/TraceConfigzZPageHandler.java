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

import io.opentelemetry.sdk.trace.config.TraceConfig;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Formatter;
import java.util.Locale;
import java.util.Map;

final class TraceConfigzZPageHandler extends ZPageHandler {
  private static final String TRACE_CONFIGZ_URL = "/traceconfigz";
  private static final String QUERY_STRING_SAMPLING_PROBABILITY = "samplingprobability";
  private static final String QUERY_STRING_MAX_NUM_OF_ATTRIBUTES = "maxnumofattributes";
  private static final String QUERY_STRING_MAX_NUM_OF_EVENTS = "maxnumbofevents";
  private static final String QUERY_STRING_MAX_NUM_OF_LINKS = "maxnumoflinks";
  private static final String QUERY_STRING_MAX_NUM_OF_ATTRIBUTES_PER_EVENT =
      "maxnumofattributesperevent";
  private static final String QUERY_STRING_MAX_NUM_OF_ATTRIBUTES_PER_LINK =
      "maxnumofattributesperlink";

  TraceConfigzZPageHandler() {}

  @Override
  public String getUrlPath() {
    return TRACE_CONFIGZ_URL;
  }

  /**
   * Emits CSS styles to the {@link PrintStream} {@code out}. Content emited by this function should
   * be enclosed by <head></head> tag.
   *
   * @param out the {@link PrintStream} {@code out}.
   */
  private static void emitHtmlStyle(PrintStream out) {
    out.print("<style>");
    out.print(ZPageStyle.style);
    out.print("</style>");
  }

  private static void emitChangeTableRow(
      PrintStream out, Formatter formatter, String rowName, String inputName, String defaultValue) {
    out.print("<tr>");
    formatter.format("<td>%s</td>", rowName);
    formatter.format("<td><input type=text size=15 name=%s value=\"\" /></td>", inputName);
    formatter.format("<td>(%s)</td>", defaultValue);
    out.print("</tr>");
  }

  private static void emitChangeTable(PrintStream out, Formatter formatter) {
    out.print("<table style=\"border-spacing: 0; border: 1px solid #363636;\">");
    out.print("<tr class=\"bg-color\">");
    out.print("<th colspan=3 class=\"header-text\"><b>Permanently change</b></th>");
    emitChangeTableRow(
        out,
        formatter,
        "SamplingProbability to",
        QUERY_STRING_SAMPLING_PROBABILITY,
        "defaultValue");
    emitChangeTableRow(
        out,
        formatter,
        "MaxNumberOfAttributes to",
        QUERY_STRING_MAX_NUM_OF_ATTRIBUTES,
        Integer.toString(TraceConfig.getDefault().getMaxNumberOfAttributes()));
    emitChangeTableRow(
        out,
        formatter,
        "MaxNumberOfEvents to",
        QUERY_STRING_MAX_NUM_OF_EVENTS,
        Integer.toString(TraceConfig.getDefault().getMaxNumberOfEvents()));
    emitChangeTableRow(
        out,
        formatter,
        "MaxNumberOfLinks to",
        QUERY_STRING_MAX_NUM_OF_LINKS,
        Integer.toString(TraceConfig.getDefault().getMaxNumberOfLinks()));
    emitChangeTableRow(
        out,
        formatter,
        "MaxNumberOfAttributesPerEvent to",
        QUERY_STRING_MAX_NUM_OF_ATTRIBUTES_PER_EVENT,
        Integer.toString(TraceConfig.getDefault().getMaxNumberOfAttributesPerEvent()));
    emitChangeTableRow(
        out,
        formatter,
        "MaxNumberOfAttributesPerLink to",
        QUERY_STRING_MAX_NUM_OF_ATTRIBUTES_PER_LINK,
        Integer.toString(TraceConfig.getDefault().getMaxNumberOfAttributesPerLink()));
  }

  private static void emitActiveTable(PrintStream out, Formatter formatter) {
    out.print("");
    formatter.format("");
  }

  /**
   * Emits HTML body content to the {@link PrintStream} {@code out}. Content emitted by this
   * function should be enclosed by <body></body> tag.
   *
   * @param queryMap the map containing URL query parameters
   * @param out the {@link PrintStream} {@code out}.
   */
  private static void emitHtmlBody(Map<String, String> queryMap, PrintStream out)
      throws UnsupportedEncodingException {
    out.print(
        "<img style=\"height: 90px;\" src=\"data:image/png;base64,"
            + ZPageLogo.logoBase64
            + "\" />");
    out.print("<h1>Trace Configuration</h1>");
    Formatter formatter = new Formatter(out, Locale.US);
    emitChangeTable(out, formatter);
    // Button for submit
    // Button for restore default
    emitActiveTable(out, formatter);
    // deal with query map
    queryMap.toString();
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
