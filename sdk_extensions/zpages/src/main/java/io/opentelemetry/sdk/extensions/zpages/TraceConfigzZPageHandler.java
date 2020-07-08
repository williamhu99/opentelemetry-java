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
  private final TraceConfig traceConfig;

  /** Builder pattern class for emiting a single row of the change parameter table. */
  private static final class ChangeTableRow {
    private final PrintStream out;
    private final String rowName;
    private final String paramName;
    private final String defaultValue;
    private final boolean zebraStripe;

    private ChangeTableRow(Builder builder) {
      out = builder.out;
      rowName = builder.rowName;
      paramName = builder.paramName;
      defaultValue = builder.defaultValue;
      zebraStripe = builder.zebraStripe;
    }

    public static class Builder {
      private PrintStream out;
      private String rowName;
      private String paramName;
      private String defaultValue;
      private boolean zebraStripe;

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
       * Set the display name of the parameter the row corresponds to.
       *
       * @param rowName the display name of the parameter the row corresponds to.
       * @return the {@link Builder}.
       */
      public Builder setRowName(String rowName) {
        this.rowName = rowName;
        return this;
      }

      /**
       * Set the parameter name the row corresponds to.
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
       * Set the default value of the parameter the row corresponds to.
       *
       * @param defaultValue the default value of the corresponding parameter.
       * @return the {@link Builder}.
       */
      public Builder setParamDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
      }

      /**
       * Set the boolean for zebraStriping the row.
       *
       * @param zebraStripe the boolean for zebraStriping the row.
       * @return the {@link Builder}.
       */
      public Builder setZebraStripe(boolean zebraStripe) {
        this.zebraStripe = zebraStripe;
        return this;
      }
    }

    public static Builder builder() {
      return new Builder();
    }

    /** Emit HTML content to the PrintStream. */
    public void emitHtml() {
      if (zebraStripe) {
        out.print("<tr style=\"background-color: " + ZEBRA_STRIPE_COLOR + ";\">");
      } else {
        out.print("<tr>");
      }
      out.print("<td>" + rowName + "</td>");
      out.print(
          "<td class=\"border-left-dark\"><input type=text size=15 name="
              + paramName
              + " value=\"\" /></td>");
      out.print("<td class=\"border-left-dark\">(" + defaultValue + ")</td>");
      out.print("</tr>");
    }
  }

  private static final class ActiveTableRow {
    private final PrintStream out;
    private final String paramName;
    private final String paramValue;
    private final boolean zebraStripe;

    private ActiveTableRow(Builder builder) {
      out = builder.out;
      paramName = builder.paramName;
      paramValue = builder.paramValue;
      zebraStripe = builder.zebraStripe;
    }

    public static class Builder {
      private PrintStream out;
      private String paramName;
      private String paramValue;
      private boolean zebraStripe;

      public ActiveTableRow build() {
        return new ActiveTableRow(this);
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
       * Set the parameter name the row corresponds to.
       *
       * @param paramName the parameter name the row corresponds to.
       * @return the {@link Builder}.
       */
      public Builder setParamName(String paramName) {
        this.paramName = paramName;
        return this;
      }

      /**
       * Set the parameter value the row corresponds to.
       *
       * @param paramValue the parameter value the row corresponds to.
       * @return the {@link Builder}.
       */
      public Builder setParamValue(String paramValue) {
        this.paramValue = paramValue;
        return this;
      }

      /**
       * Set the boolean for zebraStriping the row.
       *
       * @param zebraStripe the boolean for zebraStriping the row.
       * @return the {@link Builder}.
       */
      public Builder setZebraStripe(boolean zebraStripe) {
        this.zebraStripe = zebraStripe;
        return this;
      }
    }

    public static Builder builder() {
      return new Builder();
    }

    /** Emit HTML content to the PrintStream. */
    public void emitHtml() {
      if (zebraStripe) {
        out.print("<tr style=\"background-color: " + ZEBRA_STRIPE_COLOR + ";\">");
      } else {
        out.print("<tr>");
      }
      out.print("<td>" + paramName + "</td>");
      out.print("<td class=\"border-left-dark\">" + paramValue + "</td>");
      out.print("</tr>");
    }
  }

  TraceConfigzZPageHandler(TraceConfig traceConfig) {
    this.traceConfig = traceConfig;
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
    out.print(
        "<th colspan=1 class=\"header-text border-left-white\"><b>Permanently change</b></th>");
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setRowName("SamplingProbability to")
        .setParamName(QUERY_STRING_SAMPLING_PROBABILITY)
        .setParamDefaultValue(TraceConfig.getDefault().getSampler().getDescription())
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setRowName("MaxNumberOfAttributes to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_ATTRIBUTES)
        .setParamDefaultValue(Integer.toString(TraceConfig.getDefault().getMaxNumberOfAttributes()))
        .setZebraStripe(true)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setRowName("MaxNumberOfEvents to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_EVENTS)
        .setParamDefaultValue(Integer.toString(TraceConfig.getDefault().getMaxNumberOfEvents()))
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setRowName("MaxNumberOfLinks to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_LINKS)
        .setParamDefaultValue(Integer.toString(TraceConfig.getDefault().getMaxNumberOfLinks()))
        .setZebraStripe(true)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setRowName("MaxNumberOfAttributesPerEvent to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_ATTRIBUTES_PER_EVENT)
        .setParamDefaultValue(
            Integer.toString(TraceConfig.getDefault().getMaxNumberOfAttributesPerEvent()))
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ChangeTableRow.builder()
        .setPrintStream(out)
        .setRowName("MaxNumberOfAttributesPerLink to")
        .setParamName(QUERY_STRING_MAX_NUM_OF_ATTRIBUTES_PER_LINK)
        .setParamDefaultValue(
            Integer.toString(TraceConfig.getDefault().getMaxNumberOfAttributesPerLink()))
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
        .setParamValue(traceConfig.getSampler().getDescription())
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ;
    ActiveTableRow.builder()
        .setPrintStream(out)
        .setParamName("MaxNumOfAttributes")
        .setParamValue(Integer.toString(traceConfig.getMaxNumberOfAttributes()))
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ;
    ActiveTableRow.builder()
        .setPrintStream(out)
        .setParamName("MaxNumOfEvents")
        .setParamValue(Integer.toString(traceConfig.getMaxNumberOfEvents()))
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ;
    ActiveTableRow.builder()
        .setPrintStream(out)
        .setParamName("MaxNumOfLinks")
        .setParamValue(Integer.toString(traceConfig.getMaxNumberOfLinks()))
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ;
    ActiveTableRow.builder()
        .setPrintStream(out)
        .setParamName("MaxNumOfAttributesPerEvent")
        .setParamValue(Integer.toString(traceConfig.getMaxNumberOfAttributesPerEvent()))
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ;
    ActiveTableRow.builder()
        .setPrintStream(out)
        .setParamName("MaxNumOfAttributesPerLink")
        .setParamValue(Integer.toString(traceConfig.getMaxNumberOfAttributesPerLink()))
        .setZebraStripe(false)
        .build()
        .emitHtml();
    ;
    out.print("</table>");
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
    out.print("<input type=\"hidden\" name=\"change\" value=\"\" />");
    emitChangeTable(out);
    // Button for submit
    out.print("<button class=\"button\" type=\"submit\" value=\"Submit\">Submit</button>");
    out.print("</form>");
    // Button for restore default
    out.print("<form class=\"form-flex\" action=\"" + TRACE_CONFIGZ_URL + "\" method=\"get\">");
    out.print("<input type=\"hidden\" name=\"default\" value=\"\" />");
    out.print("<button class=\"button\" type=\"submit\" value=\"Submit\">Restore Default</button>");
    out.print("</form>");
    emitActiveTable(out);
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
