package com.aftac.plugs.Gps;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.Date;

public class GpsService {
    private static final String LOG_TAG = "GpsService";
    private static LocationManager locationManager;
    private static boolean isGpsEnabled = false;

    static double latitude  = 0;
    static double longitude = 0;
    static double clockMultiplier  = 1.0;
    static long lastTimeStampNanos = 0;
    static long lastTimeStamp = 0;


    public static boolean init(Context context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Missing location permissions
            Log.v(LOG_TAG, "App does not have location permissions.");
            return false;
        }

        locationManager = (LocationManager) context.getSystemService(Service.LOCATION_SERVICE);
        isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!isGpsEnabled) {
            //TODO: Ask user to enable GPS
            Log.v(LOG_TAG, "GPS is disabled.");
            return false;
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                0, 0, locationListener);
        Log.v(LOG_TAG, "GPS Service is enabled");
        return true;
    }

    public static long getUtcTime() {
        if (lastTimeStamp == 0) return 0;
        return (lastTimeStamp +
                (long)((SystemClock.elapsedRealtimeNanos() - lastTimeStampNanos) / 1000000.0d
                            * clockMultiplier));
    }

    public static long getAdjustedUtcTime(long realTimeNanos) {
        if (lastTimeStamp == 0) return 0;
        return (lastTimeStamp +
                (long)((realTimeNanos - lastTimeStampNanos) / 1000000.0d * clockMultiplier));
    }

    public static String getFormattedUtcTime() {
        SimpleDateFormat formater = new SimpleDateFormat("dd MMM yyyy HH:mm:ss.SSS zz");
        Date date = new Date(getUtcTime());
        return formater.format(date);
    }

    private static android.location.LocationListener locationListener =
            new android.location.LocationListener() {

        @Override
        public void onLocationChanged(Location location) {
            // Get time
            long timestampNanos = location.getElapsedRealtimeNanos();
            long timestampUTC = location.getTime();
            Log.v(LOG_TAG, "Location Changed provider: " + location.getProvider());
            if (!location.getProvider().equals(LocationManager.GPS_PROVIDER)) return;
            if (lastTimeStamp > 0) {
                long elapsedTime = timestampUTC - lastTimeStamp;
                long elapsedNanos = timestampNanos - lastTimeStampNanos;
                double multiplier = elapsedTime / (elapsedNanos / 1000000.0d);
                Log.v(LOG_TAG, "GPS Time multiplier = " + multiplier + ", " + elapsedTime + ", " + elapsedNanos);
                clockMultiplier = (clockMultiplier * 0.99d + multiplier * 0.01d);
            }

            latitude = location.getLatitude();
            longitude = location.getLongitude();
            lastTimeStampNanos = timestampNanos;
            lastTimeStamp = timestampUTC;

            Log.v(LOG_TAG, "GPS time: " + getFormattedUtcTime());
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {
            if (provider == LocationManager.GPS_PROVIDER) {
                // TODO: GPS provicer disabled!!!
                Log.v(LOG_TAG, "GPS provider enabled");
            }
        }

        @Override
        public void onProviderDisabled(String provider) {
            if (provider == LocationManager.GPS_PROVIDER) {
                // TODO: GPS provicer disabled!!!
                Log.v(LOG_TAG, "GPS provider disabled");
            }
        }
    };
}