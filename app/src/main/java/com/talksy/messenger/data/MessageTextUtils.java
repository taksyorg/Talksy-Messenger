package com.talksy.messenger.data;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.R;
import com.talksy.messenger.net.ApiClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageTextUtils {

    private static final Pattern MENTION = Pattern.compile("@[A-Za-z0-9_]{3,32}");

    private static final Pattern URL = Pattern.compile(
            "(?i)\\b((?:https?://|www\\.)[^\\s<>]+|(?:[a-z0-9-]+\\.)+(?:com|net|org|io|ru|su|ua|by|kz|me|ly|app|dev|xyz|info|site|online|tech|co|gg|tv|fm|cc|ge|uz|am|az|md|рф)(?:/[^\\s<>]*)?)");
    private static final Pattern SPOILER = Pattern.compile("\\|\\|(.+?)\\|\\|", Pattern.DOTALL);

    private MessageTextUtils() {}

    public static void render(TextView tv, ApiClient apiClient) { render(tv, apiClient, false); }

    private static int linkColor(boolean outgoing) { return outgoing ? 0xFFFFFFFF : 0xFF8F9BFF; }

    public static void render(TextView tv, ApiClient apiClient, boolean outgoing) {
        final int linkColor = linkColor(outgoing);
        CharSequence current = tv.getText();
        if (current == null || current.length() == 0) return;
        String s = current.toString();

        boolean hasMention = MENTION.matcher(s).find();
        boolean hasSpoiler = SPOILER.matcher(s).find();
        boolean hasUrl = URL.matcher(s).find();
        if (!hasMention && !hasSpoiler && !hasUrl) return;

        SpannableString sp = new SpannableString(s);
        java.util.List<int[]> urlRanges = new java.util.ArrayList<>();

        if (hasUrl) {
            Matcher um = URL.matcher(s);
            while (um.find()) {
                int start = um.start(), end = um.end();

                while (end > start && ".,;:!?)\"'»".indexOf(s.charAt(end - 1)) >= 0) end--;
                if (end <= start) continue;

                if (start > 0 && s.charAt(start - 1) == '@') continue;
                final String link = s.substring(start, end);
                urlRanges.add(new int[]{start, end});
                sp.setSpan(new ClickableSpan() {
                    @Override public void onClick(View widget) { com.talksy.messenger.ui.LinkRouter.open(widget.getContext(), link); }
                    @Override public void updateDrawState(TextPaint ds) {
                        ds.setColor(linkColor);
                        ds.setUnderlineText(true);
                        ds.setFakeBoldText(true);
                    }
                }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        if (hasMention) {
            Matcher m = MENTION.matcher(s);
            while (m.find()) {
                final String handle = m.group();
                boolean insideUrl = false;
                for (int[] r : urlRanges) if (m.start() >= r[0] && m.end() <= r[1]) { insideUrl = true; break; }
                if (insideUrl) continue;
                sp.setSpan(new ClickableSpan() {
                    @Override public void onClick(View widget) {
                        MentionUtils.onMentionTappedPublic(widget.getContext(), apiClient, handle);
                    }
                    @Override public void updateDrawState(TextPaint ds) {
                        ds.setColor(linkColor);
                        ds.setUnderlineText(false);
                        ds.setFakeBoldText(true);
                    }
                }, m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        if (hasSpoiler) {
            Matcher m = SPOILER.matcher(s);
            while (m.find()) {
                final SpoilerSpan spoiler = new SpoilerSpan();
                int start = m.start(), end = m.end();
                sp.setSpan(spoiler, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sp.setSpan(new ClickableSpan() {
                    @Override public void onClick(View widget) {
                        spoiler.revealed = !spoiler.revealed;
                        widget.invalidate();
                    }
                    @Override public void updateDrawState(TextPaint ds) {   }
                }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        tv.setText(sp);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setHighlightColor(Color.TRANSPARENT);
    }

    public static void attachSpoilerComposerAction(EditText et) {
        et.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            private static final int ID = 87421;
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(0, ID, 0, R.string.spoiler_menu_item);
                return true;
            }
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == ID) {
                    int start = et.getSelectionStart(), end = et.getSelectionEnd();
                    if (start < 0 || end < 0 || start == end) { mode.finish(); return true; }
                    if (start > end) { int t = start; start = end; end = t; }
                    android.text.Editable text = et.getText();
                    String selected = text.subSequence(start, end).toString();
                    text.replace(start, end, "||" + selected + "||");
                    Toast.makeText(et.getContext(), R.string.spoiler_hint, Toast.LENGTH_SHORT).show();
                    mode.finish();
                    return true;
                }
                return false;
            }
            @Override public void onDestroyActionMode(ActionMode mode) {}
        });
    }
}
