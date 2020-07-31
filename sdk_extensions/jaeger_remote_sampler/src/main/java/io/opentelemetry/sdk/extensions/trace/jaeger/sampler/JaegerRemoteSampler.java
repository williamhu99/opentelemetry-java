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

package io.opentelemetry.sdk.extensions.trace.jaeger.sampler;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.ManagedChannel;
import io.opentelemetry.common.ReadableAttributes;
import io.opentelemetry.exporters.jaeger.proto.api_v2.Sampling.PerOperationSamplingStrategies;
import io.opentelemetry.exporters.jaeger.proto.api_v2.Sampling.SamplingStrategyParameters;
import io.opentelemetry.exporters.jaeger.proto.api_v2.Sampling.SamplingStrategyResponse;
import io.opentelemetry.exporters.jaeger.proto.api_v2.SamplingManagerGrpc;
import io.opentelemetry.exporters.jaeger.proto.api_v2.SamplingManagerGrpc.SamplingManagerBlockingStub;
import io.opentelemetry.sdk.common.DaemonThreadFactory;
import io.opentelemetry.sdk.trace.Sampler;
import io.opentelemetry.sdk.trace.Samplers;
import io.opentelemetry.trace.Link;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.TraceId;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** Remote sampler that gets sampling configuration from remote Jaeger server. */
public class JaegerRemoteSampler implements Sampler {
  private static final Logger logger = Logger.getLogger(JaegerRemoteSampler.class.getName());

  private static final String WORKER_THREAD_NAME =
      JaegerRemoteSampler.class.getSimpleName() + "_WorkerThread";
  private static final int DEFAULT_POLLING_INTERVAL_MS = 60000;
  private static final Sampler INITIAL_SAMPLER = Samplers.probability(0.001);

  private final String serviceName;
  private final SamplingManagerBlockingStub stub;
  private Sampler sampler;

  @SuppressWarnings("FutureReturnValueIgnored")
  private JaegerRemoteSampler(
      String serviceName, ManagedChannel channel, int pollingIntervalMs, Sampler initialSampler) {
    this.serviceName = serviceName;
    this.stub = SamplingManagerGrpc.newBlockingStub(channel);
    this.sampler = initialSampler;
    ScheduledExecutorService scheduledExecutorService =
        Executors.newScheduledThreadPool(1, new DaemonThreadFactory(WORKER_THREAD_NAME));
    scheduledExecutorService.scheduleAtFixedRate(
        updateSampleRunnable(), 0, pollingIntervalMs, TimeUnit.MILLISECONDS);
  }

  private Runnable updateSampleRunnable() {
    return new Runnable() {
      @Override
      public void run() {
        try {
          getAndUpdateSampler();
        } catch (Exception e) { // keep the timer thread alive
          logger.log(Level.WARNING, "Failed to update sampler", e);
        }
      }
    };
  }

  @Override
  public SamplingResult shouldSample(
      @Nullable SpanContext parentContext,
      TraceId traceId,
      String name,
      Kind spanKind,
      ReadableAttributes attributes,
      List<Link> parentLinks) {
    return sampler.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks);
  }

  private void getAndUpdateSampler() {
    SamplingStrategyParameters params =
        SamplingStrategyParameters.newBuilder().setServiceName(this.serviceName).build();
    SamplingStrategyResponse response = stub.getSamplingStrategy(params);
    this.sampler = updateSampler(response);
  }

  private static Sampler updateSampler(SamplingStrategyResponse response) {
    PerOperationSamplingStrategies operationSampling = response.getOperationSampling();
    if (operationSampling != null && operationSampling.getPerOperationStrategiesList().size() > 0) {
      Sampler defaultSampler =
          Samplers.probability(operationSampling.getDefaultSamplingProbability());
      return new PerOperationSampler(
          defaultSampler, operationSampling.getPerOperationStrategiesList());
    }
    switch (response.getStrategyType()) {
      case PROBABILISTIC:
        return Samplers.probability(response.getProbabilisticSampling().getSamplingRate());
      case RATE_LIMITING:
        return new RateLimitingSampler(response.getRateLimitingSampling().getMaxTracesPerSecond());
      case UNRECOGNIZED:
        throw new AssertionError("unrecognized sampler type");
    }
    throw new AssertionError("unrecognized sampler type");
  }

  @Override
  public String getDescription() {
    return this.toString();
  }

  @Override
  public String toString() {
    return String.format("JaegerRemoteSampler{%s}", this.sampler);
  }

  @VisibleForTesting
  Sampler getSampler() {
    return this.sampler;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private ManagedChannel channel;
    private String serviceName;
    private Sampler initialSampler = INITIAL_SAMPLER;
    private int pollingIntervalMs = DEFAULT_POLLING_INTERVAL_MS;

    /**
     * Sets the service name to be used by this exporter. Required.
     *
     * @param serviceName the service name.
     * @return this.
     */
    public Builder setServiceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    /**
     * Sets the managed chanel to use when communicating with the backend. Required.
     *
     * @param channel the channel to use.
     * @return this.
     */
    public Builder setChannel(ManagedChannel channel) {
      this.channel = channel;
      return this;
    }

    /**
     * Sets the polling interval. Optional.
     *
     * @param pollingIntervalMs the polling interval in Ms.
     * @return this.
     */
    public Builder withPollingInterval(int pollingIntervalMs) {
      this.pollingIntervalMs = pollingIntervalMs;
      return this;
    }

    /**
     * Sets the initial sampler that is used before sampling configuration is obtained from the
     * server. By default probabilistic sampler with is used with probability 0.001. Optional.
     *
     * @param initialSampler the initial sampler to use.
     * @return this.
     */
    public Builder withInitialSampler(Sampler initialSampler) {
      this.initialSampler = initialSampler;
      return this;
    }

    /**
     * Builds the {@link JaegerRemoteSampler}.
     *
     * @return the remote sampler instance.
     */
    public JaegerRemoteSampler build() {
      return new JaegerRemoteSampler(
          this.serviceName, this.channel, this.pollingIntervalMs, this.initialSampler);
    }

    private Builder() {}
  }
}
