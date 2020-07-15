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

package io.opentelemetry.sdk.extensions.trace.aws.resource;

import static com.google.common.truth.Truth.assertThat;
import static io.opentelemetry.common.AttributeValue.stringAttributeValue;
import static org.mockito.Mockito.when;

import io.opentelemetry.common.Attributes;
import io.opentelemetry.sdk.resources.ResourceConstants;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class EcsResourceTest {
  private static final String ECS_METADATA_KEY_V4 = "ECS_CONTAINER_METADATA_URI_V4";
  private static final String ECS_METADATA_KEY_V3 = "ECS_CONTAINER_METADATA_URI";

  @Rule public MockitoRule mocks = MockitoJUnit.rule();

  @Mock private DockerHelper mockDockerHelper;

  @Test
  public void testCreateAttributes() throws UnknownHostException {
    when(mockDockerHelper.getContainerId()).thenReturn("0123456789A");
    Map<String, String> mockSysEnv = new HashMap<>();
    mockSysEnv.put(ECS_METADATA_KEY_V3, "ecs_metadata_v3_uri");
    EcsResource populator = new EcsResource(mockSysEnv, mockDockerHelper);
    Attributes attributes = populator.createAttributes();
    assertThat(attributes)
        .isEqualTo(
            Attributes.of(
                ResourceConstants.CONTAINER_NAME,
                stringAttributeValue(InetAddress.getLocalHost().getHostName()),
                ResourceConstants.CONTAINER_ID,
                stringAttributeValue("0123456789A")));
  }

  @Test
  public void testNotOnEcs() {
    when(mockDockerHelper.getContainerId()).thenReturn("0123456789A");
    Map<String, String> mockSysEnv = new HashMap<>();
    mockSysEnv.put(ECS_METADATA_KEY_V3, "");
    mockSysEnv.put(ECS_METADATA_KEY_V4, "");
    EcsResource populator = new EcsResource(mockSysEnv, mockDockerHelper);
    Attributes attributes = populator.createAttributes();
    assertThat(attributes.isEmpty()).isTrue();
  }

  @Test
  public void testContainerIdMissing() throws UnknownHostException {
    when(mockDockerHelper.getContainerId()).thenReturn("");
    Map<String, String> mockSysEnv = new HashMap<>();
    mockSysEnv.put(ECS_METADATA_KEY_V4, "ecs_metadata_v4_uri");
    EcsResource populator = new EcsResource(mockSysEnv, mockDockerHelper);
    Attributes attributes = populator.createAttributes();
    assertThat(attributes)
        .isEqualTo(
            Attributes.of(
                ResourceConstants.CONTAINER_NAME,
                stringAttributeValue(InetAddress.getLocalHost().getHostName())));
  }
}
