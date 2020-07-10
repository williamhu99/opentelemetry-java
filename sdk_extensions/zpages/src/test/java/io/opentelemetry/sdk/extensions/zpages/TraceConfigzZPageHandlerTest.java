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

import static com.google.common.truth.Truth.assertThat;

import io.opentelemetry.sdk.trace.TracerSdkProvider;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link TraceConfigzZPageHandler}. */
@RunWith(MockitoJUnitRunner.class)
public final class TraceConfigzZPageHandlerTest {
  @Mock private TracerSdkProvider tracerProvider;
  @Mock private Map<String, String> queryMap;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  public void changeTable_emitRowsCorrectly() {
    OutputStream output = new ByteArrayOutputStream();
    String samplingProbability = "samplingprobability";
    String maxNumOfAttributes = "maxnumofattributes";
    String maxNumOfEvents = "maxnumbofevents";
    String maxNumOfLinks = "maxnumoflinks";
    String maxNumOfAttributesPerEvent = "maxnumofattributesperevent";
    String maxNumOfAttributesPerLink = "maxnumofattributesperlink";

    TraceConfigzZPageHandler traceConfigzZPageHandler =
        new TraceConfigzZPageHandler(tracerProvider);
    traceConfigzZPageHandler.emitHtml(queryMap, output);

    assertThat(output.toString()).contains("SamplingProbability to");
    assertThat(output.toString()).contains("name=" + samplingProbability);
    assertThat(output.toString()).contains("MaxNumberOfAttributes to");
    assertThat(output.toString()).contains("name=" + maxNumOfAttributes);
    assertThat(output.toString()).contains("MaxNumberOfEvents to");
    assertThat(output.toString()).contains("name=" + maxNumOfEvents);
    assertThat(output.toString()).contains("MaxNumberOfLinks to");
    assertThat(output.toString()).contains("name=" + maxNumOfLinks);
    assertThat(output.toString()).contains("MaxNumberOfAttributesPerEvent to");
    assertThat(output.toString()).contains("name=" + maxNumOfAttributesPerEvent);
    assertThat(output.toString()).contains("MaxNumberOfAttributesPerLink to");
    assertThat(output.toString()).contains("name=" + maxNumOfAttributesPerLink);
  }

  @Test
  public void activeTable_emitRowsCorrectly() {
    OutputStream output = new ByteArrayOutputStream();

    TraceConfigzZPageHandler traceConfigzZPageHandler =
        new TraceConfigzZPageHandler(tracerProvider);
    traceConfigzZPageHandler.emitHtml(queryMap, output);

    assertThat(output.toString()).contains("Sampler");
    assertThat(output.toString()).contains("MaxNumberOfAttributes");
    assertThat(output.toString()).contains("MaxNumberOfEvents");
    assertThat(output.toString()).contains("MaxNumberOfLinks");
    assertThat(output.toString()).contains("MaxNumberOfAttributesPerEvent");
    assertThat(output.toString()).contains("MaxNumberOfAttributesPerLink");
  }
}
