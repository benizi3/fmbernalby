package com.virjar.hermes.hermesagent.plugin;

import android.annotation.SuppressLint;
import android.app.Application;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Created by virjar on 2017/12/23.<br/>处理XposedHelpers一些不能处理的问题
 */

public class XposedReflectUtil {
    //能够子子类向父类寻找method
    public static void findAndHookMethodWithSupperClass(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        Class theClazz = clazz;
        NoSuchMethodError error = null;
        do {
            try {
                XposedHelpers.findAndHookMethod(theClazz, methodName, parameterTypesAndCallback);
                return;
            } catch (NoSuchMethodError e) {
                if (error == null) {
                    error = e;
                }
            }
        } while ((theClazz = theClazz.getSuperclass()) != null);
        throw error;
    }


    /**
     * 很多时候只有一个名字，各种寻找参数类型太麻烦了，该方法已过期，请使用XposedBridge#hookAllMethods
     *
     * @param clazz        class对象
     * @param methodName   想要被hook的方法名字
     * @param xcMethodHook 回调函数
     * @see XposedBridge#hookAllMethods(java.lang.Class, java.lang.String, XC_MethodHook)
     */
    public static void findAndHookMethodOnlyByMethodName(Class<?> clazz, String methodName, XC_MethodHook xcMethodHook) {
        Class theClazz = clazz;

        do {
            Method[] declaredMethods = theClazz.getDeclaredMethods();
            for (Method method : declaredMethods) {
                if (method.getName().equals(methodName)) {
                    Log.i("weijia", method.toString());
                    XposedBridge.hookMethod(method, xcMethodHook);
                    return;
                }
            }
        } while ((theClazz = theClazz.getSuperclass()) != null);
        throw new NoSuchMethodError("no method " + methodName + " for class:" + clazz.getName());
    }

//    public static void findAndHookMethodOnlyByMethodName(String className, String methodName, XC_MethodHook xcMethodHook) {
//        Class<?> aClass = XposedHelpers.findClass(className, SharedObject.masterClassLoader);
//        findAndHookMethodOnlyByMethodName(aClass, methodName, xcMethodHook);
//    }


    public static void printAllMethod(Class clazz) {
        while (clazz != null) {
            Method[] declaredMethods = clazz.getDeclaredMethods();
            for (Method method : declaredMethods) {
                XposedBridge.log("printMethod: " + method);
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * 基于堆栈回溯方法，可以找方法名字，但是存在方法重载的时候，一个methodName可能对应多个实现。此时由于行号信息不应正常，这会导致无法确定具体那个方法被调用。
     * <br>
     * 通过此方法打印所有同名函数，帮组确定那个方法被调用
     *
     * @param clazz class
     * @param name  希望被监控的方法名称
     */
    public static void monitorMethodCall(Class clazz, String name) {
        Method[] declaredMethods = clazz.getDeclaredMethods();
        for (Method method : declaredMethods) {
            if (method.getName().equals(name)) {
                XposedBridge.hookMethod(method, methodCallPrintHook);
            }
        }
    }

    private static SingletonXC_MethodHook methodCallPrintHook = new SingletonXC_MethodHook() {
        @Override
        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
            Log.i("methodCall", "the method: " + param.method);
        }
    };

    @SuppressLint("PrivateApi")
    public static Application getApplicationUsingReflection() throws Exception {
        return (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null,
                (Object[]) null);
    }

    private static void makeAccessible(Field field) {
        if (!Modifier.isPublic(field.getModifiers())) {
            field.setAccessible(true);
        }
    }

    private static Field getDeclaredField(Object object, String filedName) {
        for (Class<?> superClass = object.getClass(); superClass != Object.class; superClass = superClass
                .getSuperclass()) {
            try {
                return superClass.getDeclaredField(filedName);
            } catch (NoSuchFieldException e) {
                // Field 不在当前类定义, 继续向上转型
            }
        }
        return null;
    }

    public static void setFieldValue(Object object, String fieldName, Object value) {
        Field field = getDeclaredField(object, fieldName);

        if (field == null)
            throw new IllegalArgumentException("Could not find field [" + fieldName + "] on target [" + object + "]");

        makeAccessible(field);

        try {
            field.set(object, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object object, String fieldName) {
        Field field = getDeclaredField(object, fieldName);
        if (field == null)
            throw new IllegalArgumentException("Could not find field [" + fieldName + "] on target [" + object + "]");

        makeAccessible(field);

        Object result;
        try {
            result = field.get(object);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }

        return (T) result;
    }
}
