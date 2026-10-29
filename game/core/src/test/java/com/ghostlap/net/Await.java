package com.ghostlap.net;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Test helper: makes an async ApiClient call and waits (max 10 s) for its callback. */
public final class Await {

    private Await() {
    }

    public static <T> T success(Consumer<ApiCallback<T>> call) throws Exception {
        Object outcome = outcome(call);
        assertThat(outcome).as("expected success but got %s", outcome).isNotInstanceOf(ApiError.class);
        @SuppressWarnings("unchecked")
        T result = (T) outcome;
        return result;
    }

    public static <T> ApiError failure(Consumer<ApiCallback<T>> call) throws Exception {
        Object outcome = outcome(call);
        assertThat(outcome).as("expected failure but got %s", outcome).isInstanceOf(ApiError.class);
        return (ApiError) outcome;
    }

    private static <T> Object outcome(Consumer<ApiCallback<T>> call) throws Exception {
        CompletableFuture<Object> done = new CompletableFuture<>();
        call.accept(new ApiCallback<>() {
            @Override
            public void onSuccess(T result) {
                done.complete(result);
            }

            @Override
            public void onFailure(ApiError error) {
                done.complete(error);
            }
        });
        return done.get(10, TimeUnit.SECONDS);
    }
}
