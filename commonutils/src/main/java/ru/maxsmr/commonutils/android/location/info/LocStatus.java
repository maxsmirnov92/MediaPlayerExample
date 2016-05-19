package ru.maxsmr.commonutils.android.location.info;

public enum LocStatus {

    STOP_TRACKING(0),

    START_TRACKING(1),

    NEW_LOCATION(2);

    public final int value;

    LocStatus(int value) {
        this.value = value;
    }
}
