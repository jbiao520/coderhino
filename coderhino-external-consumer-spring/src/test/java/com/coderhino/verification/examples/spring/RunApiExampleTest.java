package com.coderhino.verification.examples.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.agent.spring.CoderhinoAgentAutoConfiguration;
import com.coderhino.query.ModelClient;
import com.coderhino.query.QueryResult;
import com.coderhino.types.Message;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunApiExampleTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CoderhinoAgentAutoConfiguration.class))
        .withUserConfiguration(FakeModelClientConfiguration.class);

    @Test
    void runStringUsesInjectedFakeModelClientWithoutCredentials() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(CoderhinoAgent.class);
            assertThat(context).hasSingleBean(ModelClient.class);
            assertThat(context).hasSingleBean(FakeModelClientConfiguration.DeterministicSpringModelClient.class);

            var agent = context.getBean(CoderhinoAgent.class);
            var fakeModelClient = context.getBean(FakeModelClientConfiguration.DeterministicSpringModelClient.class);

            assertThat(agent.config().modelClient()).isSameAs(fakeModelClient);

            var result = RunApiExample.runWithString(agent);

            assertThat(result.finalText()).isEqualTo(FakeModelClientConfiguration.FIXED_REPLY);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isError()).isFalse();
            assertThat(result.stopReason()).isEqualTo(QueryResult.StopReason.END_TURN);
            assertThat(result.iterationCount()).isEqualTo(1);
            assertThat(result.usage()).isEqualTo(FakeModelClientConfiguration.FIXED_USAGE);
            assertThat(fakeModelClient.requestCount()).isEqualTo(1);
            assertThat(fakeModelClient.lastRequest().messages())
                .anySatisfy(message -> assertThat(message)
                    .isInstanceOfSatisfying(Message.UserMessage.class, userMessage -> assertThat(userMessage.content()).isEqualTo(RunApiExample.STRING_INPUT)));
        });
    }

    @Test
    void runAgentRequestExposesVisibleInputAndDeterministicResultFields() {
        contextRunner.run(context -> {
            var agent = context.getBean(CoderhinoAgent.class);
            var fakeModelClient = context.getBean(FakeModelClientConfiguration.DeterministicSpringModelClient.class);
            var request = RunApiExample.defaultRequest();

            var result = RunApiExample.runWithRequest(agent, request);
            var observation = RunApiExample.observe(request, result);

            assertThat(result.finalText()).isEqualTo(FakeModelClientConfiguration.FIXED_REPLY);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isError()).isFalse();
            assertThat(result.stopReason()).isEqualTo(QueryResult.StopReason.END_TURN);
            assertThat(result.iterationCount()).isEqualTo(1);
            assertThat(result.usage()).isEqualTo(FakeModelClientConfiguration.FIXED_USAGE);
            assertThat(result.state().messages())
                .anySatisfy(message -> assertThat(message)
                    .isInstanceOfSatisfying(Message.UserMessage.class, userMessage -> assertThat(userMessage.content()).isEqualTo(RunApiExample.REQUEST_VISIBLE_INPUT)));

            assertThat(observation.visibleInput()).isEqualTo(RunApiExample.REQUEST_VISIBLE_INPUT);
            assertThat(observation.finalText()).isEqualTo(FakeModelClientConfiguration.FIXED_REPLY);
            assertThat(observation.success()).isTrue();
            assertThat(observation.error()).isFalse();
            assertThat(observation.stopReason()).isEqualTo(QueryResult.StopReason.END_TURN);
            assertThat(observation.iterationCount()).isEqualTo(1);
            assertThat(observation.usage()).isEqualTo(FakeModelClientConfiguration.FIXED_USAGE);

            assertThat(fakeModelClient.requestCount()).isEqualTo(1);
            assertThat(fakeModelClient.lastRequest().messages())
                .anySatisfy(message -> assertThat(message)
                    .isInstanceOfSatisfying(Message.UserMessage.class, userMessage -> assertThat(userMessage.content()).isEqualTo(RunApiExample.REQUEST_INPUT)));
        });
    }

    @Test
    void blankAgentRequestFailsFastBeforeAgentExecution() {
        assertThatThrownBy(() -> RunApiExample.request("   ", "visible input"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("input is required");
    }
}
