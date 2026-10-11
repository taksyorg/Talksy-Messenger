package com.talksy.messenger.sync;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.talksy.messenger.R;

public final class ConnectionUi {
    private ConnectionUi() {}

    public interface TextProvider { CharSequence get(); }

    public static final class Binding implements SyncManager.StateListener {
        private final Activity act;
        private final TextView title, subtitle;
        private final CharSequence normalTitle;
        private TextProvider subtitleProvider;
        private TextView banner;
        private final SyncManager sm;

        Binding(Activity act, TextView title, CharSequence normalTitle, TextView subtitle, View topBar) {
            this.act = act; this.title = title; this.normalTitle = normalTitle; this.subtitle = subtitle;
            this.sm = SyncManager.get(act);
            if (topBar != null && topBar.getParent() instanceof LinearLayout
                    && ((LinearLayout) topBar.getParent()).getOrientation() == LinearLayout.VERTICAL) {
                ViewGroup p = (ViewGroup) topBar.getParent();
                banner = new TextView(act);
                banner.setText(com.talksy.messenger.data.IconManager.prefix(act, "no_internet", act.getString(R.string.no_connection), Color.WHITE, 14));
                banner.setTextColor(Color.WHITE);
                banner.setBackgroundColor(0xFFB45309);
                banner.setTextSize(12);
                banner.setGravity(Gravity.CENTER);
                banner.setPadding(0, dp(3), 0, dp(3));
                banner.setVisibility(View.GONE);
                p.addView(banner, p.indexOfChild(topBar) + 1,
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }

        private int dp(int v) { return Math.round(v * act.getResources().getDisplayMetrics().density); }

        public Binding subtitleProvider(TextProvider p) { this.subtitleProvider = p; render(sm.getState()); return this; }

        public void resume() { sm.addListener(this); render(sm.getState()); }
        public void pause() { sm.removeListener(this); }

        public void refresh() { render(sm.getState()); }

        @Override public void onStateChanged(ConnState s) { render(s); }

        private void render(ConnState s) {
            boolean online = s == ConnState.ONLINE;
            if (title != null) title.setText(online ? normalTitle : act.getString(R.string.connecting));
            if (subtitle != null && subtitleProvider != null) subtitle.setText(online ? subtitleProvider.get() : act.getString(R.string.connecting_lower));
            if (banner != null) banner.setVisibility(s == ConnState.OFFLINE ? View.VISIBLE : View.GONE);
        }
    }

    public static Binding attach(Activity a, TextView title, CharSequence normalTitle, TextView subtitle, View topBar) {
        return new Binding(a, title, normalTitle, subtitle, topBar);
    }
}
