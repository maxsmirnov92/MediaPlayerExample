package ru.altarix.commonutils.android.notification;

import android.app.PendingIntent;
import android.content.Intent;

/**
 * Created by maxsmirnov on 10.09.15.
 */
public class NotificationActionInfo {

    public enum NotificationAction {
        NONE, ACTIVITY, SERVICE, BROADCAST;
    }

    public final NotificationAction action;

    public final int iconResId;

    public final String title;

    public final Intent actionIntent;

    public final int pIntentFlag;

    public NotificationActionInfo(NotificationAction action, int iconResId, String title, Intent actionIntent, int pIntentFlag) {
        this.action = action == null ? NotificationAction.NONE : action;
        this.iconResId = iconResId;
        this.title = title;
        this.actionIntent = actionIntent;
        this.pIntentFlag = (pIntentFlag == PendingIntent.FLAG_CANCEL_CURRENT || pIntentFlag == PendingIntent.FLAG_NO_CREATE || pIntentFlag == PendingIntent.FLAG_ONE_SHOT || pIntentFlag == PendingIntent.FLAG_UPDATE_CURRENT || pIntentFlag == 0) ? pIntentFlag : 0;
    }

    @Override
    public String toString() {
        return "NotificationActionInfo{" +
                "action=" + action +
                ", iconResId=" + iconResId +
                ", title='" + title + '\'' +
                ", actionIntent=" + actionIntent +
                ", pIntentFlag=" + pIntentFlag +
                '}';
    }
}
