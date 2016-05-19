package ru.maxsmr.commonutils.android.location;

import android.content.Context;
import android.database.Observable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.maxsmr.commonutils.android.hardware.DeviceUtils;
import ru.maxsmr.commonutils.android.location.info.LocStatus;
import ru.maxsmr.commonutils.android.location.info.LocationInfo;

public class LocationWatcher {

    private static final Logger logger = LoggerFactory.getLogger(LocationWatcher.class);

    private static LocationWatcher sInstance;

    public static void initInstance(Context ctx) {
        if (sInstance == null) {
            synchronized (LocationWatcher.class) {
                sInstance = new LocationWatcher(ctx);
            }
        }
    }

    public static LocationWatcher getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("initInstance() was not called");
        }
        return sInstance;
    }

    private final Context mContext;

    private LocationWatcher(Context ctx) {
        mContext = ctx;
    }

    private final LocationObservable locationObservable = new LocationObservable();

    public Observable<LocationTrackingListener> getLocationObservable() {
        return locationObservable;
    }

    private Location mLastLocation;

    public Location getLastLocation() {
        return mLastLocation;
    }

    private LocationInfo mLastLocationInfo;

    public LocationInfo getLastLocationInfo() {
        return mLastLocationInfo;
    }

    private boolean updateLocation(Location loc) {

        if (loc == null)
            return false;

        if (loc == mLastLocation)
            return false;

        LocationInfo locationInfo = LocationInfo.from(loc, mContext);

        final boolean isSameLocationInfos = mLastLocationInfo != null && mLastLocationInfo.equals(locationInfo);
        logger.info("last location info: " + mLastLocationInfo + ", new location info: " + locationInfo + ", is same: " + isSameLocationInfos);

        final float accuracy = mLastLocation == null ? 0 : mLastLocation.getAccuracy();
        final boolean isBetterAccuracy = loc.getAccuracy() > accuracy;
        logger.info("last accuracy: " + accuracy + ", new is better: " + isBetterAccuracy);

        final long time = mLastLocation == null ? 0 : mLastLocation.getTime();
        final boolean isLaterTime = loc.getTime() - time > mLocationUpdateTime;
        logger.info("last update time: " + time + ", new is later: " + isLaterTime);

        if (!isSameLocationInfos && (isBetterAccuracy || isLaterTime)) {

            mLastLocation = loc;
            mLastLocationInfo = locationInfo;
            logger.info("last location info has been changed: " + mLastLocationInfo);

            locationObservable.dispatchLocationUpdated(mLastLocation, mLastLocationInfo);
            locationObservable.dispatchLocationTrackingStatusChanged(LocStatus.NEW_LOCATION);
            return true;
        }

        return false;
    }




    private long mLocationUpdateTime = Defaults.DEFAULT_LOCATION_UPDATE_TIME;

    private float mLocationUpdateDistance = Defaults.DEFAULT_LOCATION_UPDATE_DISTANCE;

    @SuppressWarnings("MissingPermission")
    private static boolean addLocationListener(Context ctx, boolean openGpsActivity, final long minTime,
                                               final float minDistance, final LocationListener locationListener) {

        if (ctx == null)
            throw new NullPointerException("context is null");

        if (locationListener == null)
            throw new NullPointerException("locationListener is null");


        if (minTime < 0)
            throw new IllegalArgumentException("incorrect minTime: " + minTime);

        if (minDistance < 0)
            throw new IllegalArgumentException("incorrect minDistance: " + minDistance);

        final LocationManager locationManager = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);

        if (!LocationUtils.isProviderEnabled(ctx)) {
            logger.error("gps/network provider is not enabled");

            if (openGpsActivity)
                LocationUtils.startGpsSettingsActivity(ctx);

            return false;
        }

        if (DeviceUtils.checkPermission(ctx, "android.permission.ACCESS_FINE_LOCATION") && DeviceUtils.checkPermission(ctx, "android.permission.ACCESS_COARSE_LOCATION")) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDistance, locationListener, ctx.getMainLooper());
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTime, minDistance, locationListener, ctx.getMainLooper());
            return true;

        } else {
            logger.error("location permissions are not allowed");
            return false;
        }
    }

    @SuppressWarnings("MissingPermission")
    private static void removeLocationListener(Context ctx, final LocationListener locationListener) {

        if (locationListener == null)
            throw new NullPointerException("locationListener is null");

        if (DeviceUtils.checkPermission(ctx, "android.permission.ACCESS_FINE_LOCATION") && DeviceUtils.checkPermission(ctx, "android.permission.ACCESS_COARSE_LOCATION")) {
            ((LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE)).removeUpdates(locationListener);
        } else {
            logger.error("location permissions are not allowed");
        }
    }

    private DeviceLocationListener mLocationListener = null;

    private class DeviceLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location loc) {
            logger.debug("onLocationChanged(), loc=" + loc);
            updateLocation(loc);
        }

        @Override
        public void onProviderDisabled(String provider) {
            logger.debug("onProviderDisabled(), provider=" + provider);
        }

        @Override
        public void onProviderEnabled(String provider) {
            logger.debug("onProviderEnabled(), provider=" + provider);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            logger.debug("onStatusChanged(), provider=" + provider + ", status=" + status);

            switch (status) {
                case LocationProvider.AVAILABLE:
                    logger.info("provider " + provider + " is available");
                    break;

                case LocationProvider.OUT_OF_SERVICE:
                    logger.warn("provider " + provider + " is out of service");
                    break;

                case LocationProvider.TEMPORARILY_UNAVAILABLE:
                    logger.warn("provider " + provider + " is temporary unavailable");
                    break;
            }

        }
    }

    public boolean isTracking() {
        return mLocationListener != null;
    }

    public void stopTracking(boolean resetLastLoc) {
        logger.debug("stopTracking(), resetLastLoc=" + resetLastLoc);

        if (!isTracking()) {
            logger.debug("not tracking");
            return;
        }

        removeLocationListener(mContext, mLocationListener);
        mLocationListener = null;

        if (resetLastLoc) {
            mLastLocation = null;
            mLastLocationInfo = null;
        }

        locationObservable.dispatchLocationTrackingStatusChanged(LocStatus.STOP_TRACKING);
        logger.debug("tracking stopped");
    }

    public void startTracking(long minTime, float minDistance, boolean resetLastLoc, boolean openGpsActivity) {
        if (!isTracking()) {
            restartTracking(minTime, minDistance, resetLastLoc, openGpsActivity);
        }
    }

    /**
     * restarts tracking if it was already started
     */
    public void restartTracking(long minTime, float minDistance, boolean resetLastLoc, boolean openGpsActivity) {
        logger.debug("restartTracking(), minTime=" + minTime + ", minDistance=" + minDistance + ", resetLastLoc=" + resetLastLoc + ", openGpsActivity=" + openGpsActivity);

        stopTracking(resetLastLoc);

        if (addLocationListener(mContext, openGpsActivity, minTime, minDistance, mLocationListener = new DeviceLocationListener())) {
            mLocationUpdateTime = minTime;
            mLocationUpdateDistance = minDistance;
            locationObservable.dispatchLocationTrackingStatusChanged(LocStatus.START_TRACKING);
            logger.debug("tracking started");
        }
    }


    private static class LocationObservable extends Observable<LocationTrackingListener> {

        private void dispatchLocationTrackingStatusChanged(LocStatus status) {
            for (LocationTrackingListener l : mObservers) {
                l.onLocationTrackingStatusChanged(status);
            }
        }

        private void dispatchLocationUpdated(Location loc, LocationInfo locInfo) {
            for (LocationTrackingListener l : mObservers) {
                l.onLocationUpdated(loc, locInfo);
            }
        }
    }

    public interface Defaults {
        long DEFAULT_LOCATION_UPDATE_TIME = 5000; // 10000;
        float DEFAULT_LOCATION_UPDATE_DISTANCE = 0;
    }

}
