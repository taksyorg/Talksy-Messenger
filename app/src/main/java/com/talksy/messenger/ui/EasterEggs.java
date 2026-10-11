package com.talksy.messenger.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;

import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.LiquidGlass;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.net.ApiClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class EasterEggs {
    private EasterEggs() {}

    private static final String KEY_FOUND = "secrets_found";

    private static final String[] SECRETS = {"matrix", "confetti", "ping", "me", "dice", "coin", "night", "glass", "42", "talksy"};

    private static final String[][] HINTS = {
            {"matrix", "Что видит Нео?", "What does Neo see?"},
            {"confetti", "Праздник без повода", "A party for no reason"},
            {"ping", "Эхо сервера", "Echo of the server"},
            {"me", "Кто я?", "Who am I?"},
            {"dice", "Кости брошены", "The die is cast"},
            {"coin", "Орёл или решка", "Heads or tails"},
            {"night", "Свет мой, зеркальце, скажи", "Lights out"},
            {"glass", "Жидкое стекло", "Liquid glass"},
            {"42", "Ответ на главный вопрос жизни, вселенной и всего такого", "The answer to life, the universe and everything"},
            {"talksy", "Назови нас по имени", "Call us by name"},
    };

    private static boolean ru(Context c) {
        return "ru".equals(c.getResources().getConfiguration().getLocales().get(0).getLanguage());
    }

    private static String tr(Context c, String ru, String en) { return ru(c) ? ru : en; }

    public static boolean handle(Activity a, String query) {
        if (query == null) return false;
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        if (!q.startsWith("/") || q.length() < 2) return false;
        String cmd = q.substring(1);
        switch (cmd) {
            case "matrix": found(a, cmd); matrix(a); return true;
            case "confetti": found(a, cmd); confetti(a); return true;
            case "ping": found(a, cmd); ping(a); return true;
            case "me": found(a, cmd); me(a); return true;
            case "dice": found(a, cmd); dialog(a, "🎲", tr(a, "Выпало: ", "You rolled: ") + (1 + new Random().nextInt(6))); return true;
            case "coin": found(a, cmd); dialog(a, "🪙", new Random().nextBoolean() ? tr(a, "Орёл", "Heads") : tr(a, "Решка", "Tails")); return true;
            case "night": found(a, cmd); night(a); return true;
            case "glass": found(a, cmd); glass(a); return true;
            case "42": found(a, cmd); dialog(a, "42", tr(a, "Теперь осталось вспомнить сам вопрос.", "Now all that is left is to remember the question.")); return true;
            case "talksy": found(a, cmd); confetti(a); Toast.makeText(a, tr(a, "Talksy благодарит вас за то, что вы здесь", "Talksy thanks you for being here"), Toast.LENGTH_LONG).show(); return true;
            case "secrets": secrets(a); return true;
            case "support": a.startActivity(new Intent(a, SupportActivity.class)); AnimUtils.forward(a); return true;
            default: return false;
        }
    }

    private static Set<String> foundSet(Context c) {
        String raw = new Prefs(c).get(KEY_FOUND, "");
        Set<String> s = new HashSet<>();
        if (!raw.isEmpty()) s.addAll(Arrays.asList(raw.split(",")));
        return s;
    }

    private static void found(Activity a, String cmd) {
        Set<String> s = foundSet(a);
        if (!s.add(cmd)) return;
        new Prefs(a).put(KEY_FOUND, android.text.TextUtils.join(",", s));
        Toast.makeText(a, "🔓 " + tr(a, "Секрет найден: ", "Secret found: ") + s.size() + "/" + SECRETS.length, Toast.LENGTH_SHORT).show();
        if (s.size() == SECRETS.length) {
            confetti(a);
            new TDialog.Builder(a)
                    .setTitle("🏆")
                    .setMessage(tr(a, "Вы нашли все секреты Talksy. Вы в числе самых внимательных.", "You found every Talksy secret. You are among the most attentive."))
                    .setPositiveButton(android.R.string.ok, null).show();
        }
    }

    static void secrets(Activity a) {
        Set<String> s = foundSet(a);
        StringBuilder sb = new StringBuilder();
        sb.append(tr(a, "Найдено: ", "Found: ")).append(s.size()).append(" / ").append(SECRETS.length).append("\n\n");
        List<String[]> left = new ArrayList<>();
        for (String[] h : HINTS) if (!s.contains(h[0])) left.add(h);
        java.util.Collections.shuffle(left);
        if (left.isEmpty()) sb.append(tr(a, "Больше искать нечего.", "Nothing left to find."));
        else {
            sb.append(tr(a, "Подсказки:", "Hints:"));
            for (int i = 0; i < Math.min(3, left.size()); i++) sb.append("\n• ").append(ru(a) ? left.get(i)[1] : left.get(i)[2]);
            sb.append("\n\n").append(tr(a, "Команды вводятся в поиске и начинаются с «/».", "Commands are typed in search and start with “/”."));
        }
        new TDialog.Builder(a).setTitle("/secrets").setMessage(sb.toString()).setPositiveButton(android.R.string.ok, null).show();
    }

    private static void dialog(Activity a, String title, String msg) {
        new TDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton(android.R.string.ok, null).show();
    }

    static void ping(Activity a) {
        ApiClient api = new ApiClient(a);
        long t0 = SystemClock.elapsedRealtime();
        api.supportGet("/support/info", (ok, json, err) -> {
            long ms = SystemClock.elapsedRealtime() - t0;
            String body = tr(a, "Ответ сервера за ", "Server answered in ") + ms + tr(a, " мс", " ms");
            if (!ok && err != null) body += "\n" + err;
            dialog(a, "📡 ping", body);
        });
    }

    static void me(Activity a) {
        String ver = "";
        try { ver = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; } catch (Exception ignored) {}
        Prefs p = new Prefs(a);
        String info = "ID: " + p.get(Prefs.KEY_USER_ID, "?")
                + "\nTalksy " + ver
                + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\n" + Build.MANUFACTURER + " " + Build.MODEL
                + "\n" + new ApiClient(a).getBaseUrl();
        new TDialog.Builder(a).setTitle(tr(a, "Это вы", "This is you")).setMessage(info)
                .setPositiveButton(tr(a, "Скопировать", "Copy"), (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Talksy", info));
                    Toast.makeText(a, tr(a, "Скопировано", "Copied"), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private static void night(Activity a) {
        Prefs p = new Prefs(a);
        boolean dark = !p.getBool(Prefs.KEY_DARK_THEME, true);
        p.putBool(Prefs.KEY_DARK_THEME, dark);
        AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        a.recreate();
    }

    private static void glass(Activity a) {
        boolean on = !LiquidGlass.isEnabled(a);
        LiquidGlass.setEnabled(a, on);
        Toast.makeText(a, "Liquid Glass: " + (on ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        a.recreate();
    }

    private static void overlay(Activity a, View v, long durationMs, ValueAnimator.AnimatorUpdateListener tick) {
        ViewGroup root = a.findViewById(android.R.id.content);
        if (root == null) return;
        root.addView(v, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(durationMs);
        va.setInterpolator(new LinearInterpolator());
        va.addUpdateListener(an -> { tick.onAnimationUpdate(an); v.invalidate(); });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) { root.removeView(v); }
        });
        va.start();
    }

    static void matrix(Activity a) {
        final MatrixView mv = new MatrixView(a);
        overlay(a, mv, 6000, an -> mv.progress = (float) an.getAnimatedValue());
    }

    static void confetti(Activity a) {
        final ConfettiView cv = new ConfettiView(a);
        overlay(a, cv, 3800, an -> cv.progress = (float) an.getAnimatedValue());
    }

    private static final class MatrixView extends View {
        float progress;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd = new Random();
        private float[] heads;
        private float[] speeds;
        private float size;
        private static final String CHARS = "01ABCDEFGHIJKLMNOPQRSTUVWXYZ#$%&*+=<>";

        MatrixView(Context c) {
            super(c);
            paint.setTypeface(Typeface.MONOSPACE);
            size = c.getResources().getDisplayMetrics().density * 16;
            paint.setTextSize(size);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            int cols = Math.max(1, (int) (w / size));
            heads = new float[cols];
            speeds = new float[cols];
            for (int i = 0; i < cols; i++) {
                heads[i] = -rnd.nextInt(Math.max(1, h)) ;
                speeds[i] = size * (0.25f + rnd.nextFloat() * 0.6f);
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            if (heads == null) return;
            float fade = progress < 0.08f ? progress / 0.08f : progress > 0.85f ? (1f - progress) / 0.15f : 1f;
            canvas.drawColor(Color.argb((int) (230 * fade), 0, 0, 0));
            int trail = 14;
            for (int i = 0; i < heads.length; i++) {
                heads[i] += speeds[i];
                if (heads[i] - trail * size > getHeight()) heads[i] = -rnd.nextInt(300);
                for (int t = 0; t < trail; t++) {
                    float y = heads[i] - t * size;
                    if (y < 0 || y > getHeight()) continue;
                    int alpha = (int) (255 * fade * (1f - t / (float) trail));
                    paint.setColor(t == 0 ? Color.argb((int) (255 * fade), 220, 255, 220) : Color.argb(alpha, 0, 255, 70));
                    canvas.drawText(String.valueOf(CHARS.charAt(rnd.nextInt(CHARS.length()))), i * size, y, paint);
                }
            }
        }
    }

    private static final class ConfettiView extends View {
        float progress;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd = new Random();
        private final float[] x = new float[140], y = new float[140], vx = new float[140], vy = new float[140], rot = new float[140], sz = new float[140];
        private final int[] col = new int[140];
        private boolean init;
        private static final int[] COLORS = {0xFFEF4444, 0xFFF59E0B, 0xFF22C55E, 0xFF3B82F6, 0xFF8B5CF6, 0xFFEC4899, 0xFF06B6D4};

        ConfettiView(Context c) { super(c); }

        @Override protected void onDraw(Canvas canvas) {
            float d = getResources().getDisplayMetrics().density;
            if (!init) {
                init = true;
                for (int i = 0; i < x.length; i++) {
                    x[i] = getWidth() / 2f;
                    y[i] = getHeight() * 0.35f;
                    double ang = rnd.nextDouble() * Math.PI * 2;
                    float sp = (4 + rnd.nextFloat() * 14) * d;
                    vx[i] = (float) Math.cos(ang) * sp;
                    vy[i] = (float) Math.sin(ang) * sp - 8 * d;
                    rot[i] = rnd.nextFloat() * 360;
                    sz[i] = (5 + rnd.nextFloat() * 6) * d;
                    col[i] = COLORS[rnd.nextInt(COLORS.length)];
                }
            }
            float fade = progress > 0.8f ? (1f - progress) / 0.2f : 1f;
            for (int i = 0; i < x.length; i++) {
                vy[i] += 0.5f * d;
                vx[i] *= 0.985f;
                x[i] += vx[i];
                y[i] += vy[i];
                rot[i] += vx[i] * 2;
                paint.setColor(col[i]);
                paint.setAlpha((int) (255 * fade));
                canvas.save();
                canvas.rotate(rot[i], x[i], y[i]);
                canvas.drawRect(x[i], y[i], x[i] + sz[i], y[i] + sz[i] * 0.55f, paint);
                canvas.restore();
            }
        }
    }
}
