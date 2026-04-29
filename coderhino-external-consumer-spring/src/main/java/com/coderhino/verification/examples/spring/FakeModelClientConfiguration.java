package com.coderhino.verification.examples.spring;

import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelResponse;
import com.coderhino.query.QueryRequest;
import com.coderhino.state.BootstrapState;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class FakeModelClientConfiguration {
    public static final String FIXED_REPLY = "spring fake model reply";
    public static final ModelResponse.Usage FIXED_USAGE = new ModelResponse.Usage(7, 3, 0, 0);

    @Bean
    DeterministicSpringModelClient deterministicSpringModelClient() {
        return new DeterministicSpringModelClient(FIXED_REPLY, FIXED_USAGE);
    }

    public static final class DeterministicSpringModelClient implements ModelClient {
        private final String replyText;
        private final ModelResponse.Usage usage;
        private final List<QueryRequest> requests = new ArrayList<>();

        private DeterministicSpringModelClient(String replyText, ModelResponse.Usage usage) {
            this.replyText = replyText;
            this.usage = usage;
        }

        @Override
        public ModelResponse complete(BootstrapState bootstrapState, QueryRequest request) {
            requests.add(request);
            return new ModelResponse.AssistantReply(replyText, usage);
        }

        public int requestCount() {
            return requests.size();
        }

        public QueryRequest lastRequest() {
            return requests.isEmpty() ? null : requests.get(requests.size() - 1);
        }
    }
}
