package com.virjar.hermes.hermesagent.hermes_api;

/**
 * @author dengweijia
 * @since 1.0.7
 * lazy fill in invokeResult
 */
public interface InvokeResultPromise {
    int resultType();

    String body();
}
