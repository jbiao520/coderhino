package com.coderhino.services.settings;

public interface SettingsSyncService {

    void sync();

    Object getRemoteSetting(String key);

    void setLocalSetting(String key, Object value);

    boolean isSynced();
}
