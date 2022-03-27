package com.virjar.hermes.hermesagent.host.manager;

import org.apache.commons.lang3.StringUtils;

import java.util.TimerTask;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2018/10/10.<br>记录所有timer的执行日志
 */
@Slf4j
public abstract class LoggerTimerTask extends TimerTask {
    private String taskName;

    public LoggerTimerTask(String taskName) {
        this.taskName = taskName;
        if (StringUtils.isBlank(this.taskName)) {
            this.taskName = getClass().getName();
        }
    }

    public LoggerTimerTask() {
        this.taskName = getClass().getName();
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        log.info("timer :{} task begin", taskName);
        try {
            doRun();
        } catch (RuntimeException e) {
            log.warn("task execute error", e);
            throw e;
        } finally {
            log.info("timer: :{} end,duration:{} ", taskName, (System.currentTimeMillis() - start));
        }
    }

    public abstract void doRun();
}
