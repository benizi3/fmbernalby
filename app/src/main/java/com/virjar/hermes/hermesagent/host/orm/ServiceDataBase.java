package com.virjar.hermes.hermesagent.host.orm;

import com.raizlabs.android.dbflow.annotation.Database;

/**
 * Created by virjar on 2018/9/7.
 */
@Database(name = ServiceDataBase.NAME, version = ServiceDataBase.VERSION)
public class ServiceDataBase {
    public static final String NAME = "db_hermes_service";
    public static final int VERSION = 1;
}
