package ru.altarix.commonutils.android.preferences;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public interface PreferenceChangeListener {
    <T> void onPreferenceChanged(@NonNull String key, @Nullable T oldValue, @Nullable T newValue);
}
