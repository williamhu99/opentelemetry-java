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

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;

final class TraceConfigzZPageHandler extends ZPageHandler {
  private static final String TRACE_CONFIGZ_URL = "/traceconfigz";

  @Override
  public String getUrlPath() {
    return TRACE_CONFIGZ_URL;
  }

  /**
   * Emits CSS styles to the {@link PrintStream} {@code out}. Content emited by this function should
   * be enclosed by <head></head> tag.
   * @param out the {@link PrintStream} {@code out}.
   */
  private static void emitHtmlStyle(PrintStream out) {
    out.print("<style>");
    out.print(ZPageStyle.style);
    out.print("</style>");
  }

  /**
   * Emits HTML body content to the {@link PrintStream} {@code out}. Content emitted by this function
   * should be enclosed by <body></body> tag.
   * @param queryMap the map containing URL query parameters
   * @param out the {@link PrintStream} {@code out}.
   */
  private void emitHtmlBody(Map<String, String> queryMap, PrintStream out) throws UnsupportedEncodingException {

  }

  @Override
  public void emitHtml(Map<String, String> queryMap, OutputStream outputStream) {
    // PrintStream for emiting HTML contents
    try (PrintStream out = new PrintStream(outputStream, /* autoFlush= */false, "UTF-8")){
      out.print("<!DOCTYPE html>");
      out.print("<html lang=\"en\">");
      out.print("<head>");
      out.print("<meta charset=\"UTF-8\">");
      out.print("<link rel=\"shortcut icon\" href=\"data:image/png;base64,"+ZPageLogo.faviconBase64+"\" type=\"image/png\">");
      out.print("<link href=\"https://fonts.googleapis.com/css?family=Open+Sans:300\"" + "rel=\"stylesheet\">");
      out.print("<link href=\"https://fonts.googleapis.com/css?family=Roboto\" rel=\"stylesheet\">");
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