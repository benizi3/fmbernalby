package com.virjar.hermes.hermesagent.hermes_api;

import android.content.Context;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by virjar on 2018/9/14.<br>
 * 创世纪，在插件环境下，这两个参数首先被赋值，很多工具类都会work base on this
 */

public class SharedObject {
    public static Context context;
    public static XC_LoadPackage.LoadPackageParam loadPackageParam;
}
