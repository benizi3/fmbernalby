package com.virjar.hermes.hermesagent.hermes_api;

import android.util.Log;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.virjar.xposed_extention.ClassScanner;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import javax.annotation.Nullable;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 在注解模式下，创建多action wrapper，基于注解扫描方案
 *
 * @author dengweijia
 * @since 1.0.4
 */
public class MultiActionWrapperFactory {


    /**
     * hermes agent 1.8 以前的版本，无法自动感知注解wrapper，需要自己手动继承MultiActionWrapper，然后调用此方法，进行手动注册。
     * <pre>
     *     public class XXAppMulitiActionWrapper extends MultiActionWrapper {
     *          public XXAppMulitiActionWrapper() {
     *               //挂载XXAppMulitiActionWrapper.class 同级别路径和子路径的actionHandler
     *              MultiActionWrapperFactory.registerActionHandlers(this, XXAppMulitiActionWrapper.class.getPackage().getName());
     *          }
     *      }
     * </pre>
     *
     * @param multiActionWrapper multiActionWrapper基类实现
     * @param scanBasePackage    注册基于扫描机制，需要提供一个base路径
     */
    public static void registerActionHandlers(MultiActionWrapper multiActionWrapper, String scanBasePackage) {
        ArrayList<ActionRequestHandler> actionRequestHandlers = scanActionWrappers(scanBasePackage, bindApkLocation(multiActionWrapper.getClass().getClassLoader()), multiActionWrapper.getClass().getClassLoader());
        if (actionRequestHandlers.size() == 0) {
            throw new IllegalStateException("can not find action handler implement for base package: " + scanBasePackage);
        }
        for (ActionRequestHandler actionRequestHandler : actionRequestHandlers) {
            multiActionWrapper.registryHandler(resolveAction(actionRequestHandler), actionRequestHandler);
        }
    }


    public static ArrayList<ActionRequestHandler> scanActionWrappers(String packageName, File apkFilePath, ClassLoader classLoader) {
        ClassScanner.AnnotationClassVisitor annotationClassVisitor = new ClassScanner.AnnotationClassVisitor(WrapperAction.class);
        ClassScanner.scan(annotationClassVisitor, Sets.newHashSet(packageName), apkFilePath, classLoader);

        return Lists.newArrayList(Iterables.filter(Iterables.transform(Iterables.filter(annotationClassVisitor.getClassSet(), new Predicate<Class>() {
            @Override
            public boolean apply(@Nullable Class input) {
                if (input == null) {
                    return false;
                }
                if (Modifier.isAbstract(input.getModifiers()) || input.isInterface()) {
                    return false;
                }
                WrapperAction wrapperAction = (WrapperAction) input.getAnnotation(WrapperAction.class);
                return !wrapperAction.value().trim().isEmpty() && ActionRequestHandler.class.isAssignableFrom(input);
            }
        }), new Function<Class, ActionRequestHandler>() {
            @Nullable
            @Override
            public ActionRequestHandler apply(@Nullable Class input) {
                if (input == null) {
                    return null;
                }
                try {
                    return (ActionRequestHandler) input.newInstance();
                } catch (Exception e) {
                    Log.w("weijia", "failed to load create plugin", e);
                    return null;
                }
            }
        }), new Predicate<ActionRequestHandler>() {
            @Override
            public boolean apply(@Nullable ActionRequestHandler input) {
                return input != null;
            }
        }));
    }

    public static File bindApkLocation(ClassLoader pathClassLoader) {
        // 不能使用getResourceAsStream，这是因为classloader双亲委派的影响
//        InputStream stream = pathClassLoader.getResourceAsStream(ANDROID_MANIFEST_FILENAME);
//        if (stream == null) {
//            XposedBridge.log("can not find AndroidManifest.xml in classloader");
//            return null;
//        }

        // we can`t call package parser in android inner api,parse logic implemented with native code
        //this object is dalvik.system.DexPathList,android inner api
        Object pathList = XposedHelpers.getObjectField(pathClassLoader, "pathList");
        if (pathList == null) {
            XposedBridge.log("can not find pathList in pathClassLoader");
            return null;
        }

        //this object is  dalvik.system.DexPathList.Element[]
        Object[] dexElements = (Object[]) XposedHelpers.getObjectField(pathList, "dexElements");
        if (dexElements == null || dexElements.length == 0) {
            XposedBridge.log("can not find dexElements in pathList");
            return null;
        }

        return (File) XposedHelpers.getObjectField(dexElements[0], "zip");
        // Object dexElement = dexElements[0];

        // /data/app/com.virjar.xposedhooktool/base.apk
        // /data/app/com.virjar.xposedhooktool-1/base.apk
        // /data/app/com.virjar.xposedhooktool-2/base.apk
        // return (File) XposedHelpers.getObjectField(dexElement, "zip");
    }

    public static MultiActionWrapper createWrapperByAction(String packageName, ClassLoader loader) {
        return createWrapperByAction(packageName, bindApkLocation(loader));
    }

    public static String resolveAction(ActionRequestHandler actionRequestHandler) {
        return actionRequestHandler.getClass().getAnnotation(WrapperAction.class).value();
    }


    public static MultiActionWrapper createWrapperByAction(String packageName, File apkFilePath) {
        ArrayList<ActionRequestHandler> actionRequestHandlers = scanActionWrappers(packageName, apkFilePath, null);
        if (actionRequestHandlers.size() == 0) {
            return null;
        }
        MultiActionWrapper multiActionWrapper = new MultiActionWrapper();
        for (ActionRequestHandler actionRequestHandler : actionRequestHandlers) {
            multiActionWrapper.registryHandler(resolveAction(actionRequestHandler), actionRequestHandler);
        }
        return multiActionWrapper;
    }
}
