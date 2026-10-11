package com.talksy.messenger.data;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.R;
import com.talksy.messenger.net.ApiClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MentionUtils {

    private static final Pattern MENTION = Pattern.compile("@[A-Za-z0-9_]{3,32}");

    public static void linkify(TextView tv, ApiClient apiClient) {
        CharSequence current = tv.getText();
        if (current == null || current.length() == 0) return;
        String s = current.toString();
        Matcher m = MENTION.matcher(s);
        if (!m.find()) return;

        SpannableString sp = new SpannableString(s);
        m.reset();
        while (m.find()) {
            final String handle = m.group();
            final int start = m.start(), end = m.end();
            sp.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    onMentionTapped(widget.getContext(), apiClient, handle);
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    ds.setColor(ContextCompat.getColor(tv.getContext(), R.color.accent));
                    ds.setUnderlineText(false);
                }
            }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tv.setText(sp);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setHighlightColor(Color.TRANSPARENT);
    }

    private static void onMentionTapped(Context ctx, ApiClient apiClient, String handle) {
        onMentionTappedPublic(ctx, apiClient, handle);
    }

    public static void onMentionTappedPublic(Context ctx, ApiClient apiClient, String handle) {
        apiClient.resolveHandle(handle, (ok, type, id, name, error) -> {
            if (!ok || id <= 0) {
                Toast.makeText(ctx, R.string.mention_not_found, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i;
            if ("group".equals(type)) {
                i = new Intent(ctx, com.talksy.messenger.ui.GroupChatActivity.class);
                i.putExtra("group_id", id);
                i.putExtra("group_name", name);
            } else {
                i = new Intent(ctx, com.talksy.messenger.ui.UserProfileActivity.class);
                i.putExtra("user_id", id);
            }
            if (!(ctx instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        });
    }
}
