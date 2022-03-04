package com.virjar.hermes.hermesagent.plugin.interceptor;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.HermesCommonConfig;
import com.virjar.hermes.hermesagent.hermes_api.Ones;
import com.virjar.hermes.hermesagent.hermes_api.SingletonXC_MethodHook;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeRequest;
import com.virjar.hermes.hermesagent.hermes_api.aidl.InvokeResult;
import com.virjar.hermes.hermesagent.plugin.InvokeInterceptor;
import com.virjar.hermes.hermesagent.util.CommonUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

/**
 * Created by virjar on 2018/9/27.<br>
 * 对地理位置进行mock
 */
@SuppressWarnings("unused")
public class LocationMockInterceptor implements InvokeInterceptor {

    private static final String latitudeKey = "__hermes_Latitude";
    private static final String longitudeKey = "__hermes_Longitude";

    @Override
    public InvokeResult intercept(InvokeRequest invokeRequest) {
        if (!CommonUtils.configChange(latitudeKey, invokeRequest)
                && !CommonUtils.configChange(longitudeKey, invokeRequest)) {
            return null;
        }
        String latitude = invokeRequest.getString(latitudeKey);
        String longitude = invokeRequest.getString(longitudeKey);
        try {
            double latitudeDouble = Double.parseDouble(latitude);
            double longitudeDouble = Double.parseDouble(longitude);
            if (latitudeDouble < -90D || latitudeDouble > 90D) {
                return InvokeResult.failed("param format error,latitude must between -90,90  ");
            }
            if (longitudeDouble < -180 || longitudeDouble > 180) {
                return InvokeResult.failed("param format error,longitude must between -180,180  ");
            }
        } catch (NumberFormatException e) {
            return InvokeResult.failed("param format error,latitude & latitude must be double  ");
        }

        HermesCommonConfig.putString(latitudeKey, latitude);
        HermesCommonConfig.putString(longitudeKey, longitude);
        setup();
        return null;
    }

    @Override
    public void setup() {
        //从app刚开始运行的时候，就收集所有的回调函数，这样未来我们动态设计gps配置的时候，可以找到所有的回调，并且callback one by one
        Ones.hookOnes(LocationManager.class, "collect_location_listener", new Ones.DoOnce() {
            @Override
            public void doOne(Class<?> clazz) {
                XposedHelpers.findAndHookMethod(LocationManager.class, "wrapListener", LocationListener.class, Looper.class, new SingletonXC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        LocationListener locationListener = (LocationListener) param.args[0];
                        if (locationListener == null) {
                            return;
                        }
                        if (locationListener instanceof FakeLocationListener) {
                            return;
                        }
                        locationListenerSet.add(locationListener);
                        param.args[0] = new FakeLocationListener(locationListener);
                    }
                });
            }
        });

        final String latitude = HermesCommonConfig.getString(latitudeKey);
        final String longitude = HermesCommonConfig.getString(longitudeKey);
        if (StringUtils.isBlank(latitude)
                || StringUtils.isBlank(longitude)) {
            return;
        }
        Ones.hookOnes(Location.class, "mock_location", new Ones.DoOnce() {
            @Override
            public void doOne(Class<?> clazz) {
                locationHook();
                hookLocationManager();
            }
        });
        mockEnabled = true;
        fireLocationEvent();
    }

    private static boolean mockEnabled = false;

    private static void fireLocationEvent() {
        Location location = fakeLocation("gps");
        if (location == null) {
            return;
        }
        for (LocationListener locationListener : locationListenerSet) {
            try {
                locationListener.onLocationChanged(location);
            } catch (Throwable throwable) {
                Log.w("weijia", throwable);
            }
        }
    }


    private static Location fakeLocation(String provider) {
        final String latitude = HermesCommonConfig.getString(latitudeKey);
        final String longitude = HermesCommonConfig.getString(longitudeKey);
        if (StringUtils.isBlank(latitude)
                || StringUtils.isBlank(longitude)) {
            return null;
        }
        if (provider == null) {
            provider = "gps";
        }
        Location location = new Location(provider);
        location.setLatitude(Double.parseDouble(latitude));
        location.setLongitude(Double.parseDouble(longitude));
        location.setAccuracy(100.0F);
        location.setTime(System.currentTimeMillis());
        location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        return location;
    }

    private static Set<LocationListener> locationListenerSet = Sets.newConcurrentHashSet();

    private static class FakeLocationListener implements LocationListener {
        private LocationListener delegate;

        FakeLocationListener(LocationListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onLocationChanged(Location location) {
            if (!mockEnabled) {
                delegate.onLocationChanged(location);
                return;
            }
            Location fakeLocation = fakeLocation(location.getProvider());
            if (fakeLocation == null) {
                fakeLocation = location;
            }
            delegate.onLocationChanged(fakeLocation);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (!mockEnabled) {
                delegate.onStatusChanged(provider, status, extras);
                return;
            }
            if (!StringUtils.equalsIgnoreCase(provider, "gps")) {
                return;
            }
            delegate.onStatusChanged(provider, status, extras);
        }

        @Override
        public void onProviderEnabled(String provider) {
            if (!mockEnabled) {
                delegate.onProviderEnabled(provider);
                return;
            }
            delegate.onProviderEnabled("gps");
        }

        @Override
        public void onProviderDisabled(String provider) {
            if (!mockEnabled) {
                delegate.onProviderDisabled(provider);
                return;
            }
            if (!StringUtils.equalsIgnoreCase(provider, "gps")) {
                delegate.onProviderDisabled(provider);
            }
        }
    }


    private static void hookLocationManager() {

        XposedHelpers.findAndHookMethod(LocationManager.class, "requestLocationUpdates", "android.location.LocationRequest", LocationListener.class, Looper.class, PendingIntent.class, new SingletonXC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                LocationListener listener = (LocationListener) param.args[1];
                if (listener == null) {
                    return;
                }
                Location location = fakeLocation("gps");
                if (location == null) {
                    return;
                }
                listener.onLocationChanged(location);
            }
        });

        XposedHelpers.findAndHookMethod(LocationManager.class, "getLastLocation", new SingletonXC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Location location = fakeLocation("gps");
                if (location != null) {
                    param.setResult(location);
                }
            }
        });

        XposedHelpers.findAndHookMethod(LocationManager.class, "getLastKnownLocation", String.class, new SingletonXC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String provider = (String) param.args[0];
                Location location = fakeLocation(provider);
                if (location != null) {
                    param.setResult(location);
                }
            }
        });

        //这个含义暂时还不明确
        XposedHelpers.findAndHookMethod(LocationManager.class, "addNmeaListener", GpsStatus.NmeaListener.class, new SingletonXC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(false);
            }
        });


        SingletonXC_MethodHook gps = new SingletonXC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (param.method.getName().equalsIgnoreCase("getBestProvider")) {
                    param.setResult("gps");
                    return;
                }
                List<String> result = (List<String>) param.getResult();
                if (result == null) {
                    result = Lists.newArrayList();
                    param.setResult(result);
                }
                if (result.contains("gps")) {
                    return;
                }
                result.add("gps");

            }
        };


        XposedHelpers.findAndHookMethod(LocationManager.class, "getBestProvider", Criteria.class, boolean.class, gps);
        XposedHelpers.findAndHookMethod(LocationManager.class, "getProviders", Criteria.class, boolean.class, gps);
        XposedHelpers.findAndHookMethod(LocationManager.class, "getProviders", boolean.class, gps);

        XposedHelpers.findAndHookMethod(LocationManager.class, "isProviderEnabled", String.class, new SingletonXC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (StringUtils.equalsIgnoreCase(APICommonUtils.safeToString(param.args[0]), "gps")) {
                    param.setResult(true);
                }
            }
        });
    }


    private static void locationHook() {
        findAndHookMethod(Location.class, "hasAccuracy", XC_MethodReplacement.returnConstant(true));
        findAndHookMethod(Location.class, "hasAltitude", XC_MethodReplacement.returnConstant(true));
        findAndHookMethod(Location.class, "hasBearing", XC_MethodReplacement.returnConstant(true));
        findAndHookMethod(Location.class, "hasSpeed", XC_MethodReplacement.returnConstant(true));
        findAndHookMethod(Location.class, "getExtras", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Bundle bundle = (Bundle) param.getResult();
                if (bundle == null) {
                    bundle = new Bundle();
                }
                bundle.putInt("satellites", 12);
                param.setResult(bundle);
            }
        });

        findAndHookMethod(Location.class, "getLatitude", new SingletonXC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(Double.parseDouble(HermesCommonConfig.getString(latitudeKey)));
            }
        });
        findAndHookMethod(Location.class, "getLongitude", new SingletonXC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(Double.parseDouble(HermesCommonConfig.getString(longitudeKey)));
            }
        });
        findAndHookMethod(Location.class, "getSpeed", XC_MethodReplacement.returnConstant(5.0f));
        findAndHookMethod(Location.class, "getAccuracy", XC_MethodReplacement.returnConstant(50.0f));
        findAndHookMethod(Location.class, "getBearing", XC_MethodReplacement.returnConstant(50.0f));
        findAndHookMethod(Location.class, "getAltitude", XC_MethodReplacement.returnConstant(50.0d));
        findAndHookMethod(Location.class, "getTimeToFirstFix", XC_MethodReplacement.returnConstant(1080));

        findAndHookMethod(android.provider.Settings.Secure.class, "getString", ContentResolver.class, String.class, new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param)
                    throws Throwable {
                if ("mock_location".equals(param.args[1])) {
                    param.setResult("0");
                }
            }
        });

        findAndHookMethod(Location.class, "isFromMockProvider", new XC_MethodHook() {
            protected void beforeHookedMethod(MethodHookParam param)
                    throws Throwable {
                param.setResult(Boolean.FALSE);
            }
        });
    }
}
