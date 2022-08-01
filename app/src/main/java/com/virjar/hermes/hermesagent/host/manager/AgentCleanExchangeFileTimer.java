package com.virjar.hermes.hermesagent.host.manager;

import com.virjar.hermes.hermesagent.hermes_api.Constant;
import com.virjar.xposed_extention.SharedObject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * the exchange file for IPC clean failed by IPC callback(remote service dead etc)
 */
public class AgentCleanExchangeFileTimer extends LoggerTimerTask {
    //hermes_exchange_1545361753323_185_1
    //hermes_exchange_1545362256496_8_1
    private static final Pattern exchangeFileNamePattern = Pattern.compile("hermes_exchange_(\\d+)_(\\d+)_(\\d+)");

    @Override
    public void doRun() {
        File[] files = SharedObject.context.getCacheDir().listFiles();
        if (files == null) {
            return;
        }
        long cleanStart = System.currentTimeMillis() - 5 * 60 * 1000;
        for (File file : files) {
            if (!file.getName().startsWith(Constant.exchangeFilePreffx)) {
                continue;
            }
            Matcher matcher = exchangeFileNamePattern.matcher(file.getName());
            if (!matcher.matches()) {
                continue;
            }
            long timestamp = NumberUtils.toLong(matcher.group(1), -1);
            if (timestamp < 0) {
                continue;
            }
            if (timestamp < cleanStart) {
                FileUtils.deleteQuietly(file);
            }
        }
    }
}
