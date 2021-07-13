package com.virjar.hermes.hermesagent.host.http;

import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.virjar.hermes.hermesagent.bean.CommonRes;
import com.virjar.hermes.hermesagent.util.CommonUtils;
import com.virjar.hermes.hermesagent.util.Constant;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Created by virjar on 2018/8/25.
 */

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
        try {
            threadPoolExecutor.execute(runnable);
        } catch (RejectedExecutionException e) {
            CommonUtils.sendJSON(response, CommonRes.failed(Constant.status_rate_limited, Constant.rateLimitedMessage));
        }
    }
}
