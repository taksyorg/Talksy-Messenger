package com.talksy.messenger.ui;

import com.talksy.messenger.ui.TDialog;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.talksy.messenger.R;

import java.util.Locale;

public class UploadProgressDialog {

    private final TDialog dialog;
    private final ProgressBar bar;
    private final TextView text;
    private final Context ctx;

    public UploadProgressDialog(Context ctx, String fileName, Runnable onCancel) {
        this.ctx = ctx;
        float dp = ctx.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * dp);
        root.setPadding(pad, pad, pad, (int) (8 * dp));

        TextView name = new TextView(ctx);
        name.setText(fileName);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        name.setTextSize(15);
        root.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (8 * dp));
        barLp.topMargin = (int) (14 * dp);
        root.addView(bar, barLp);

        text = new TextView(ctx);
        text.setTextSize(13);
        text.setText(R.string.preparing_file);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.topMargin = (int) (8 * dp);
        root.addView(text, textLp);

        dialog = new TDialog.Builder(ctx)
                .setView(root)
                .setCancelable(false)
                .setNegativeButton(R.string.upload_cancel_yes, (d, w) -> {
                    if (onCancel != null) onCancel.run();
                })
                .create();
    }

    public void show() { dialog.show(); }

    public void dismiss() {
        try { if (dialog.isShowing()) dialog.dismiss(); } catch (Exception ignored) { }
    }

    public void update(long sent, long total) {
        int pct = total > 0 ? (int) Math.min(100, sent * 100 / total) : 0;
        bar.setProgress(pct, true);
        text.setText(ctx.getString(R.string.upload_progress, mb(sent), mb(total), pct));
    }

    private static String mb(long bytes) {
        return String.format(Locale.getDefault(), "%.1f", bytes / 1048576.0);
    }
}
