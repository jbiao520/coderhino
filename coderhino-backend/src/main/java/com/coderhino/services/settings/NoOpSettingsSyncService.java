package com.coderhino.services.settings;

public final class NoOpSettingsSyncService implements SettingsSyncService {

    @Override
    public void sync() {
    }

    @Override
    public Object getRemoteSetting(String key) {
        return null;
    }

    @Override
    public void setLocalSetting(String key, Object value) {
    }

    @Override
    public boolean isSynced() {
        return false;
    }
}
