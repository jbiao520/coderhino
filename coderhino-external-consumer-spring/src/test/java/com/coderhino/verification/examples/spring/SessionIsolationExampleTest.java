package com.coderhino.verification.examples.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.agent.spring.CoderhinoAgentAutoConfiguration;
import com.coderhino.query.QueryResult;
import com.coderhino.state.BootstrapState;
import com.coderhino.types.Message;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SessionIsolationExampleTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CoderhinoAgentAutoConfiguration.class))
        .withUserConfiguration(FakeModelClientConfiguration.class);

    @Test
    void requestSpecificBootstrapStateCapturesMessagesWithoutMutatingManagedAgentState() {
        contextRunner.run(context -> {
            var agent = context.getBean(CoderhinoAgent.class);
            var fakeModelClient = context.getBean(FakeModelClientConfiguration.DeterministicSpringModelClient.class);
            var managedBeforeRun = agent.state();
            BootstrapState requestState = SessionIsolationExample.newSessionState(agent);

            var result = SessionIsolationExample.run(agent, requestState);
            var observation = SessionIsolationExample.observe(managedBeforeRun, agent, requestState, result);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isError()).isFalse();
            assertThat(result.stopReason()).isEqualTo(QueryResult.StopReason.END_TURN);
            assertThat(result.finalText()).isEqualTo(FakeModelClientConfiguration.FIXED_REPLY);
            assertThat(result.bootstrapState()).isSameAs(requestState);
            assertThat(result.state()).isEqualTo(requestState.get());

            assertThat(fakeModelClient.requestCount()).isEqualTo(1);
            assertThat(fakeModelClient.lastRequest().messages())
                .anySatisfy(message -> assertThat(message)
                    .isInstanceOfSatisfying(Message.UserMessage.class, userMessage -> assertThat(userMessage.content()).isEqualTo(SessionIsolationExample.REQUEST_INPUT)));

            assertThat(requestState.get().messages())
                .anySatisfy(message -> assertThat(message)
                    .isInstanceOfSatisfying(Message.UserMessage.class, userMessage -> assertThat(userMessage.content()).isEqualTo(SessionIsolationExample.REQUEST_VISIBLE_INPUT)));

            assertThat(observation.finalText()).isEqualTo(FakeModelClientConfiguration.FIXED_REPLY);
            assertThat(observation.stopReason()).isEqualTo(QueryResult.StopReason.END_TURN);
            assertThat(observation.managedStateUnchanged()).isTrue();
            assertThat(observation.requestStateCapturedMessages()).isTrue();
            assertThat(observation.managedMessageCountBeforeRun()).isZero();
            assertThat(observation.managedMessageCountAfterRun()).isZero();
            assertThat(observation.requestMessageCountAfterRun()).isGreaterThan(0);
            assertThat(observation.managedSessionIdAfterRun()).isEqualTo(observation.managedSessionIdBeforeRun());
            assertThat(observation.requestSessionId()).isNotEqualTo(observation.managedSessionIdBeforeRun());

            assertThat(agent.state()).isEqualTo(managedBeforeRun);
            assertThat(agent.state().messages()).isEmpty();
        });
    }
}
