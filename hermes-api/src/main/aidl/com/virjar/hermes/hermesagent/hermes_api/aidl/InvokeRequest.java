package com.virjar.hermes.hermesagent.hermes_api.aidl;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.virjar.hermes.hermesagent.hermes_api.APICommonUtils;
import com.virjar.hermes.hermesagent.hermes_api.Multimap;

import org.apache.commons.io.IOUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

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
        if (paramContent.length() < 4096 || context == null) {
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

    private Multimap nameValuePairsModel;
    private JSONObject jsonModel;

    public String getString(String name) {
        return getString(name, null);
    }

    public String getString(String name, String defaultValue) {
        initInnerModel();
        if (nameValuePairsModel != null) {
            String ret = nameValuePairsModel.getString(name);
            return ret == null ? defaultValue : ret;
        }
        if (jsonModel != null) {
            String ret = jsonModel.getString(name);
            return ret == null ? defaultValue : ret;
        }
        throw new IllegalStateException("parameter parse failed");
    }

    public int getInt(String name) {
        return getInt(name, 0);
    }

    public int getInt(String name, int defaultValue) {
        initInnerModel();
        if (nameValuePairsModel != null) {
            String value = nameValuePairsModel.getString(name);
            if (value == null || value.trim().isEmpty()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        if (jsonModel != null) {
            try {
                Integer value = jsonModel.getInteger(name);
                if (value == null) {
                    return defaultValue;
                }
                return value;
            } catch (JSONException e) {
                return defaultValue;
            }
        }
        throw new IllegalStateException("parameter parse failed");
    }

    public List<String> getValues(String name) {
        initInnerModel();
        if (nameValuePairsModel != null) {
            return nameValuePairsModel.get(name);
        }
        if (jsonModel != null) {
            Object o = jsonModel.get(name);
            if (o instanceof CharSequence) {
                return Lists.newArrayList(o.toString());
            } else if (o instanceof JSONArray) {
                return Lists.newArrayList(Iterables.filter(Iterables.transform((JSONArray) o, new Function<Object, String>() {
                    @Nullable
                    @Override
                    public String apply(@Nullable Object input) {
                        if (input instanceof CharSequence) {
                            return input.toString();
                        }
                        return null;
                    }
                }), new Predicate<String>() {
                    @Override
                    public boolean apply(@Nullable String input) {
                        return input != null;
                    }
                }));
            }
            return Lists.newArrayList(jsonModel.getString(name));
        }
        throw new IllegalStateException("parameter parse failed");
    }

    public boolean hasParam(String name) {
        initInnerModel();
        if (nameValuePairsModel != null) {
            return nameValuePairsModel.containsKey(name);
        }
        if (jsonModel != null) {
            return jsonModel.containsKey(name);
        }
        throw new IllegalStateException("parameter parse failed");
    }

    public JSONObject getJsonParam() {
        initInnerModel();
        return jsonModel;
    }

    public String jsonPath(String jsonPath) {
        initInnerModel();
        if (jsonModel == null) {
            throw new IllegalStateException("param not  json format");
        }
        return APICommonUtils.safeToString(JSONPath.compile(jsonPath).eval(jsonModel));
    }

    private void initInnerModel() {
        if (nameValuePairsModel != null || jsonModel != null) {
            return;
        }
        synchronized (this) {
            if (nameValuePairsModel != null || jsonModel != null) {
                return;
            }
            String paramContent = getParamContent();
            if (paramContent == null) {
                throw new IllegalArgumentException("invoke request can not be empty");
            }
            paramContent = paramContent.trim();
            if (paramContent.startsWith("{")) {
                try {
                    jsonModel = JSONObject.parseObject(paramContent);
                } catch (JSONException e) {
                    //ignore
                }
            }
            if (jsonModel != null) {
                return;
            }
            nameValuePairsModel = Multimap.parseUrlEncoded(paramContent);
        }
    }
}
