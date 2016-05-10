package ru.altarix.commonutils.android;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.SystemClock;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.altarix.commonutils.android.notification.NotificationController;
import ru.altarix.commonutils.android.notification.NotificationInfo;

public final class ServiceUtils {

    private final static Logger logger = LoggerFactory.getLogger(ServiceUtils.class);

    private ServiceUtils() {
    }

    public static <S extends Service> boolean isServiceRunning(@NonNull Context context, @NonNull Class<S> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static <S extends Service> boolean isServiceForeground(@NonNull Context context, @NonNull Class<S> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName()) && service.foreground) {
                return true;
            }
        }
        return false;
    }

    private static <S extends Service> void startNoCheck(@NonNull Context context, @NonNull Class<S> serviceClass) {
        Intent service = new Intent(context, serviceClass);
        context.startService(service);
    }

    public static <S extends Service> void start(@NonNull Context context, @NonNull Class<S> serviceClass) {
        logger.debug("start(), serviceClass= " + serviceClass);
        if (!isServiceRunning(context, serviceClass)) {
            restartNoCheck(context, serviceClass, null);
        }
    }

    private static <S extends Service> void restartNoCheck(@NonNull Context context, @NonNull Class<S> serviceClass, @Nullable S serviceInst) {
        logger.debug("restart(), serviceClass=" + serviceClass);
        stopNoCheck(context, serviceClass, serviceInst);
        startNoCheck(context, serviceClass);
    }

    public static <S extends Service> void restart(@NonNull Context context, @NonNull Class<S> serviceClass, @Nullable S serviceInst) {
        logger.debug("restart(), serviceClass=" + serviceClass);
        stop(context, serviceClass, serviceInst);
        startNoCheck(context, serviceClass);
    }

    private static <S extends Service> void stopNoCheck(@NonNull Context context, @NonNull Class<S> serviceClass, @Nullable S serviceInst) {
//        if (serviceInst != null) {
//            serviceInst.onDestroy();
//        }
        Intent service = new Intent(context, serviceClass);
        context.stopService(service);
    }

    public static <S extends Service> void stop(@NonNull Context context, @NonNull Class<S> serviceClass, @Nullable S serviceInst) {
        logger.debug("stop(), serviceClass=" + serviceClass + ", serviceInst=" + serviceInst);
        if (isServiceRunning(context, serviceClass)) {
            stopNoCheck(context, serviceClass, serviceInst);
        }
    }

    public static <S extends Service> void cancelDelay(@NonNull Context context, @NonNull Class<S> serviceClass) {
        logger.debug("cancelDelay(), serviceClass=" + serviceClass);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, serviceClass);
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, 0);
        alarmManager.cancel(pendingIntent);
    }

    public static <S extends Service> void restartDelay(@NonNull Context context, @NonNull Class<S> serviceClass, long delay) {
        logger.debug("restartDelay(), serviceClass=" + serviceClass + ", delay=" + delay);

        if (delay < 0) {
            throw new IllegalArgumentException("incorrect delay: " + delay);
        }
        stop(context, serviceClass, null);
        startDelayNoCheck(context, serviceClass, delay);
    }

    private static <S extends Service> void restartDelayNoCheck(@NonNull Context context, @NonNull Class<S> serviceClass, long delay) {

        if (delay < 0) {
            throw new IllegalArgumentException("incorrect delay: " + delay);
        }

        stopNoCheck(context, serviceClass, null);
        startDelayNoCheck(context, serviceClass, delay);
    }

    public static <S extends Service> void startDelayNoCheck(@NonNull Context context, @NonNull Class<S> serviceClass, long delay) {

        cancelDelay(context, serviceClass);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, serviceClass);
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, 0);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, pendingIntent);
    }

    public static <S extends Service> void startDelay(@NonNull Context context, @NonNull Class<S> serviceClass, long delay) {
        logger.debug("startDelay(), serviceClass=" + serviceClass + ", delay=" + delay);
        if (!isServiceRunning(context, serviceClass)) {
            restartDelayNoCheck(context, serviceClass, delay);
        }
    }

    public static void startServiceForeground(@NonNull Context context, @NonNull Service service, int id, @Nullable Intent contentIntent, @Nullable String ticker, @Nullable String title, @Nullable String text, @DrawableRes int iconRes) {
        logger.debug("startServiceForeground(), service=" + service + ", id=" + id + ", contentIntent=" + contentIntent + ", ticker=" + ticker + ", title=" + title + ", text=" + text + ", iconRes=" + iconRes);
        if (!isServiceForeground(context, service.getClass())) {

            NotificationController.getInstance().removeNotification(id);

            NotificationInfo info = new NotificationInfo();
            info.id = id;
            info.autoCancel = false;
            info.onlyUpdate = false;
            info.ongoing = true;
            info.contentIntent = contentIntent;
            info.tickerText = ticker;
            info.contentTitle = title;
            info.text = text;

            service.startForeground(id, NotificationController.getInstance().createAndAddNotification(info));
        }
    }

    public static <S extends Service> void stopServiceForeground(@NonNull Context context, @NonNull S service, int id) {
        logger.debug("stopServiceForeground(), service=" + service + ", id=" + id);
        if (isServiceForeground(context, service.getClass())) {
            service.stopForeground(true);
            NotificationController.getInstance().removeNotification(id);
        }
    }

    public static <C extends ServiceConnection, S extends Service> boolean bindService(@NonNull Context ctx, @NonNull Class<S> serviceClass, @NonNull C serviceConnection, int flags) {
        logger.debug("bindService(), serviceClass=" + serviceClass + ", serviceConnection=" + serviceConnection + ", flags=" + flags);
        if (!ctx.bindService(new Intent(ctx, serviceClass), serviceConnection, flags)) {
            logger.error("binding to service " + serviceClass + " failed");
            stop(ctx, serviceClass, null);
            start(ctx, serviceClass);
            return false;
        }
        return true;
    }

    public static <C extends ServiceConnection> void unbindService(@NonNull Context context, @NonNull C serviceConnection) {
        logger.debug("unbindService(), serviceConnection=" + serviceConnection);
        context.unbindService(serviceConnection);
    }


}
