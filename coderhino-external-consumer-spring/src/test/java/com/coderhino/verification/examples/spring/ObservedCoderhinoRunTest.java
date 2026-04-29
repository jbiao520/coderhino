package com.coderhino.verification.examples.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.agent.spring.CoderhinoAgentAutoConfiguration;
import com.coderhino.query.ModelClient;
import com.coderhino.query.QueryEventSink;
import com.coderhino.query.QueryResult;
import com.coderhino.state.BootstrapState;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ObservedCoderhinoRunTest {
    private final ApplicationContextRunner successRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CoderhinoAgentAutoConfiguration.class))
        .withUserConfiguration(FakeModelClientConfiguration.class, ConfiguredSinkConfiguration.class);

    private final ApplicationContextRunner failureRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CoderhinoAgentAutoConfiguration.class))
        .withUserConfiguration(FailingModelClientConfiguration.class, ConfiguredSinkConfiguration.class);

    @Test
    void autoConfiguredAgentUsesConfiguredSinkBeanForSuccessfulRun() {
        successRunner.run(context -> {
            var agent = context.getBean(CoderhinoAgent.class);
            var sink = context.getBean(ObservedCoderhinoRun.RecordingSink.class);
            var result = ObservedCoderhinoRun.run(agent);
            var observation = ObservedCoderhinoRun.observe(ObservedCoderhinoRun.configuredSinkRequest(), result, sink);

            assertThat(context).hasSingleBean(QueryEventSink.class);
            assertThat(agent.config().eventSink()).isSameAs(sink);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isError()).isFalse();
            assertThat(result.stopReason()).isEqualTo(QueryResult.StopReason.END_TURN);
            assertThat(result.finalText()).isEqualTo(FakeModelClientConfiguration.FIXED_REPLY);

            assertThat(observation.visibleInput()).isEqualTo(ObservedCoderhinoRun.CONFIGURED_SINK_VISIBLE_INPUT);
            assertThat(observation.finalText()).isEqualTo(FakeModelClientConfiguration.FIXED_REPLY);
            assertThat(observation.success()).isTrue();
            assertThat(observation.error()).isFalse();
            assertThat(observation.completedText()).isEqualTo(FakeModelClientConfiguration.FIXED_REPLY);
            assertThat(observation.errors()).isEmpty();
            assertThat(observation.textChunks()).containsExactly(FakeModelClientConfiguration.FIXED_REPLY);
            assertThat(observation.usages()).containsExactly(new ObservedCoderhinoRun.UsageRecord(7, 3, 0, 0));
        });
    }

    @Test
    void requestLevelSinkOverridesConfiguredSinkForThatRun() {
        successRunner.run(context -> {
            var agent = context.getBean(CoderhinoAgent.class);
            var configuredSink = context.getBean(ObservedCoderhinoRun.RecordingSink.class);
            var requestSink = ObservedCoderhinoRun.newRecorder();
            var request = ObservedCoderhinoRun.requestSinkOverride(requestSink);

            var result = ObservedCoderhinoRun.run(agent, request);
            var requestObservation = ObservedCoderhinoRun.observe(request, result, requestSink);

            assertThat(agent.config().eventSink()).isSameAs(configuredSink);
            assertThat(result.isSuccess()).isTrue();
            assertThat(requestObservation.completedText()).isEqualTo(FakeModelClientConfiguration.FIXED_REPLY);
            assertThat(requestObservation.errors()).isEmpty();
            assertThat(requestObservation.usages()).containsExactly(new ObservedCoderhinoRun.UsageRecord(7, 3, 0, 0));

            assertThat(configuredSink.completedText()).isNull();
            assertThat(configuredSink.errors()).isEmpty();
            assertThat(configuredSink.usages()).isEmpty();
            assertThat(configuredSink.textChunks()).isEmpty();
        });
    }

    @Test
    void deterministicModelFailureTriggersOnErrorInsteadOfOnCompleted() {
        failureRunner.run(context -> {
            var agent = context.getBean(CoderhinoAgent.class);
            var sink = context.getBean(ObservedCoderhinoRun.RecordingSink.class);

            var result = ObservedCoderhinoRun.run(agent);
            var observation = ObservedCoderhinoRun.observe(ObservedCoderhinoRun.configuredSinkRequest(), result, sink);

            assertThat(context).hasSingleBean(ModelClient.class);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isError()).isTrue();
            assertThat(result.stopReason()).isEqualTo(QueryResult.StopReason.ERROR);
            assertThat(result.finalText()).isEqualTo("Query engine error: deterministic fake failure");

            assertThat(observation.completedText()).isNull();
            assertThat(observation.errors()).containsExactly("deterministic fake failure");
            assertThat(observation.usages()).isEmpty();
            assertThat(observation.textChunks()).isEmpty();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class ConfiguredSinkConfiguration {
        @Bean
        ObservedCoderhinoRun.RecordingSink queryEventSink() {
            return ObservedCoderhinoRun.newRecorder();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FailingModelClientConfiguration {
        @Bean
        ModelClient failingModelClient() {
            return new ModelClient() {
                @Override
                public com.coderhino.query.ModelResponse complete(BootstrapState bootstrapState, com.coderhino.query.QueryRequest request) {
                    throw new IllegalStateException("deterministic fake failure");
                }
            };
        }
    }
}
