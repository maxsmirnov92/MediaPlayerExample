package ru.altarix.commonutils.android.notification;

import android.content.Intent;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;

/**
 * Created by maxsmirnov on 10.09.15.
 */
public class NotificationInfo {

    public int id;

    public boolean autoCancel;

    public boolean onlyUpdate;

    public boolean ongoing;

    @Nullable
    public Intent contentIntent;

    @Nullable
    public NotificationActionInfo actionInfo;

    @DrawableRes
    public int iconResId;

    @Nullable
    public String tickerText;

    @Nullable
    public String contentTitle;

    @Nullable
    public String text;

    @Nullable
    public String subText;

    public int progress;


    @Override
    public String toString() {
        return "NotificationInfo{" +
                "id=" + id +
                ", autoCancel=" + autoCancel +
                ", onlyUpdate=" + onlyUpdate +
                ", ongoing=" + ongoing +
                ", contentIntent=" + contentIntent +
                ", iconResId=" + iconResId +
                ", tickerText='" + tickerText + '\'' +
                ", contentTitle='" + contentTitle + '\'' +
                ", text='" + text + '\'' +
                ", subText='" + subText + '\'' +
                ", progress=" + progress +
                ", actionInfo=" + actionInfo +
                '}';
    }
}
