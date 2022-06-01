package com.virjar.hermes.hermesagent.host.orm;

import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by virjar on 2018/9/7.
 */
@Table(database = ServiceDataBase.class)
public class ServiceModel extends BaseModel {

    @Getter
    @Setter
    @PrimaryKey
    private Long serviceId;


    @Column
    private boolean status;

    // status lombok生成存在错误，需要使用is，而不是get
    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    @Getter
    @Setter
    @Column
    private String deviceMac;


    @Getter
    @Setter
    @Column
    private String targetAppPackage;


    @Getter
    @Setter
    @Column
    private Long targetAppVersionCode;

    @Getter
    @Setter
    @Column
    private String targetAppDownloadUrl;

    @Getter
    @Setter
    @Column
    private String wrapperPackage;

    @Getter
    @Setter
    @Column
    private Long wrapperVersionCode;

    @Getter
    @Setter
    @Column
    private String wrapperAppDownloadUrl;

    @Getter
    @Setter
    @Column
    private String sourcePath;

}
