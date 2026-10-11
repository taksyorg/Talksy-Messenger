package com.talksy.messenger.data;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;

import com.talksy.messenger.R;

public class AnimUtils {

    public static void forward(Activity activity) {
        if (isEnabled(activity)) {
            activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        } else {
            activity.overridePendingTransition(0, 0);
        }
    }

    public static void back(Activity activity) {
        if (isEnabled(activity)) {
            activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        } else {
            activity.overridePendingTransition(0, 0);
        }
    }

    public static boolean isEnabled(Activity activity) {
        return new Prefs(activity).getBool(Prefs.KEY_ANIMATIONS_ENABLED, true);
    }

    public static void pressScale(View view) {
        view.setOnTouchListener((v, event) -> {
            if (!isEnabled((Activity) v.getContext())) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(100).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150)
                            .setInterpolator(new android.view.animation.OvershootInterpolator())
                            .start();
                    break;
            }
            return false;
        });
    }

    public static void popIn(View view) {
        if (!isEnabled((Activity) view.getContext())) return;
        view.startAnimation(AnimationUtils.loadAnimation(view.getContext(), R.anim.message_pop_in));
    }

    public static void runLayoutAnimation(android.view.ViewGroup recyclerView) {
        if (!isEnabled((Activity) recyclerView.getContext())) return;
        recyclerView.setLayoutAnimation(
                AnimationUtils.loadLayoutAnimation(recyclerView.getContext(), R.anim.layout_animation_fall_down));
        recyclerView.scheduleLayoutAnimation();
    }
}
