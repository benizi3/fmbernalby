package com.virjar.hermes.hermesagent.xposed_classloader_bridge;

import dalvik.system.PathClassLoader;

/**
 * Created by virjar on 2018/9/21.
 */

public class XposedRelateClassLoader {
    public static ClassLoader createClassLoader(String sourcePath) {
        return new PathClassLoader(sourcePath, XposedRelateClassLoader.class.getClassLoader());
    }
}
