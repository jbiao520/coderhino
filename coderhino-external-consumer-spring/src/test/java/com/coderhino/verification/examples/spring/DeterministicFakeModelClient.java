package com.coderhino.verification.examples.spring;

import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelResponse;
import com.coderhino.query.QueryRequest;
import com.coderhino.state.BootstrapState;

import java.util.ArrayList;
import java.util.List;

public final class DeterministicFakeModelClient implements ModelClient {
    private final ModelResponse response;
    private final RuntimeException failure;
    private final List<QueryRequest> requests = new ArrayList<>();

    private DeterministicFakeModelClient(ModelResponse response, RuntimeException failure) {
        this.response = response;
        this.failure = failure;
    }

    public static DeterministicFakeModelClient replying(String text) {
        return replying(text, new ModelResponse.Usage(1, 1));
    }

    public static DeterministicFakeModelClient replying(String text, ModelResponse.Usage usage) {
        return new DeterministicFakeModelClient(new ModelResponse.AssistantReply(text, usage), null);
    }

    public static DeterministicFakeModelClient failing(RuntimeException failure) {
        return new DeterministicFakeModelClient(null, failure);
    }

    @Override
    public ModelResponse complete(BootstrapState bootstrapState, QueryRequest request) {
        requests.add(request);
        if (failure != null) {
            throw failure;
        }
        return response;
    }

    public int requestCount() {
        return requests.size();
    }

    public QueryRequest lastRequest() {
        return requests.isEmpty() ? null : requests.get(requests.size() - 1);
    }
}
