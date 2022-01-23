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
    private Long id;

    @Getter
    @Setter
    @Column
    private Integer status;

    @Getter
    @Setter
    @Column
    private String appPackage;

    @Getter
    @Setter
    @Column
    private String deviceMac;

    @Getter
    @Setter
    @Column
    private String sourcePath;

}
