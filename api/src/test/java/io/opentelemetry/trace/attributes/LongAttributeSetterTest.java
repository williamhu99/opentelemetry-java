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
import static io.opentelemetry.common.AttributeValue.longAttributeValue;

import io.opentelemetry.common.Attributes;
import org.junit.Test;

public class LongAttributeSetterTest {

  @Test
  public void attributesBuilder() {
    LongAttributeSetter setter = LongAttributeSetter.create("how much?");
    Attributes.Builder attributes = Attributes.newBuilder();
    setter.set(attributes, 10);
    assertThat(attributes.build().get("how much?")).isEqualTo(longAttributeValue(10));
  }
}
