package com.virjar.hermes.hermesagent;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.google.common.base.Joiner;
import com.virjar.hermes.hermesagent.hermes_api.Constant;
import com.virjar.hermes.hermesagent.hermes_api.aidl.IServiceRegister;
import com.virjar.hermes.hermesagent.host.service.FontService;
import com.virjar.hermes.hermesagent.util.CommonUtils;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private IServiceRegister mService = null;

    private ServiceConnection fontServiceConnection = new ServiceConnection() {


        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IServiceRegister.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

//    // Used to load the 'native-lib' library on application startup.
//    static {
//        System.loadLibrary("native-lib");
//    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent intent = new Intent(this, FontService.class);
        startService(intent);

        intent = new Intent();
        intent.setAction(Constant.serviceRegisterAction);
        intent.setPackage(BuildConfig.APPLICATION_ID);
        bindService(intent, fontServiceConnection, Context.BIND_AUTO_CREATE);
        Timer timer = new Timer("refreshUI", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateStatus();
            }
        }, 500, 2000);

        new Thread() {
            @Override
            public void run() {
                try {
                    CommonUtils.enableADBTCPProtocol(MainActivity.this);
                } catch (Exception e) {
                    Log.e("weijia", "enable adb remote exception", e);
                }
            }
        }.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent intent = new Intent();
        intent.setAction(Constant.serviceRegisterAction);
        intent.setPackage(BuildConfig.APPLICATION_ID);
        this.unbindService(fontServiceConnection);
    }


    public void updateStatus() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv = findViewById(R.id.sample_text);
                String text;
                if (CommonUtils.xposedStartSuccess) {
                    text = "???????????????" + CommonUtils.localServerBaseURL();
                    if (mService != null) {
                        try {
                            text += "\n\n??????????????????:\n"
                                    + Joiner.on("\n").join(mService.onlineService());
                            text += "\n\n?????????????????????" + (int) (mService.systemScore() * 100);
                        } catch (RemoteException e) {
                            text += "????????????????????????";
                        }


                    }
                } else {
                    text = "xposed?????????????????????HermesAgent??????";
                }
                if (!CommonUtils.isSuAvailable() && !CommonUtils.requestSuPermission()) {
                    text += "\n  HermesAgent??????root??????????????????HermesAgent???root??????";
                }
                text += "\n\nAndroidId: " + CommonUtils.deviceID(MainActivity.this);
                text += "\n\nagent?????????" + BuildConfig.VERSION_CODE;
                if (CommonUtils.isLocalTest()) {
                    text += "\n\n??????????????????";
                }
                tv.setText(text);
            }
        });
    }


//
//    /**
//     * A native method that is implemented by the 'native-lib' native library,
//     * which is packaged with this application.
//     */
//    public native String stringFromJNI();
}
