package com.talksy.messenger.sync;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Looper;

import com.talksy.messenger.net.ApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.HashMap;
import java.util.Map;

public final class LiveLocation {
    private LiveLocation() {}

    private static final Map<String, LocationCallback> ACTIVE = new HashMap<>();

    @SuppressLint("MissingPermission")
    public static synchronized void start(Context ctx, String id, long untilMs) {
        if (ACTIVE.containsKey(id) || untilMs <= System.currentTimeMillis()) return;
        final Context app = ctx.getApplicationContext();
        final ApiClient api = new ApiClient(app);
        final FusedLocationProviderClient fused = LocationServices.getFusedLocationProviderClient(app);
        LocationCallback cb = new LocationCallback() {
            @Override public void onLocationResult(LocationResult r) {
                if (System.currentTimeMillis() > untilMs) { stop(app, id); return; }
                android.location.Location l = r.getLastLocation();
                if (l != null) new Thread(() -> api.postLiveLocation(id, l.getLatitude(), l.getLongitude(), untilMs)).start();
            }
        };
        ACTIVE.put(id, cb);
        try {
            fused.requestLocationUpdates(new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000).setMinUpdateDistanceMeters(5).build(), cb, Looper.getMainLooper());
        } catch (SecurityException e) { ACTIVE.remove(id); }
    }

    public static synchronized void stop(Context ctx, String id) {
        LocationCallback cb = ACTIVE.remove(id);
        if (cb != null) LocationServices.getFusedLocationProviderClient(ctx.getApplicationContext()).removeLocationUpdates(cb);
    }

    public static synchronized boolean isActive(String id) { return ACTIVE.containsKey(id); }
}
