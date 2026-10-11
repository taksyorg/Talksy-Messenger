package com.talksy.messenger.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.IconManager;
import com.talksy.messenger.data.MapKey;
import com.talksy.messenger.ui.widget.SettingsUi;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

public class LocationPickerActivity extends BaseActivity {
    private MapView mapView;
    private GoogleMap map;
    private TextView txtChosen;
    private LinearLayout placesBox;
    private boolean hasPerm;
    private double myLat = Double.NaN, myLon = Double.NaN;

    private final ActivityResultLauncher<String[]> permLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
        hasPerm = checkPerm();
        if (hasPerm) enableMyLocation(); else Toast.makeText(this, R.string.loc_no_permission, Toast.LENGTH_LONG).show();
    });

    private boolean checkPerm() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));
        mapView = new MapView(this);
        root.addView(mapView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mapView.onCreate(b);

        ImageView pin = new ImageView(this);
        IconManager.set(pin, "location", 0xFFEF4444);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(SettingsUi.dp(this, 44), SettingsUi.dp(this, 44), Gravity.CENTER);
        pp.bottomMargin = SettingsUi.dp(this, 120) + SettingsUi.dp(this, 22);
        root.addView(pin, pp);

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        GradientDrawable bg = new GradientDrawable(); bg.setShape(GradientDrawable.OVAL); bg.setColor(ContextCompat.getColor(this, R.color.bg2));
        back.setBackground(bg); back.setElevation(SettingsUi.dp(this, 4));
        back.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(SettingsUi.dp(this, 44), SettingsUi.dp(this, 44), Gravity.TOP | Gravity.START);
        bp.setMargins(SettingsUi.dp(this, 12), SettingsUi.dp(this, 36), 0, 0);
        root.addView(back, bp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pg = new GradientDrawable(); float r = SettingsUi.dp(this, 24);
        pg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0}); pg.setColor(ContextCompat.getColor(this, R.color.bg2));
        panel.setBackground(pg); panel.setElevation(SettingsUi.dp(this, 10));
        panel.setPadding(0, SettingsUi.dp(this, 8), 0, SettingsUi.dp(this, 8));
        txtChosen = new TextView(this);
        panel.addView(row("location", R.string.loc_send_selected, null, v -> sendSelected(), true));
        panel.addView(row("share", R.string.loc_live, getString(R.string.loc_live_sub), v -> chooseLive(), false));
        panel.addView(row("secure_chat", R.string.loc_send_mine, null, v -> sendMine(), false));
        TextView or = new TextView(this);
        or.setText(R.string.loc_or_choose); or.setTextColor(ContextCompat.getColor(this, R.color.accent)); or.setTextSize(13);
        or.setTypeface(or.getTypeface(), android.graphics.Typeface.BOLD);
        or.setPadding(SettingsUi.dp(this, 20), SettingsUi.dp(this, 10), 0, SettingsUi.dp(this, 4));
        panel.addView(or);
        ScrollView sv = new ScrollView(this);
        placesBox = new LinearLayout(this); placesBox.setOrientation(LinearLayout.VERTICAL);
        sv.addView(placesBox);
        panel.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 150)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(panel, lp);
        setContentView(root);

        hasPerm = checkPerm();
        if (!hasPerm) permLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        checkGps();
        mapView.getMapAsync(g -> {
            map = g;
            g.getUiSettings().setMapToolbarEnabled(false);
            g.getUiSettings().setZoomControlsEnabled(false);
            g.setPadding(0, 0, 0, SettingsUi.dp(this, 300));
            g.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(41.7151, 44.8271), 4f));
            if (hasPerm) enableMyLocation();
            g.setOnCameraIdleListener(this::onCenterChanged);
        });
    }

    private View row(String icon, int title, String sub, View.OnClickListener click, boolean showAddress) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(SettingsUi.dp(this, 20), SettingsUi.dp(this, 11), SettingsUi.dp(this, 20), SettingsUi.dp(this, 11));
        r.setBackgroundResource(R.drawable.ripple_item);
        ImageView iv = new ImageView(this);
        GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(ContextCompat.getColor(this, R.color.accent));
        iv.setBackground(g); int pad = SettingsUi.dp(this, 9); iv.setPadding(pad, pad, pad, pad);
        IconManager.set(iv, icon, Color.WHITE);
        r.addView(iv, new LinearLayout.LayoutParams(SettingsUi.dp(this, 42), SettingsUi.dp(this, 42)));
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); cp.setMarginStart(SettingsUi.dp(this, 14));
        r.addView(col, cp);
        TextView t = new TextView(this); t.setText(title); t.setTextSize(15); t.setTextColor(ContextCompat.getColor(this, R.color.text1)); col.addView(t);
        TextView s = showAddress ? txtChosen : new TextView(this);
        s.setTextSize(12); s.setTextColor(ContextCompat.getColor(this, R.color.text3)); s.setSingleLine(true);
        if (sub != null) s.setText(sub);
        col.addView(s);
        r.setOnClickListener(click);
        return r;
    }

    private void checkGps() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean on = lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        if (on) return;
        new TDialog.Builder(this).setMessage(R.string.loc_gps_off)
                .setPositiveButton(R.string.loc_gps_enable, (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (map != null) { try { map.setMyLocationEnabled(true); } catch (SecurityException ignored) {} }
        LocationServices.getFusedLocationProviderClient(this).getLastLocation().addOnSuccessListener(l -> {
            if (l == null) return;
            myLat = l.getLatitude(); myLon = l.getLongitude();
            if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(myLat, myLon), 16f));
            loadPlaces(myLat, myLon);
        });
    }

    private void onCenterChanged() {
        if (map == null) return;
        LatLng c = map.getCameraPosition().target;
        txtChosen.setText(String.format(Locale.US, "%.5f, %.5f", c.latitude, c.longitude));
        new Thread(() -> {
            try {
                List<Address> a = new Geocoder(this, Locale.getDefault()).getFromLocation(c.latitude, c.longitude, 1);
                if (a != null && !a.isEmpty() && a.get(0).getAddressLine(0) != null) { String line = a.get(0).getAddressLine(0); runOnUiThread(() -> txtChosen.setText(line)); }
            } catch (Exception ignored) {}
        }).start();
    }

    private LatLng center() { return map == null ? new LatLng(0, 0) : map.getCameraPosition().target; }

    private void sendSelected() { LatLng c = center(); finishWith(c.latitude, c.longitude, null, 0); }

    private void sendMine() {
        if (!hasPerm) { permLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}); return; }
        if (Double.isNaN(myLat)) { Toast.makeText(this, R.string.loc_waiting, Toast.LENGTH_SHORT).show(); return; }
        finishWith(myLat, myLon, null, 0);
    }

    private void chooseLive() {
        if (!hasPerm || Double.isNaN(myLat)) { Toast.makeText(this, hasPerm ? R.string.loc_waiting : R.string.loc_no_permission, Toast.LENGTH_SHORT).show(); return; }
        final int[] minutes = {15, 60, 480};
        CharSequence[] labels = {getString(R.string.loc_15m), getString(R.string.loc_1h), getString(R.string.loc_8h)};
        new TDialog.Builder(this).setTitle(R.string.loc_live).setItems(labels, (d, w) -> finishWith(myLat, myLon, null, minutes[w])).show();
    }

    private void finishWith(double lat, double lon, String title, int liveMinutes) {
        Intent r = new Intent().putExtra("lat", lat).putExtra("lon", lon).putExtra("live_minutes", liveMinutes);
        if (title != null) r.putExtra("title", title);
        setResult(RESULT_OK, r);
        finish();
    }

    private void loadPlaces(double lat, double lon) {
        if (!MapKey.has(this)) return;
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://places.googleapis.com/v1/places:searchNearby").openConnection();
                c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(8000); c.setReadTimeout(8000);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("X-Goog-Api-Key", MapKey.get(this));
                c.setRequestProperty("X-Goog-FieldMask", "places.displayName,places.formattedAddress,places.location");
                JSONObject body = new JSONObject().put("maxResultCount", 12).put("languageCode", Locale.getDefault().getLanguage())
                        .put("locationRestriction", new JSONObject().put("circle", new JSONObject()
                                .put("center", new JSONObject().put("latitude", lat).put("longitude", lon)).put("radius", 300.0)));
                try (OutputStream os = c.getOutputStream()) { os.write(body.toString().getBytes("UTF-8")); }
                if (c.getResponseCode() / 100 != 2) return;
                StringBuilder sb = new StringBuilder(); try (java.io.InputStream is = c.getInputStream()) { byte[] buf = new byte[4096]; int n; while ((n = is.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8")); }
                JSONArray arr = new JSONObject(sb.toString()).optJSONArray("places");
                runOnUiThread(() -> showPlaces(arr));
            } catch (Exception ignored) {}
        }).start();
    }

    private void showPlaces(JSONArray arr) {
        placesBox.removeAllViews();
        for (int i = 0; arr != null && i < arr.length(); i++) {
            JSONObject p = arr.optJSONObject(i); if (p == null) continue;
            final String name = p.optJSONObject("displayName") == null ? "" : p.optJSONObject("displayName").optString("text");
            final String addr = p.optString("formattedAddress");
            JSONObject loc = p.optJSONObject("location"); if (loc == null || name.isEmpty()) continue;
            final double la = loc.optDouble("latitude"), lo = loc.optDouble("longitude");
            LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL);
            r.setPadding(SettingsUi.dp(this, 20), SettingsUi.dp(this, 8), SettingsUi.dp(this, 20), SettingsUi.dp(this, 8));
            r.setBackgroundResource(R.drawable.ripple_item);
            TextView t = new TextView(this); t.setText(name); t.setTextSize(15); t.setTextColor(ContextCompat.getColor(this, R.color.text1)); r.addView(t);
            TextView s = new TextView(this); s.setText(addr); s.setTextSize(12); s.setSingleLine(true); s.setTextColor(ContextCompat.getColor(this, R.color.text3)); r.addView(s);
            r.setOnClickListener(v -> finishWith(la, lo, name, 0));
            placesBox.addView(r);
        }
    }

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { mapView.onStop(); super.onStop(); }
    @Override protected void onDestroy() { mapView.onDestroy(); super.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); mapView.onSaveInstanceState(out); }
}
