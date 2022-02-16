package com.virjar.hermes.hermesagent.host.service;

import android.support.annotation.NonNull;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Created by virjar on 2018/9/25.
 */

public class PingWatchTask implements Delayed {
    public long needCheckTimestamp;
    public String targetPackageName;
    public boolean isDone = false;

    public PingWatchTask(long needCheckTimestamp, String targetPackageName) {
        this.needCheckTimestamp = needCheckTimestamp;
        this.targetPackageName = targetPackageName;
    }


    @Override
    public long getDelay(@NonNull TimeUnit unit) {
        return unit.convert(needCheckTimestamp - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(@NonNull Delayed o) {
        return Long.valueOf(getDelay(TimeUnit.MILLISECONDS)).compareTo(o.getDelay(TimeUnit.MILLISECONDS));
    }
}
