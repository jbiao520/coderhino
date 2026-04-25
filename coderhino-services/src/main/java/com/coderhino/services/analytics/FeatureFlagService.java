package com.coderhino.services.analytics;

import java.util.Map;
import java.util.Set;

public interface FeatureFlagService {
    boolean isEnabled(String flagName);

    boolean isEnabled(String flagName, boolean defaultValue);

    String getString(String flagName, String defaultValue);

    Set<String> flagNames();

    Map<String, Object> snapshot();
}