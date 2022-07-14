package com.virjar.hermes.hermesagent.hermes_api.aidl;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by virjar on 2018/8/22.<br>
 * 一个客户端上报的信息
 */

public class AgentInfo implements Parcelable {
    private String packageName;
    private String serviceAlis;
    private long versionCode = -1;

    protected AgentInfo(Parcel in) {
        packageName = in.readString();
        serviceAlis = in.readString();
        versionCode = in.readLong();
    }

    public AgentInfo(String packageName, String serviceAlis) {
        this.packageName = packageName;
        this.serviceAlis = serviceAlis;
    }

    public static final Creator<AgentInfo> CREATOR = new Creator<AgentInfo>() {
        @Override
        public AgentInfo createFromParcel(Parcel in) {
            return new AgentInfo(in);
        }

        @Override
        public AgentInfo[] newArray(int size) {
            return new AgentInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeString(serviceAlis);
        dest.writeLong(versionCode);
    }

    public String getPackageName() {
        return packageName;
    }

    public String getServiceAlis() {
        return serviceAlis;
    }

    public void setVersionCode(long versionCode) {
        this.versionCode = versionCode;
    }

    public long getVersionCode() {
        return versionCode;
    }
}
