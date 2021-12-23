package com.virjar.hermes.hermesagent.hermes_api.aidl;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;

import org.apache.commons.io.IOUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created by virjar on 2018/8/23.
 */

public class InvokeRequest implements Parcelable {
    private String paramContent;
    private boolean useFile;
    private String requestID;

    private static final String TAG = "BinderRPC";

    protected InvokeRequest(Parcel in) {
        paramContent = in.readString();
        useFile = in.readByte() != 0;
        requestID = in.readString();
    }

    public InvokeRequest(String paramContent, Context context, String requestID) {
        this.requestID = requestID;
        if (paramContent.length() < 4096) {
            this.paramContent = paramContent;
        } else {
            File file = APICommonUtils.genTempFile(context);
            try {
                BufferedWriter bufferedWriter = Files.newWriter(file, Charsets.UTF_8);
                bufferedWriter.write(paramContent);
                bufferedWriter.close();
                this.useFile = true;
                this.paramContent = file.getAbsolutePath();
            } catch (IOException e) {
                throw new IllegalStateException("failed to write a temp file " + file.getAbsolutePath(), e);
            }
        }
    }

    public String getRequestID() {
        return requestID;
    }

    public String getParamContent() {
        return getParamContent(true);
    }

    public String getParamContent(boolean changeState) {
        if (!useFile) {
            return paramContent;
        }
        File file = new File(paramContent);
        if (!file.exists() || !file.canRead() || !file.isFile()) {
            throw new IllegalStateException("target file " + file.getAbsolutePath() + " can not be accessed");
        }
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            String ret = IOUtils.toString(fileInputStream, Charsets.UTF_8);
            fileInputStream.close();
            if (changeState) {
                paramContent = ret;
                useFile = false;
                if (!file.delete()) {
                    Log.w(TAG, "delete binder file failed:" + file.getAbsolutePath());
                }
            }
            return ret;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final Creator<InvokeRequest> CREATOR = new Creator<InvokeRequest>() {
        @Override
        public InvokeRequest createFromParcel(Parcel in) {
            return new InvokeRequest(in);
        }

        @Override
        public InvokeRequest[] newArray(int size) {
            return new InvokeRequest[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(paramContent);
        dest.writeByte((byte) (useFile ? 1 : 0));
        dest.writeString(requestID);
    }

    public void readFromParcel(Parcel reply) {
        paramContent = reply.readString();
        useFile = reply.readByte() != 0;
        requestID = reply.readString();
    }
}
