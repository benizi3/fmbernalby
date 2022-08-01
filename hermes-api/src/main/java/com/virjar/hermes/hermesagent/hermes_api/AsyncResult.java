package com.virjar.hermes.hermesagent.hermes_api;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.virjar.xposed_extention.ClassLoadMonitor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import de.robv.android.xposed.XposedHelpers;

/**
 * 如果返回值是这个类型，那么证明结果是异步的，我将会等待异步结果返回
 *
 * @author dengweijia
 * @since 1.4
 */
public class AsyncResult {
    private Object data;
    private boolean callbackCalled = false;
    private final Object lock = new Object();
    private Long waitTimeOut = null;

    public AsyncResult setWaitTimeOut(Long waitTimeOut) {
        this.waitTimeOut = waitTimeOut;
        return this;
    }

    void waitCallback(long timeOUt) {
        if (callbackCalled) {
            return;
        }
        synchronized (lock) {
            if (callbackCalled) {
                return;
            }
            if (waitTimeOut != null) {
                timeOUt = waitTimeOut;
            }
            try {
                lock.wait(timeOUt);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 异步返回，需要主动调用这个接口通知数据到达，唤醒http request线程，进行数据回传
     *
     * @param responseData 异步返回结果，可以是任何类型
     */
    public void notifyDataArrival(Object responseData) {
        Preconditions.checkArgument(!(responseData instanceof AsyncResult), "async response can not be a AsyncResult type");
        data = responseData;
        synchronized (lock) {
            callbackCalled = true;
            lock.notify();
        }
    }

    Object finalResult() {
        if (!callbackCalled) {
            return "timeOut";
        }
        return data;
    }

    boolean isCallbackCalled() {
        return callbackCalled;
    }

    /**
     * 方便链式
     */
    public static abstract class AsyncResultBuilder {
        private AsyncResult asyncResult = new AsyncResult();

        public abstract void bind(AsyncResult asyncResult);

        public AsyncResult build() {
            bind(asyncResult);
            return asyncResult;
        }
    }

    /**
     * 包裹一个rxJava对象，将它封装为一个异步返回对象。类似future
     *
     * @param rxJavaObserver RxJava的观察者对象,也可以是consumer对象
     * @return HeremsAPI定义的Future结构
     */
    public static AsyncResult wrapRxJava(Object rxJavaObserver) {
        return wrapRxJava(rxJavaObserver, "rx.Observer", "subscribe", null);
    }

    /**
     * 包裹一个rxJava对象，将它封装为一个异步返回对象。类似future
     *
     * @param rxJavaObserver  RxJava的观察者对象,也可以是consumer对象
     * @param dataTransformer 当数据异步返回的时候，有可能需要对数据进行编码转换。同步的时候可以直接转换，异步就只能通过回调拦截
     * @return HeremsAPI定义的Future结构
     */
    public static AsyncResult wrapRxJava(Object rxJavaObserver, Function<Object, Object> dataTransformer) {
        return wrapRxJava(rxJavaObserver, "rx.Observer", "subscribe", dataTransformer);
    }

    /**
     * 包裹一个rxJava对象，将它封装为一个异步返回对象。类似future
     *
     * @param rxJavaObserver      RxJava的观察者对象,也可以是consumer对象
     * @param observerClassName   在有混淆的情况下，消费者class名称可能发生变化
     * @param subscribeMethodName 在混淆的情况下，subscribe方法名字可能发生变化
     * @return HeremsAPI定义的Future结构
     */
    public static AsyncResult wrapRxJava(Object rxJavaObserver, @NonNull String observerClassName, @NonNull String subscribeMethodName) {
        return wrapRxJava(rxJavaObserver, observerClassName, subscribeMethodName, null);
    }


    /**
     * 包裹一个rxJava对象，将它封装为一个异步返回对象。类似future
     *
     * @param observerClassName   在有混淆的情况下，消费者class名称可能发生变化
     * @param subscribeMethodName 在混淆的情况下，subscribe方法名字可能发生变化
     * @param rxJavaObservable    RxJava的观察者对象,也可以是consumer对象
     * @param dataTransformer     当数据异步返回的时候，有可能需要对数据进行编码转换。同步的时候可以直接转换，异步就只能通过回调拦截
     * @return HeremsAPI定义的Future结构
     */
    public static AsyncResult wrapRxJava(final @NonNull Object rxJavaObservable, final @NonNull String observerClassName, final @NonNull String subscribeMethodName, final @Nullable Function<Object, Object> dataTransformer) {
        return new AsyncResult.AsyncResultBuilder() {

            @Override
            public void bind(final AsyncResult asyncResult) {
                Object observerOrConsumer = Proxy.newProxyInstance(rxJavaObservable.getClass().getClassLoader(), new Class[]{ClassLoadMonitor.tryLoadClass(observerClassName)},
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                if (method.getDeclaringClass() != ClassLoadMonitor.tryLoadClass(observerClassName)) {
                                    return method.invoke(this, args);
                                }
                                if (args == null) {
                                    return null;
                                }
                                if (args.length > 0) {
                                    Object data = args[0];
                                    if (dataTransformer != null) {
                                        data = dataTransformer.apply(data);
                                    }
                                    asyncResult.notifyDataArrival(data);
                                }
                                return null;
                            }
                        });
                Class<?> observerClass = ClassLoadMonitor.tryLoadClass(observerClassName);
                for (Method method : rxJavaObservable.getClass().getMethods()) {
                    if (!method.getName().equals(subscribeMethodName)) {
                        continue;
                    }
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes.length == 1 && parameterTypes[0].isAssignableFrom(observerClass)) {
                        //for observer
                        XposedHelpers.callMethod(rxJavaObservable, subscribeMethodName, observerOrConsumer);
                        return;
                    }
                    if (parameterTypes.length == 2 && parameterTypes[0].isAssignableFrom(observerClass)
                            && parameterTypes[1].isAssignableFrom(observerClass)) {
                        //for consumer
                        XposedHelpers.callMethod(rxJavaObservable, subscribeMethodName, observerOrConsumer, observerOrConsumer);
                        return;
                    }
                }
                throw new IllegalStateException("can not find suitable subscribe method for class：" + rxJavaObservable.getClass() + " methodName:" + subscribeMethodName + " param class:" + observerClassName);
            }
        }.build();
    }

    /**
     * 这是rxJava第二代版本的API
     *
     * @param object 一个observer
     * @return 转化为HermesApi自定义的Future对象
     */
    public static AsyncResult wrapRxJava2(final Object object) {
        return wrapRxJava(object, "io.reactivex.Observer", "subscribe", null);
    }

    /**
     * 这是rxJava第二代版本的API
     *
     * @param object 一个observer
     * @return 转化为HermesApi自定义的Future对象
     */
    public static AsyncResult wrapRxJava2(final Object object, Function<Object, Object> dataTransformer) {
        return wrapRxJava(object, "io.reactivex.Observer", "subscribe", dataTransformer);
    }

    /**
     * 对Retrofit框架的异步对象进行包装
     *
     * @param retrofitCall 一个代表Retrofit异步任务的对象
     * @return 异步调用后的结果
     */
    public static Object wrapRetrofit(Object retrofitCall) {
        Object response = XposedHelpers.callMethod(retrofitCall, "execute");
        if (response == null) {
            return null;
        }
        return XposedHelpers.callMethod(response, "body");
    }

    public static Object wrapOkHttp(Object okHttpCall) {
        Object responseBody = wrapRetrofit(okHttpCall);
        if (responseBody == null) {
            return null;
        }
        String response_str = (String) XposedHelpers.callMethod(responseBody, "string");
        if (response_str == null) {
            return null;
        }
        // try encoding
        try {
            return JSON.parse(response_str);
        } catch (JSONException exception) {
            //failed over for plain string
            return response_str;
        }
    }
}