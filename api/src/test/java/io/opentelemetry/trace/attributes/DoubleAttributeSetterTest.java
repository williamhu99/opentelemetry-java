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

package io.opentelemetry.trace.attributes;

import static com.google.common.truth.Truth.assertThat;
import static io.opentelemetry.common.AttributeValue.doubleAttributeValue;

import io.opentelemetry.common.Attributes;
import org.junit.Test;

public class DoubleAttributeSetterTest {

  @Test
  public void attributesBuilder() {
    DoubleAttributeSetter setter = DoubleAttributeSetter.create("how much?");
    Attributes.Builder attributes = Attributes.newBuilder();
    setter.set(attributes, 10.0);
    assertThat(attributes.build().get("how much?")).isEqualTo(doubleAttributeValue(10.0));
  }
}
