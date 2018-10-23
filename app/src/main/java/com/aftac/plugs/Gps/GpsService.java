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



    // Reject timestamps with too much jitter
    private static final double MAX_JITTER = 0.01;

    // Number of samples to reject when jitter is too high
    private static final int JITTER_REJECT = 3;

    // Number samples rejected due to jitter before the clock is reset
    private static final int JITTER_RESET = 100;

    private static LocationManager locationManager;
    private static boolean isGpsEnabled = false;

    static double latitude  = 0;
    static double longitude = 0;

    static long gpsTimeStamp = 0;
    static long gpsNanoTimeStamp = 0;

    static long lastTimeStamp     = 0;
    static long lastNanoTimeStamp = 0;
    static long firstTimestamp          = 0;
    static long firstNanoTimestamp      = 0;

    static double clockMultiplier = 0;
    static long multiplierTimestamp     = 0;
    static long multiplierNanoTimestamp = 0;

    static double lastJitter     = 0;
    static double highestJitter  = 0;
    static long   jitterNanos   = 0;
    static long   jitterCounter = JITTER_RESET;
    static long   jitterReject  = JITTER_REJECT;

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
        if (gpsTimeStamp == 0) return 0;
        return (gpsTimeStamp +
                (long)((SystemClock.elapsedRealtimeNanos() - gpsNanoTimeStamp) / 1000000.0d
                            * clockMultiplier));
    }

    public static long getAdjustedUtcTime(long realTimeNanos) {
        if (gpsTimeStamp == 0) return 0;
        return (gpsTimeStamp +
                (long)((realTimeNanos - gpsNanoTimeStamp) / 1000000.0d * clockMultiplier));
    }

    public static String getFormattedUtcTime(String format) {
        Date date = new Date(getUtcTime());
        SimpleDateFormat formater = new SimpleDateFormat(format);
        return formater.format(date);
    }

    private static android.location.LocationListener locationListener =
            new android.location.LocationListener() {

        @Override
        public void onLocationChanged(Location location) {
            // Get time
            long timestampNanos = location.getElapsedRealtimeNanos();
            long timestampUTC   = location.getTime();

            // Get location
            latitude = location.getLatitude();
            longitude = location.getLongitude();

            // If this location came from GPS set time from it
            if (location.getProvider().equals(LocationManager.GPS_PROVIDER))
                setTime(timestampUTC, timestampNanos);
        }
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                Log.v(LOG_TAG, "GPS provider enabled");
            }
        }

        @Override
        public void onProviderDisabled(String provider) {
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                // TODO: GPS provider disabled!!!
                Log.v(LOG_TAG, "GPS provider disabled");
            }
        }
    };

    private static void setTime(long timestampUTC, long timestampNanos) {
        if (firstTimestamp == 0) {
            firstTimestamp     = multiplierTimestamp     = lastTimeStamp     = timestampUTC;
            firstNanoTimestamp = multiplierNanoTimestamp = lastNanoTimeStamp = timestampNanos;
            return;
        }

        long elapsedTime, elapsedNanos;
        double multiplier, jitter = 0;

        // Only calculate multiplier while elapsed nanos is within the accuracy range for doubles
        elapsedNanos = timestampNanos - firstNanoTimestamp;
        if (elapsedNanos < 0x10000000000000L) {
            elapsedTime  = (timestampUTC   - firstTimestamp) * 1000000L;
            if (elapsedTime < 0x10000000000000L) {
                multiplier = (double) elapsedTime / (double) elapsedNanos;
                jitter = Math.abs(clockMultiplier - multiplier);
                clockMultiplier = multiplier;
            }
        }

        elapsedTime  = (timestampUTC   - lastTimeStamp) * 1000000L;
        elapsedNanos =  timestampNanos - lastNanoTimeStamp;

        jitterNanos += elapsedNanos * clockMultiplier - elapsedTime;

        lastTimeStamp     = timestampUTC;
        lastNanoTimeStamp = timestampNanos;

        multiplier   = (double)elapsedTime / (double)elapsedNanos;
        jitter += Math.abs(multiplier - clockMultiplier);

        if (jitter > MAX_JITTER) {
            Log.v(LOG_TAG, "Timestamp rejected due to jitter\n"
                    + "Multiplier: " + clockMultiplier + "\n"
                    + "Jitter: " + jitter * 100 + "%\n"
                    + "Last timestamp: " + lastTimeStamp + "\n"
                    + "Elapsed Time: " + elapsedTime + "\n"
                    + "Elapsed Nanos: " + elapsedNanos + "\n"
                    + "Jitter nanos: " + jitterNanos);
            if (--jitterCounter == 0) {
                firstTimestamp = 0;
                setTime(timestampUTC, timestampNanos);
                jitterCounter = JITTER_RESET;
            }
            jitterReject = JITTER_REJECT;
            return;
        } else {
            if (--jitterReject > 0) return;
            jitterCounter = JITTER_RESET;
        }


        if (jitter > highestJitter) highestJitter  = jitter;
        lastJitter = jitter;

        gpsTimeStamp     = timestampUTC;
        gpsNanoTimeStamp = timestampNanos;// + (elapsedNanos - (long)(elapsedTime / clockMultiplier));

        jitterNanos *= 0.99;
    }
}