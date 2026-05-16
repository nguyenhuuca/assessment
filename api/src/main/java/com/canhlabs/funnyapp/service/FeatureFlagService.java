package com.canhlabs.funnyapp.service;

public interface FeatureFlagService {
    boolean isEnabled(String flag, boolean defaultValue);
}
