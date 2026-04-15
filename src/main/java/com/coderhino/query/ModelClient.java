package com.coderhino.query;

import com.coderhino.state.BootstrapState;

public interface ModelClient {
    ModelResponse complete(BootstrapState bootstrapState, QueryRequest request);

    default ModelResponse complete(BootstrapState bootstrapState, QueryRequest request, ModelStreamEventSink streamSink) {
        return complete(bootstrapState, request);
    }
}
