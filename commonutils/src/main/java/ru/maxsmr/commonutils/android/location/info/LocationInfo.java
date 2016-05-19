package ru.maxsmr.commonutils.android.location.info;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class LocationInfo {

    private static final Logger logger = LoggerFactory.getLogger(LocationInfo.class);

    @NonNull
    public static LocationInfo from(@Nullable Location location, @NonNull Context context) {

        if (location == null) {
            return new LocationInfo();
        }

        logger.info("location provider: " + location.getProvider());

        LocationInfo locationInfo = new LocationInfo();
        locationInfo.accuracy = location.getAccuracy();
        locationInfo.longitude = location.getLongitude();
        locationInfo.latitude = location.getLatitude();

        Geocoder gcd = new Geocoder(context, Locale.getDefault());
        try {
            final List<Address> addresses = gcd.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses.size() > 0) {
                locationInfo.countryName = addresses.get(0).getCountryName();
                locationInfo.locality = addresses.get(0).getLocality();
            }
        } catch (IOException e) {
            logger.error("an IOException occurred during getFromLocation(): " + e.getMessage());
        }
        return locationInfo;
    }

    public double latitude;

    public double longitude;

    public float accuracy;

    /**
     * in millis
     */
    public long time;

    public String countryName;

    public String locality;

    public boolean equals(LocationInfo locationInfo) {

        if (locationInfo == null) {
            return false;
        }

        if (locationInfo == this) {
            return true;
        }

        if (Double.doubleToLongBits(latitude) != Double.doubleToLongBits(locationInfo.latitude))
            return false;

        if (Double.doubleToLongBits(longitude) != Double.doubleToLongBits(locationInfo.longitude))
            return false;

        if (countryName == null) {
            if (locationInfo.countryName != null)
                return false;
        } else if (!countryName.equals(locationInfo.countryName))
            return false;

        if (locality == null) {
            if (locationInfo.locality != null)
                return false;
        } else if (!locality.equals(locationInfo.locality))
            return false;

        return true;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        return equals((LocationInfo) obj);
    }


    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(latitude);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(longitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (accuracy != +0.0f ? Float.floatToIntBits(accuracy) : 0);
        result = 31 * result + (int) (time ^ (time >>> 32));
        result = 31 * result + (countryName != null ? countryName.hashCode() : 0);
        result = 31 * result + (locality != null ? locality.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "LocationInfo{" +
                "latitude=" + latitude +
                ", longitude=" + longitude +
                ", accuracy=" + accuracy +
                ", time=" + time +
                ", countryName='" + countryName + '\'' +
                ", locality='" + locality + '\'' +
                '}';
    }
}