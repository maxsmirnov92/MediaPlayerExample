package ru.maxsmr.commonutils.android.preferences;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

public class PreferencesManager {

    private static final Logger logger = LoggerFactory.getLogger(PreferencesManager.class);

    @NonNull
    private Context context;

    private SharedPreferences preferences;

    public PreferencesManager(@NonNull Context ctx, @Nullable String preferencesName) {
        context = ctx;
        init(preferencesName);
    }

    private void init(String name) {
        preferences = !TextUtils.isEmpty(name) ? context.getSharedPreferences(name, Context.MODE_WORLD_READABLE) : android.preference.PreferenceManager.getDefaultSharedPreferences(context);
    }

    private final LinkedList<PreferenceChangeListener> changeListeners = new LinkedList<>();

    public void addPreferenceChangeListener(PreferenceChangeListener listener) throws NullPointerException {

        if (listener == null) {
            throw new NullPointerException("listener is null");
        }

        if (!changeListeners.contains(listener)) {
            synchronized (changeListeners) {
                changeListeners.add(listener);
            }
        }
    }

    public void removePreferenceChangeListener(PreferenceChangeListener listener) {
        if (changeListeners.contains(listener)) {
            synchronized (changeListeners) {
                changeListeners.remove(listener);
            }
        }
    }

    public synchronized <V> V getValue(@NonNull String key, @NonNull Class<V> clazz) {
        return getValue(key, clazz, null);
    }

    @SuppressWarnings("unchecked")
    public synchronized <V> V getValue(@NonNull String key, @NonNull Class<V> clazz, @Nullable V defaultValue) {
        logger.debug("getValue(), key=" + key + ", clazz=" + clazz);
        try {
            if (clazz.isAssignableFrom(String.class)) {
                return (V) preferences.getString(key, (String) defaultValue);
            } else if (clazz.isAssignableFrom(Integer.class)) {
                return (V) Integer.valueOf(preferences.getInt(key, defaultValue != null ? (Integer) defaultValue : 0));
            } else if (clazz.isAssignableFrom(Long.class)) {
                return (V) Long.valueOf(preferences.getLong(key, defaultValue != null ? (Long) defaultValue : 0));
            } else if (clazz.isAssignableFrom(Float.class)) {
                return (V) Float.valueOf(preferences.getFloat(key, defaultValue != null ? (Float) defaultValue : 0f));
            } else if (clazz.isAssignableFrom(Boolean.class)) {
                return (V) Boolean.valueOf(preferences.getBoolean(key, defaultValue != null ? (Boolean) defaultValue : false));
            } else {
                throw new UnsupportedOperationException("incorrect class: " + clazz);
            }
        } catch (ClassCastException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @SuppressLint("CommitPrefEdits")
    /**
     * @return true if successfully committed
     */
    public synchronized <V> boolean setValue(@NonNull String key, @Nullable V value) {
        logger.debug("setValue(), key=" + key + ", value=" + value);
        final V oldValue = value != null ? getValue(key, (Class<V>) value.getClass(), null) : null;
        final SharedPreferences.Editor editor = preferences.edit();
        try {
            if (value != null) {
                if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(key, (Float) value);
                } else if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else {
                    throw new UnsupportedOperationException("incorrect value type: " + value.getClass());
                }
            } else {
                editor.putString(key, null);
            }
        } catch (ClassCastException e) {
            e.printStackTrace();
        }
        if (editor.commit()) {

            if (value != null ? !value.equals(oldValue) : oldValue != null) {
                synchronized (changeListeners) {
                    for (PreferenceChangeListener l : changeListeners) {
                        l.onPreferenceChanged(key, oldValue, value);
                    }
                }
            }

            return true;
        }

        return false;
    }

    /**
     * @return true if successfully committed
     */
    @SuppressLint("CommitPrefEdits")
    public synchronized boolean clearValues() {
        logger.debug("clearValues()");
        final SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
       return editor.commit();
    }

}
