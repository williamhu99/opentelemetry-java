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

import io.opentelemetry.common.Attributes;
import io.opentelemetry.trace.Span;
import javax.annotation.concurrent.Immutable;

/** Defines the behavior for a span attribute with long values. */
@Immutable
public final class LongAttributeSetter {

  /**
   * Returns a new attribute setter.
   *
   * @param attributeKey the attribute name
   * @return the setter object
   */
  public static LongAttributeSetter create(String attributeKey) {
    return new LongAttributeSetter(attributeKey);
  }

  private final String attributeKey;

  private LongAttributeSetter(String attributeKey) {
    if (attributeKey == null || attributeKey.length() == 0) {
      throw new IllegalArgumentException("attributeKey cannot be empty");
    }
    this.attributeKey = attributeKey;
  }

  /**
   * Returns the attribute name.
   *
   * @return the attribute map key
   */
  public String key() {
    return attributeKey;
  }

  /**
   * Sets the attribute on the provided span.
   *
   * @param span the span to add the attribute to
   * @param value the value for this attribute
   */
  public void set(Span span, long value) {
    span.setAttribute(key(), value);
  }

  /**
   * Sets the attribute on the provided span builder.
   *
   * @param spanBuilder the span builder to add the attribute to
   * @param value the value for this attribute
   */
  public void set(Span.Builder spanBuilder, long value) {
    spanBuilder.setAttribute(key(), value);
  }

  /**
   * Sets the attribute on the provided {@link Attributes.Builder}.
   *
   * @param attributesBuilder the attributes builder to add the attribute to
   * @param value the value for this attribute
   */
  public void set(Attributes.Builder attributesBuilder, long value) {
    attributesBuilder.setAttribute(key(), value);
  }
}
