package com.virjar.hermes.hermesagent.hermes_api;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;
import android.util.Log;

import org.apache.commons.io.FileUtils;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;

/**
 * Created by zhangyunfei on 15/9/21.
 * <br>由于跨进程问题，assets无法使用，所以通过代码来控制日志配置
 */
public class LogConfigurator {

    public static void configure(Context context) {
        String processName = context.getPackageName();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            for (ActivityManager.RunningAppProcessInfo info : activityManager.getRunningAppProcesses()) {
                if (info.pid == Process.myPid()) {
                    processName = info.processName;
                    break;
                }
            }
        }
        //还是由于进程问题，可能同一个app有用多个子进程，需要对进程做隔离，保证他们的日志文件不冲突
        File logDirs = new File(context.getFilesDir(), "hermesLog_" + EscapeUtil.escape(processName));
        try {
            FileUtils.forceMkdir(logDirs);
        } catch (IOException e) {
            Log.e("weijia", "can not create log directory", e);
            return;
        }
        final String PREFIX = "log";
        configureLogbackDirectly(logDirs.getAbsolutePath(), PREFIX);
    }

    private static void configureLogbackDirectly(String log_dir, String filePrefix) {
        // reset the default context (which may already have been initialized)
        // since we want to reconfigure it
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.setContext(context);
        encoder.start();


        RollingFileAppender<ILoggingEvent> rollingFileAppender = hermesSystemLogAppender(context, log_dir, filePrefix);
        rollingFileAppender.setEncoder(encoder);


        LogcatAppender logcatAppender = new LogcatAppender();
        logcatAppender.setContext(context);
        logcatAppender.setEncoder(encoder);
        logcatAppender.setName("logcat1");
        logcatAppender.start();

        //对运行日志，单独设置规则
        Logger wrapperLogger = (Logger) LoggerFactory.getLogger(Constant.hermesWrapperLogTag);
        wrapperLogger.setLevel(Level.INFO);
        RollingFileAppender<ILoggingEvent> wrapperAppender = wrapperLogAppender(context, log_dir, filePrefix);
        wrapperAppender.setEncoder(encoder);
        wrapperLogger.addAppender(wrapperAppender);
        wrapperLogger.addAppender(logcatAppender);
        wrapperLogger.setAdditive(false);
        wrapperAppender.start();

        // add the newly created appenders to the root logger;
        // qualify Logger to disambiguate from org.slf4j.Logger
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.INFO);
        root.addAppender(rollingFileAppender);
        root.addAppender(logcatAppender);
        rollingFileAppender.start();

        // print any status messages (warnings, etc) encountered in logback config
        //StatusPrinter.print(context);


    }

    private static RollingFileAppender<ILoggingEvent> wrapperLogAppender(LoggerContext context, String log_dir, String filePrefix) {
        RollingFileAppender<ILoggingEvent> rollingFileAppender = new RollingFileAppender<>();
        rollingFileAppender.setAppend(true);
        rollingFileAppender.setFile(log_dir + "/" + filePrefix + "hermes_wrapper.log");
        rollingFileAppender.setContext(context);

        FixedWindowRollingPolicy fixedWindowRollingPolicy = new FixedWindowRollingPolicy();
        fixedWindowRollingPolicy.setFileNamePattern(log_dir + "/" + filePrefix + "hermes_wrapper_%i.log");
        fixedWindowRollingPolicy.setMaxIndex(5);
        fixedWindowRollingPolicy.setMinIndex(1);
        fixedWindowRollingPolicy.setParent(rollingFileAppender);
        fixedWindowRollingPolicy.setContext(context);
        fixedWindowRollingPolicy.start();

        SizeBasedTriggeringPolicy<ILoggingEvent> sizeBasedTriggeringPolicy = new SizeBasedTriggeringPolicy<>();
        sizeBasedTriggeringPolicy.setMaxFileSize(FileSize.valueOf("100MB"));
        sizeBasedTriggeringPolicy.setContext(context);
        sizeBasedTriggeringPolicy.start();

        rollingFileAppender.setRollingPolicy(fixedWindowRollingPolicy);
        rollingFileAppender.setTriggeringPolicy(sizeBasedTriggeringPolicy);

        //rollingFileAppender.setEncoder(encoder);
        return rollingFileAppender;
    }

    private static RollingFileAppender<ILoggingEvent> hermesSystemLogAppender(LoggerContext context, String log_dir, String filePrefix) {
        RollingFileAppender<ILoggingEvent> rollingFileAppender = new RollingFileAppender<>();
        rollingFileAppender.setAppend(true);
        rollingFileAppender.setContext(context);

        // OPTIONAL: Set an active log file (separate from the rollover files).
        // If rollingPolicy.fileNamePattern already set, you don't need this.
        //rollingFileAppender.setFile(LOG_DIR + "/log.txt");

        TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setFileNamePattern(log_dir + "/" + filePrefix + "hermes_system_%d{yyyyMMdd_HH}.log");
        //记录12个小时的日志
        rollingPolicy.setMaxHistory(12);
        rollingPolicy.setParent(rollingFileAppender);  // parent and context required!
        rollingPolicy.setContext(context);
        rollingPolicy.start();

        rollingFileAppender.setRollingPolicy(rollingPolicy);

        //rollingFileAppender.setEncoder(encoder);
        //rollingFileAppender.start();
        return rollingFileAppender;
    }

}
