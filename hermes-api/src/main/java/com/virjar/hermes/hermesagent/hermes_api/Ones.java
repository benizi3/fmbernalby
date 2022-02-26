package com.virjar.hermes.hermesagent.hermes_api;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by virjar on 2018/9/27.<br>
 * 针对于一个class，只能执行一次的任务,特别是hook逻辑，否则可能导致多次注册hook
 */

public class Ones {
    private static ConcurrentMap<Class<?>, Set<String>> completedTasks = Maps.newConcurrentMap();

    public interface DoOnce {
        void doOne(Class<?> clazz);
    }

    public static boolean hookOnes(Class<?> clazz, String taskType, DoOnce doOnce) {
        Set<String> tasks = completedTasks.get(clazz);
        if (tasks == null) {
            completedTasks.putIfAbsent(clazz, Sets.<String>newConcurrentHashSet());
            tasks = completedTasks.get(clazz);
        }
        if (tasks.contains(taskType)) {
            return false;
        }
        synchronized (Ones.class) {
            if (tasks.contains(taskType)) {
                return false;
            }
            doOnce.doOne(clazz);
            tasks.add(taskType);
        }
        return true;
    }
}
