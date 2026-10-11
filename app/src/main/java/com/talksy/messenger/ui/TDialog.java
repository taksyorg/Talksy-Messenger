package com.talksy.messenger.ui;

import com.talksy.messenger.data.IconManager;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.R;

public class TDialog implements DialogInterface {

    private final Dialog dialog;

    private TDialog(Dialog d) { this.dialog = d; }

    public void show() { dialog.show(); }
    public boolean isShowing() { return dialog.isShowing(); }
    @Override public void dismiss() { dialog.dismiss(); }
    @Override public void cancel() { dialog.cancel(); }
    public void setOnDismissListener(OnDismissListener l) { dialog.setOnDismissListener(l); }
    public Dialog getDialog() { return dialog; }

    public static class Builder {
        private final Context ctx;
        private CharSequence title, message;
        private CharSequence[] items;
        private int checked = -1;
        private boolean singleChoice = false;
        private OnClickListener itemListener;
        private View customView;
        private CharSequence posText, negText, neutText;
        private OnClickListener posListener, negListener, neutListener;
        private boolean cancelable = true;
        private OnDismissListener dismissListener;

        public Builder(Context ctx) { this.ctx = ctx; }

        public Builder setTitle(CharSequence t) { title = t; return this; }
        public Builder setTitle(int res) { title = ctx.getString(res); return this; }
        public Builder setMessage(CharSequence m) { message = m; return this; }
        public Builder setMessage(int res) { message = ctx.getString(res); return this; }

        public Builder setIcon(int res) { return this; }
        public Builder setView(View v) { customView = v; return this; }
        public Builder setCancelable(boolean c) { cancelable = c; return this; }
        public Builder setOnDismissListener(OnDismissListener l) { dismissListener = l; return this; }

        public Builder setItems(CharSequence[] its, OnClickListener l) {
            items = its; itemListener = l; singleChoice = false; return this;
        }
        public Builder setSingleChoiceItems(CharSequence[] its, int checkedItem, OnClickListener l) {
            items = its; checked = checkedItem; itemListener = l; singleChoice = true; return this;
        }

        public Builder setPositiveButton(CharSequence t, OnClickListener l) { posText = t; posListener = l; return this; }
        public Builder setPositiveButton(int res, OnClickListener l) { return setPositiveButton(ctx.getString(res), l); }
        public Builder setNegativeButton(CharSequence t, OnClickListener l) { negText = t; negListener = l; return this; }
        public Builder setNegativeButton(int res, OnClickListener l) { return setNegativeButton(ctx.getString(res), l); }
        public Builder setNeutralButton(CharSequence t, OnClickListener l) { neutText = t; neutListener = l; return this; }
        public Builder setNeutralButton(int res, OnClickListener l) { return setNeutralButton(ctx.getString(res), l); }

        private float dp(float v) { return v * ctx.getResources().getDisplayMetrics().density; }
        private int color(int res) { return ContextCompat.getColor(ctx, res); }

        public TDialog create() {
            final Dialog d = new Dialog(ctx);
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            final TDialog td = new TDialog(d);

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(color(R.color.bg2));
            bg.setCornerRadius(dp(28));
            card.setBackground(bg);
            card.setPadding(0, (int) dp(20), 0, (int) dp(10));

            if (title != null && title.length() > 0) {
                TextView tv = new TextView(ctx);
                tv.setText(title);
                tv.setTextColor(color(R.color.text1));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
                tv.setPadding((int) dp(24), 0, (int) dp(24), (int) dp(10));
                card.addView(tv);
            }
            if (message != null && message.length() > 0) {
                TextView tv = new TextView(ctx);
                tv.setText(message);
                tv.setTextColor(color(R.color.text2));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                tv.setPadding((int) dp(24), 0, (int) dp(24), (int) dp(10));
                card.addView(tv);
            }

            int maxH = (int) (ctx.getResources().getDisplayMetrics().heightPixels * 0.6f);
            if (items != null) {
                LinearLayout list = new LinearLayout(ctx);
                list.setOrientation(LinearLayout.VERTICAL);
                for (int i = 0; i < items.length; i++) {
                    final int which = i;
                    LinearLayout row = new LinearLayout(ctx);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding((int) dp(24), (int) dp(14), (int) dp(24), (int) dp(14));
                    row.setBackgroundResource(R.drawable.ripple_item);

                    TextView label = new TextView(ctx);
                    label.setText(items[i]);
                    label.setTextColor(color(R.color.text1));
                    label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    if (singleChoice && i == checked) {
                        TextView mark = new TextView(ctx);
                        IconManager.set(mark, "success", 22, color(R.color.accent));
                        row.addView(mark);
                    }
                    row.setOnClickListener(v -> {
                        if (itemListener != null) itemListener.onClick(td, which);

                        if (!singleChoice && d.isShowing()) d.dismiss();
                    });
                    list.addView(row);
                }
                ScrollView sv = new ScrollView(ctx);
                sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
                sv.addView(list);
                card.addView(sv, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                sv.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                    if (sv.getHeight() > maxH) sv.getLayoutParams().height = maxH;
                });
            }

            if (customView != null) {
                if (customView.getParent() instanceof ViewGroup) ((ViewGroup) customView.getParent()).removeView(customView);
                ScrollView sv = new ScrollView(ctx);
                sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
                sv.addView(customView);
                sv.setPadding((int) dp(8), 0, (int) dp(8), 0);
                card.addView(sv, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                sv.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                    if (sv.getHeight() > maxH) sv.getLayoutParams().height = maxH;
                });
            }

            boolean hasButtons = posText != null || negText != null || neutText != null;
            if (hasButtons) {
                LinearLayout bar = new LinearLayout(ctx);
                bar.setOrientation(LinearLayout.HORIZONTAL);
                bar.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
                bar.setPadding((int) dp(12), (int) dp(8), (int) dp(12), 0);
                if (neutText != null) {
                    bar.addView(button(neutText, R.color.text2, false, td, neutListener, d, DialogInterface.BUTTON_NEUTRAL));
                    View spacer = new View(ctx);
                    bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
                }
                if (negText != null) bar.addView(button(negText, R.color.text2, false, td, negListener, d, DialogInterface.BUTTON_NEGATIVE));
                if (posText != null) bar.addView(button(posText, R.color.accent, true, td, posListener, d, DialogInterface.BUTTON_POSITIVE));
                card.addView(bar);
            } else if (items == null && customView == null) {
                card.setPadding(0, (int) dp(20), 0, (int) dp(20));
            }

            d.setContentView(card);
            d.setCancelable(cancelable);
            d.setCanceledOnTouchOutside(cancelable);
            if (dismissListener != null) d.setOnDismissListener(dismissListener);

            Window w = d.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setDimAmount(0.45f);
                w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                int width = Math.min(ctx.getResources().getDisplayMetrics().widthPixels - (int) dp(48), (int) dp(360));
                w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
                w.setGravity(Gravity.CENTER);
            }

            d.setOnShowListener(x -> {
                card.setScaleX(0.86f);
                card.setScaleY(0.86f);
                card.setAlpha(0f);
                card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260)
                        .setInterpolator(new OvershootInterpolator(1.4f)).start();
            });
            return td;
        }

        private TextView button(CharSequence text, int colorRes, boolean bold, TDialog td,
                                OnClickListener l, Dialog d, int which) {
            TextView b = new TextView(ctx);
            b.setText(text);
            b.setTextColor(color(colorRes));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            if (bold) b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
            b.setGravity(Gravity.CENTER);
            b.setPadding((int) dp(16), (int) dp(10), (int) dp(16), (int) dp(10));
            b.setBackgroundResource(R.drawable.ripple_item_round);
            b.setOnClickListener(v -> {
                if (l != null) l.onClick(td, which);
                if (d.isShowing()) d.dismiss();
            });
            com.talksy.messenger.data.AnimUtils.pressScale(b);
            return b;
        }

        public TDialog show() {
            TDialog td = create();
            td.show();
            return td;
        }
    }
}
