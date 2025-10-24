package com.ligitabl.api.shared;

public interface UseCase<Request, Response> {
    Response execute(Request request);

    // Marker for use cases with no input
    Void NO_INPUT = null;
}
