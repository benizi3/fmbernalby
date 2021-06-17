package com.virjar.hermes.hermesagent.aidl;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by virjar on 2018/8/22.<br>
 * 一个客户端上报的信息
 */

public class AgentInfo implements Parcelable {
    private String packageName;
    private String serviceAlis;

    protected AgentInfo(Parcel in) {
        packageName = in.readString();
        serviceAlis = in.readString();
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
    }
}
