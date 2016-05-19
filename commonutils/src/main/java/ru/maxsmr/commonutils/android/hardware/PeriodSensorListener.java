package ru.maxsmr.commonutils.android.hardware;

import android.content.Context;
import android.database.Observable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.annotation.NonNull;

public class PeriodSensorListener {

    public PeriodSensorListener(@NonNull Context context, int sensorType) {
        this.context = context;
        this.sensorType = sensorType;
    }

    @NonNull
    private final Context context;

    private final SensorEventObservable sensorEventObservable = new SensorEventObservable();

    private int sensorType;

    private SensorEvents sensorEvents;

    public Observable<SensorEventListener> getSensorEventObservable() {
        return sensorEventObservable;
    }

    public boolean isTracking() {
        return sensorEvents != null;
    }

    public void startTracking(long period) {
        if (!isTracking()) {
            restartTracking(period);
        }
    }

    public void stopTracking() {
        if (isTracking()) {
            SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            sensorManager.unregisterListener(sensorEvents);
            sensorEvents = null;
        }
    }

    public void restartTracking(long period) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = sensorManager.getDefaultSensor(sensorType);
        sensorManager.registerListener(sensorEvents = new SensorEvents(period), sensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    private class SensorEvents implements SensorEventListener {

        final long period;

        long lastSensorChangedTimestamp = 0;
        long lastAccuracyChangedTimestamp = 0;

        public SensorEvents(long period) {
            if (period <= 0) {
                throw new IllegalArgumentException("incorrect period: " + period);
            }
            this.period = period;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
//            logger.debug("onSensorChanged(), event values:" + Arrays.toString(event.values));
            final long currentTimestamp = System.currentTimeMillis();
            if (currentTimestamp - lastSensorChangedTimestamp >= period) {
                lastSensorChangedTimestamp = currentTimestamp;
                sensorEventObservable.dispatchSensorChanged(event);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
//            logger.debug("onAccuracyChanged(), sensor=" + sensor + ", accuracy=" + accuracy);
            final long currentTimestamp = System.currentTimeMillis();
            if (currentTimestamp - lastAccuracyChangedTimestamp >= period) {
                lastAccuracyChangedTimestamp = currentTimestamp;
                sensorEventObservable.dispatchAccuracyChanged(sensor, accuracy);
            }
        }
    }

    private class SensorEventObservable extends Observable<SensorEventListener> {

        void dispatchSensorChanged(SensorEvent event) {
            for (SensorEventListener l : mObservers) {
                l.onSensorChanged(event);
            }
        }

        void dispatchAccuracyChanged(Sensor sensor, int accuracy) {
            for (SensorEventListener l : mObservers) {
                l.onAccuracyChanged(sensor, accuracy);
            }
        }
    }


}
