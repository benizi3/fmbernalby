package com.virjar.hermes.hermesagent.host.http;

import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.CommonRes;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.Constant;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2018/8/25.
 */
@Slf4j
public class J2ExecutorWrapper {
    private ThreadPoolExecutor threadPoolExecutor;
    private Runnable runnable;
    private AsyncHttpServerResponse response;

    J2ExecutorWrapper(ThreadPoolExecutor threadPoolExecutor, Runnable runnable, AsyncHttpServerResponse response) {
        this.threadPoolExecutor = threadPoolExecutor;
        this.runnable = runnable;
        this.response = response;
    }

    public void run() {
        Thread.currentThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                log.warn("request process failed, ", e);
                CommonUtils.sendJSON(response, CommonRes.failed(APICommonUtils.translateSimpleExceptionMessage(e)));
            }
        });
        try {
            threadPoolExecutor.execute(runnable);
        } catch (RejectedExecutionException e) {
            log.warn(Constant.rateLimitedMessage);
            CommonUtils.sendJSON(response, CommonRes.failed(Constant.status_rate_limited, Constant.rateLimitedMessage));
        }
    }
}
