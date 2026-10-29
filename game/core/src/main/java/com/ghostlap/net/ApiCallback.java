package com.ghostlap.net;

/**
 * Receives the result of a server call. Exactly one method is called, on the game (render) thread,
 * so it is safe to change game state from here.
 */
public interface ApiCallback<T> {

    void onSuccess(T result);

    void onFailure(ApiError error);
}
