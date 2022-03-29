package com.virjar.hermes.hermesagent.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2018/10/10.<br>
 * 当线程池发生了异常，如果没有把他try住，那么会直接导致app闪退，如果此时又没有记录日志的话，可能永远找不到为啥闪退的
 */
@Slf4j
public class LogedExceptionHandler implements Thread.UncaughtExceptionHandler {
    private Thread.UncaughtExceptionHandler origin;

    public LogedExceptionHandler(Thread.UncaughtExceptionHandler origin) {
        this.origin = origin;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        log.error("uncaughtException for thread:{}", t.getName(), e);
        origin.uncaughtException(t, e);
    }

    public static LogedExceptionHandler wrap(Thread.UncaughtExceptionHandler origin) {
        if (origin instanceof LogedExceptionHandler) {
            return (LogedExceptionHandler) origin;
        }
        return new LogedExceptionHandler(origin);
    }
}
