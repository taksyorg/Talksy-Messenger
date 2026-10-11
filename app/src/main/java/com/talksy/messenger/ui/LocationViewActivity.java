package com.talksy.messenger.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.ui.widget.SettingsUi;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LocationViewActivity extends BaseActivity {
    private MapView mapView;
    private GoogleMap map;
    private Marker marker;
    private TextView txtSub, txtTitle;
    private double lat, lon;
    private String liveId;
    private long until;
    private final Handler h = new Handler(Looper.getMainLooper());
    private ApiClient api;
    private final Runnable poll = new Runnable() { @Override public void run() { pollLive(); h.postDelayed(this, 5000); } };

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        api = new ApiClient(this);
        lat = getIntent().getDoubleExtra("lat", 0); lon = getIntent().getDoubleExtra("lon", 0);
        liveId = getIntent().getStringExtra("live_id"); until = getIntent().getLongExtra("until", 0);
        FrameLayout root = new FrameLayout(this);
        mapView = new MapView(this);
        root.addView(mapView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mapView.onCreate(b);

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        GradientDrawable bg = new GradientDrawable(); bg.setShape(GradientDrawable.OVAL); bg.setColor(ContextCompat.getColor(this, R.color.bg2));
        back.setBackground(bg); back.setElevation(SettingsUi.dp(this, 4));
        back.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(SettingsUi.dp(this, 44), SettingsUi.dp(this, 44), Gravity.TOP | Gravity.START);
        bp.setMargins(SettingsUi.dp(this, 12), SettingsUi.dp(this, 36), 0, 0);
        root.addView(back, bp);

        TextView open = new TextView(this);
        open.setText(R.string.loc_open_map); open.setTextColor(ContextCompat.getColor(this, R.color.text1)); open.setTextSize(14);
        GradientDrawable og = new GradientDrawable(); og.setCornerRadius(SettingsUi.dp(this, 22)); og.setColor(ContextCompat.getColor(this, R.color.bg2));
        open.setBackground(og); open.setElevation(SettingsUi.dp(this, 4));
        open.setPadding(SettingsUi.dp(this, 18), SettingsUi.dp(this, 11), SettingsUi.dp(this, 18), SettingsUi.dp(this, 11));
        open.setOnClickListener(v -> openInMaps());
        FrameLayout.LayoutParams opp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        opp.topMargin = SettingsUi.dp(this, 40);
        root.addView(open, opp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pg = new GradientDrawable(); float r = SettingsUi.dp(this, 24);
        pg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0}); pg.setColor(ContextCompat.getColor(this, R.color.bg2));
        panel.setBackground(pg); panel.setElevation(SettingsUi.dp(this, 10));
        panel.setPadding(SettingsUi.dp(this, 20), SettingsUi.dp(this, 16), SettingsUi.dp(this, 20), SettingsUi.dp(this, 16));
        txtTitle = new TextView(this); txtTitle.setTextSize(16); txtTitle.setTypeface(txtTitle.getTypeface(), android.graphics.Typeface.BOLD);
        txtTitle.setTextColor(ContextCompat.getColor(this, R.color.text1));
        String title = getIntent().getStringExtra("title");
        txtTitle.setText(title == null || title.isEmpty() ? getString(R.string.attach_location) : title);
        panel.addView(txtTitle);
        txtSub = new TextView(this); txtSub.setTextSize(13); txtSub.setTextColor(ContextCompat.getColor(this, R.color.text3));
        txtSub.setText(R.string.loc_loading);
        panel.addView(txtSub);
        Button route = new Button(this, null, 0, R.style.TalksyButton);
        route.setText(R.string.loc_route);
        route.setOnClickListener(v -> openRoute());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 50));
        rp.topMargin = SettingsUi.dp(this, 12);
        panel.addView(route, rp);
        root.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        setContentView(root);

        mapView.getMapAsync(g -> {
            map = g;
            g.getUiSettings().setMapToolbarEnabled(false);
            g.setPadding(0, 0, 0, SettingsUi.dp(this, 150));
            marker = g.addMarker(new MarkerOptions().position(new LatLng(lat, lon)));
            g.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lon), 16f));
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) g.setMyLocationEnabled(true);
        });
        resolveAddress(lat, lon);
    }

    private void resolveAddress(double la, double lo) {
        new Thread(() -> {
            String line = String.format(Locale.US, "%.5f, %.5f", la, lo);
            try {
                List<Address> a = new Geocoder(this, Locale.getDefault()).getFromLocation(la, lo, 1);
                if (a != null && !a.isEmpty() && a.get(0).getAddressLine(0) != null) line = a.get(0).getAddressLine(0);
            } catch (Exception ignored) {}
            final String l = line;
            runOnUiThread(() -> { if (liveId == null || liveId.isEmpty()) txtSub.setText(l); });
        }).start();
    }

    private void pollLive() {
        if (liveId == null || liveId.isEmpty()) return;
        api.getLiveLocation(liveId, (ok, p, err) -> {
            if (!ok || p == null) return;
            lat = p.lat; lon = p.lon;
            if (marker != null) marker.setPosition(new LatLng(lat, lon));
            if (map != null) map.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(lat, lon)));
            SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            txtSub.setText(p.active ? getString(R.string.loc_live_updated, f.format(new Date(p.updatedMs))) : getString(R.string.loc_live_ended));
            if (p.name != null && !p.name.isEmpty()) txtTitle.setText(p.name);
            if (!p.active) h.removeCallbacks(poll);
        });
    }

    private void openInMaps() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon));
        i.setPackage("com.google.android.apps.maps");
        try { startActivity(i); } catch (Exception e) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon))); }
    }

    private void openRoute() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + lat + "," + lon));
        i.setPackage("com.google.android.apps.maps");
        try { startActivity(i); }
        catch (Exception e) { LinkRouter.openExternal(this, "https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lon); }
    }

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); if (liveId != null && !liveId.isEmpty()) h.post(poll); }
    @Override protected void onPause() { h.removeCallbacks(poll); mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { mapView.onStop(); super.onStop(); }
    @Override protected void onDestroy() { mapView.onDestroy(); super.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); mapView.onSaveInstanceState(out); }
}
