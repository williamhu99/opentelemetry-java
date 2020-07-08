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
  // Background color used for zebra striping rows in table
  private static final String ZEBRA_STRIPE_COLOR = "#e6e6e6";

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

  /** Builder pattern class for emiting a single row of the change parameter table. */
  private static final class ChangeTableRow {
    private final PrintStream out;
    private final Formatter formatter;
    private final String rowName;
    private final String paramName;
    private final String defaultValue;
    private final boolean zebraStripe;

    private ChangeTableRow(Builder builder) {
      out = builder.out;
      formatter = builder.formatter;
      rowName = builder.rowName;
      paramName = builder.paramName;
      defaultValue = builder.defaultValue;
      zebraStripe = builder.zebraStripe;
    }

    public static class Builder {
      public ChangeTableRow build() {
        return new ChangeTableRow(this);
      }

      /**
       * Set the print stream to emit HTML contents.
       *
       * @param out the {@link PrintStream} {@code out}.
       * @return the {@link Builder}.
       */
      public Builder setPrintStream(PrintStream out) {
        this.out = out;
        return this;
      }

      /**
       * Set the print stream to emit HTML contents.
       *
       * @param formatter a {@link Formatter} for formatting HTML expressions.
       * @return the {@link Builder}.
       */
      public Builder setFormatter(Formatter formatter) {
        this.formatter = formatter;
        return this;
      }

      /**
       * Set the print stream to emit HTML contents.
       *
       * @param rowName the display name of the parameter the row.
       * @return the {@link Builder}.
       */
      public Builder setRowName(String rowName) {
        this.rowName = rowName;
        return this;
      }

      /**
       * Set the print stream to emit HTML contents.
       *
       * @param paramName the parameter name the row corresponds to (this will be used as URL query
       *     parameter, e.g. /traceconfigz?maxnumofattributes=30).
       * @return the {@link Builder}.
       */
      public Builder setParamName(String paramName) {
        this.paramName = paramName;
        return this;
      }

      /**
       * Set the print stream to emit HTML contents.
       *
       * @param defaultValue the default value of the corresponding parameter.
       * @return the {@link Builder}.
       */
      public Builder setParamDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
      }

      /**
       * Set the print stream to emit HTML contents.
       *
       * @param zebraStripe the boolean for zebraStriping rows.
       * @return the {@link Builder}.
       */
      public Builder setZebraStripe(boolean zebraStripe) {
        this.zebraStripe = zebraStripe;
        return this;
      }

      private PrintStream out;
      private Formatter formatter;
      private String rowName;
      private String paramName;
      private String defaultValue;
      private boolean zebraStripe;
    }

    public static Builder builder() {
      return new Builder();
    }

    public void emitHtml() {
      if (zebraStripe) {
        formatter.format("<tr style=\"background-color: %s;\">", ZEBRA_STRIPE_COLOR);
      } else {
        out.print("<tr>");
      }
      formatter.format("<td>%s</td>", rowName);
      formatter.format(
          "<td class=\"border-left-dark\"><input type=text size=15 name=%s value=\"\" /></td>",
          paramName);
      formatter.format("<td class=\"border-left-dark\">(%s)</td>", defaultValue);
      out.print("</tr>");
    }
  }

  /**
   * Emits the change tracing parameter table to the {@link PrintStream} {@code out}.
   *
   * @param out the {@link PrintStream} {@code out}.
   * @param formatter a {@link Formatter} for formatting HTML expressions.
   */
  private static void emitChangeTable(PrintStream out, Formatter formatter) {
    out.print("<table style=\"border-spacing: 0; border: 1px solid #363636;\">");
    out.print("<tr class=\"bg-color\">");
    out.print("<th colspan=3 class=\"header-text\"><b>Permanently change</b></th>");
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setFormatter(formatter)
        .setRowName("SamplingProbability to")
        .setParamName(QUERY_STRING_SAMPLING_PROBABILITY)
        .setParamDefaultValue("1.0")
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setFormatter(formatter)
        .setRowName("MaxNumberOfAttributes to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_ATTRIBUTES)
        .setParamDefaultValue(Integer.toString(TraceConfig.getDefault().getMaxNumberOfAttributes()))
        .setZebraStripe(true)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setFormatter(formatter)
        .setRowName("MaxNumberOfEvents to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_EVENTS)
        .setParamDefaultValue(Integer.toString(TraceConfig.getDefault().getMaxNumberOfEvents()))
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setFormatter(formatter)
        .setRowName("MaxNumberOfLinks to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_LINKS)
        .setParamDefaultValue(Integer.toString(TraceConfig.getDefault().getMaxNumberOfLinks()))
        .setZebraStripe(true)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setFormatter(formatter)
        .setRowName("MaxNumberOfAttributesPerEvent to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_ATTRIBUTES_PER_EVENT)
        .setParamDefaultValue(
            Integer.toString(TraceConfig.getDefault().getMaxNumberOfAttributesPerEvent()))
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setFormatter(formatter)
        .setRowName("MaxNumberOfAttributesPerLink to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_ATTRIBUTES_PER_LINK)
        .setParamDefaultValue(
            Integer.toString(TraceConfig.getDefault().getMaxNumberOfAttributesPerLink()))
        .setZebraStripe(true)
        .build()
        .emitHtml();
  }

  /**
   * Emits the active tracing parameters table to the {@link PrintStream} {@code out}.
   *
   * @param out the {@link PrintStream} {@code out}.
   * @param formatter a {@link Formatter} for formatting HTML expressions.
   */
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
    out.print("<form class=\"form-flex\" action=\"" + TRACE_CONFIGZ_URL + "\" method=\"get\">");
    out.print("<input type=\"hidden\" name=\"change\" value=\"\" />");
    emitChangeTable(out, formatter);
    // Button for submit
    out.print("<button class=\"button\" type=\"submit\" value=\"Submit\">Submit</button>");
    out.print("</form>");
    // Button for restore default
    out.print("<form class=\"form-flex\" action=\"" + TRACE_CONFIGZ_URL + "\" method=\"get\">");
    out.print("<input type=\"hidden\" name=\"default\" value=\"\" />");
    out.print("<button class=\"button\" type=\"submit\" value=\"Submit\">Restore Default</button>");
    out.print("</form>");
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
