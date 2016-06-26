package ru.maxsmr.commonutils.android.notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * Created by maxsmirnov on 10.09.15.
 */
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    private static NotificationController instance;

    public static void initInstance(Context ctx) {
        if (instance == null) {
            synchronized (NotificationController.class) {
                instance = new NotificationController(ctx);
            }
        }
    }

    public static NotificationController getInstance() {
        if (instance == null) {
            throw new IllegalStateException("initInstance() was not called");
        }
        return instance;
    }

    @NonNull
    private Context context;

    public NotificationController(@NonNull Context ctx) {
        context = ctx;
    }

    private final Map<Integer, NotificationCompat.Builder> notis = new HashMap<>();

    public boolean isNotificationExist(int id) {
        return notis.containsKey(id);
    }

    private void addToNotiList(int id, @NonNull NotificationCompat.Builder builder) {

        if (id < 0)
            throw new IllegalArgumentException("incorrect id: " + id);

        synchronized (notis) {
            if (!notis.containsKey(id))
                notis.put(id, builder);
        }
    }

    private void removeFromNotiIdsList(int id) {
        synchronized (notis) {
            if (notis.containsKey(id))
                notis.remove(id);
        }
    }


    public static Intent makeIntent(Context ctx, Class<?> cls, String action, Uri data, int flags, Bundle extras) {

        if (ctx == null && cls == null && (action == null || action.isEmpty()))
            return null;

        Intent notiIntent = (ctx != null && cls != null) ? new Intent(ctx, cls) : new Intent(action);

        if (action != null && !action.isEmpty())
            notiIntent.setAction(action);

        if (data != null)
            notiIntent.setData(data);

        notiIntent.addCategory(Intent.CATEGORY_DEFAULT);

        if (flags >= 0)
            notiIntent.addFlags(flags);

        notiIntent.putExtras(extras != null ? extras : new Bundle());

        return notiIntent;
    }


    public synchronized Notification createAndAddNotification(NotificationInfo info) {

        if (info == null)
            throw new NullPointerException("info is null");

        if (info.id < 0)
            throw new IllegalArgumentException("incorrect notification id: " + info.id);

        final NotificationCompat.Builder notificationBuilder = isNotificationExist(info.id) ? notis.get(info.id)
                : new NotificationCompat.Builder(context);

        notificationBuilder.setAutoCancel(info.autoCancel);

        notificationBuilder.setTicker(null);

        if (!info.onlyUpdate) {

            notificationBuilder.setOngoing(info.ongoing).setOnlyAlertOnce(info.ongoing);

            if (info.tickerText != null)
                notificationBuilder.setTicker(info.tickerText);

            notificationBuilder.setWhen(System.currentTimeMillis());
        }

        notificationBuilder.setSmallIcon(0);

        if (info.iconResId > 0) {
            try {
                ContextCompat.getDrawable(context, info.iconResId);
                notificationBuilder.setSmallIcon(info.iconResId);
            } catch (Resources.NotFoundException e) {
                logger.error("a Resources.NotFoundException occurred during getDrawable()", e);
                return null;
            }
        }

        notificationBuilder.setContentIntent(null);

        if (info.contentIntent != null)
            notificationBuilder.setContentIntent(PendingIntent.getActivity(context, 0, info.contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT));

        Field[] fields = notificationBuilder.getClass().getDeclaredFields();

        for (Field f : fields) {
            if (f.getName().equals("mActions")) {
                f.setAccessible(true);
                try {
                    ArrayList<NotificationCompat.Action> actions = (ArrayList<NotificationCompat.Action>) f.get(notificationBuilder);
                    if (actions != null) {
                        actions.clear();
                    }
                } catch (IllegalAccessException e) {
                    logger.error("an IllegalException occurred during get()", e);
                } catch (ClassCastException e) {
                    logger.error("a ClassCastException occurred", e);
                }
            }
        }

        if (info.actionInfos != null) {

            for (NotificationActionInfo actionInfo : info.actionInfos) {

                if (actionInfo.action != NotificationActionInfo.NotificationAction.NONE) {

                    final PendingIntent pIntent;

                    switch (actionInfo.action) {
                        case ACTIVITY:
                            pIntent = PendingIntent.getActivity(context, actionInfo.requestCode, actionInfo.actionIntent, actionInfo.pIntentFlag);
                            break;

                        case SERVICE:
                            pIntent = PendingIntent.getService(context, actionInfo.requestCode, actionInfo.actionIntent, actionInfo.pIntentFlag);
                            break;

                        case BROADCAST:
                            pIntent = PendingIntent.getBroadcast(context, actionInfo.requestCode, actionInfo.actionIntent, actionInfo.pIntentFlag);
                            break;

                        default:
                            pIntent = null;
                    }

                    notificationBuilder.addAction(actionInfo.iconResId, actionInfo.title, pIntent);
                }
            }
        }

        if (info.progress >= 0 && info.progress <= 100) {
            notificationBuilder.setProgress(info.progress == 0 ? 0 : 100, info.progress, info.progress == 0);
        } else {
            notificationBuilder.setProgress(0, 0, false);
        }

        notificationBuilder.setContentTitle(info.contentTitle != null ? info.contentTitle : "");

        notificationBuilder.setContentText(info.text);
        notificationBuilder.setSubText(info.subText);

        addToNotiList(info.id, notificationBuilder);

        return notificationBuilder.build();
    }

    /**
     * @param id must be at list
     */
    public synchronized void updateNotification(int id, Notification noti) {

        if (noti == null)
            throw new NullPointerException("noti is null");

        if (!notis.containsKey(id))
            throw new IllegalArgumentException("no notification with id " + id + " was created before");

        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, noti);
    }

    public synchronized void removeNotification(int id) {
        removeNotification(id, true);
    }

    private synchronized void removeNotification(int id, boolean removeFromList) {
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(id);
        if (removeFromList)
            removeFromNotiIdsList(id);
    }

    public synchronized void removeAllDownloadNotifications() {
        for (Iterator<Integer> it = notis.keySet().iterator(); it.hasNext(); ) {
            final int id = it.next();
            removeNotification(id, false);
            it.remove();
        }
    }

}
