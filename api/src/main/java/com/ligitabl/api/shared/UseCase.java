package com.ligitabl.api.shared;

public interface UseCase<Request, Response> {
    Response execute(Request request);

    default Response execute() {
        return execute(null);
    }
}
