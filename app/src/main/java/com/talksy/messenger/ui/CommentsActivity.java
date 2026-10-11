package com.talksy.messenger.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.IconManager;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.ui.widget.SettingsUi;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CommentsActivity extends BaseActivity {
    public static final String EXTRA_GROUP_ID = "group_id", EXTRA_POST_ID = "post_id", EXTRA_POST_TEXT = "post_text", EXTRA_CHANNEL = "channel";

    private ApiClient api;
    private long groupId, postId;
    private final List<ApiClient.Comment> items = new ArrayList<>();
    private final List<ApiClient.Comment> confirmed = new ArrayList<>();
    private final List<ApiClient.Comment> pending = new ArrayList<>();
    private com.talksy.messenger.data.CommentsCache cache;
    private boolean sending;
    private long localSeq = -1;
    private RecyclerView rv;
    private TextView txtTitle, txtPost, txtDisabled, txtReplyTo;
    private LinearLayout replyBar, composer;
    private EditText input;
    private long lastId = 0, replyId = 0;
    private boolean canModerate, enabled = true, loading, loadedFromServer;
    private long total;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final Runnable poll = new Runnable() { @Override public void run() { refresh(false); flushPending(); h.postDelayed(this, 5000); } };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        api = new ApiClient(this);
        groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, 0); postId = getIntent().getLongExtra(EXTRA_POST_ID, 0);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(ContextCompat.getColor(this, R.color.bg2));
        bar.setPadding(SettingsUi.dp(this, 8), 0, SettingsUi.dp(this, 8), 0);
        ImageButton back = new ImageButton(this);
        back.setBackgroundResource(R.drawable.ripple_item_round);
        back.setImageResource(R.drawable.ic_back);
        back.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        bar.addView(back, new LinearLayout.LayoutParams(SettingsUi.dp(this, 44), SettingsUi.dp(this, 44)));
        txtTitle = new TextView(this);
        txtTitle.setText(R.string.comments_title); txtTitle.setTextSize(18); txtTitle.setTypeface(txtTitle.getTypeface(), android.graphics.Typeface.BOLD);
        txtTitle.setTextColor(ContextCompat.getColor(this, R.color.text1));
        bar.addView(txtTitle);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 56)));

        LinearLayout postCard = new LinearLayout(this);
        postCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pg = new GradientDrawable(); pg.setCornerRadius(SettingsUi.dp(this, 14)); pg.setColor(ContextCompat.getColor(this, R.color.bg2));
        postCard.setBackground(pg);
        postCard.setPadding(SettingsUi.dp(this, 14), SettingsUi.dp(this, 10), SettingsUi.dp(this, 14), SettingsUi.dp(this, 10));
        TextView ch = new TextView(this);
        ch.setText(getIntent().getStringExtra(EXTRA_CHANNEL)); ch.setTextColor(ContextCompat.getColor(this, R.color.accent)); ch.setTextSize(13);
        ch.setTypeface(ch.getTypeface(), android.graphics.Typeface.BOLD);
        postCard.addView(ch);
        txtPost = new TextView(this);
        String pt = getIntent().getStringExtra(EXTRA_POST_TEXT);
        txtPost.setText(pt == null || pt.isEmpty() ? getString(R.string.comments_title) : pt);
        txtPost.setTextColor(ContextCompat.getColor(this, R.color.text1)); txtPost.setTextSize(14); txtPost.setMaxLines(4);
        txtPost.setEllipsize(android.text.TextUtils.TruncateAt.END);
        postCard.addView(txtPost);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.setMargins(SettingsUi.dp(this, 10), SettingsUi.dp(this, 8), SettingsUi.dp(this, 10), SettingsUi.dp(this, 4));
        root.addView(postCard, pp);

        txtDisabled = new TextView(this);
        txtDisabled.setText(R.string.comments_empty); txtDisabled.setGravity(Gravity.CENTER); txtDisabled.setTextColor(ContextCompat.getColor(this, R.color.text3));
        txtDisabled.setPadding(SettingsUi.dp(this, 24), SettingsUi.dp(this, 40), SettingsUi.dp(this, 24), 0);
        txtDisabled.setVisibility(View.GONE);
        root.addView(txtDisabled);

        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setClipToPadding(false);
        rv.setPadding(SettingsUi.dp(this, 8), SettingsUi.dp(this, 6), SettingsUi.dp(this, 8), SettingsUi.dp(this, 6));
        rv.setAdapter(new Adapter());
        root.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        replyBar = new LinearLayout(this);
        replyBar.setGravity(Gravity.CENTER_VERTICAL);
        replyBar.setBackgroundColor(ContextCompat.getColor(this, R.color.bg2));
        replyBar.setPadding(SettingsUi.dp(this, 14), SettingsUi.dp(this, 6), SettingsUi.dp(this, 6), SettingsUi.dp(this, 6));
        replyBar.setVisibility(View.GONE);
        txtReplyTo = new TextView(this);
        txtReplyTo.setTextColor(ContextCompat.getColor(this, R.color.accent)); txtReplyTo.setTextSize(13); txtReplyTo.setSingleLine(true);
        replyBar.addView(txtReplyTo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton cancel = new ImageButton(this);
        cancel.setBackgroundResource(R.drawable.ripple_item_round); IconManager.set(cancel, "close");
        cancel.setOnClickListener(v -> setReply(null));
        replyBar.addView(cancel, new LinearLayout.LayoutParams(SettingsUi.dp(this, 36), SettingsUi.dp(this, 36)));
        root.addView(replyBar);

        composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setBackgroundColor(ContextCompat.getColor(this, R.color.bg2));
        composer.setPadding(SettingsUi.dp(this, 10), SettingsUi.dp(this, 8), SettingsUi.dp(this, 10), SettingsUi.dp(this, 8));
        input = new EditText(this);
        input.setHint(R.string.comments_hint); input.setHintTextColor(ContextCompat.getColor(this, R.color.text3));
        input.setTextColor(ContextCompat.getColor(this, R.color.text1)); input.setTextSize(15); input.setMaxLines(5);
        input.setBackground(null);
        GradientDrawable ig = new GradientDrawable(); ig.setCornerRadius(SettingsUi.dp(this, 22)); ig.setColor(ContextCompat.getColor(this, R.color.bg3));
        input.setBackground(ig);
        input.setPadding(SettingsUi.dp(this, 16), SettingsUi.dp(this, 10), SettingsUi.dp(this, 16), SettingsUi.dp(this, 10));
        composer.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton send = new ImageButton(this);
        send.setBackgroundResource(R.drawable.bg_btn_record); send.setPadding(SettingsUi.dp(this, 11), SettingsUi.dp(this, 11), SettingsUi.dp(this, 11), SettingsUi.dp(this, 11));
        IconManager.set(send, "send", Color.WHITE);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(SettingsUi.dp(this, 44), SettingsUi.dp(this, 44));
        sp.setMarginStart(SettingsUi.dp(this, 8));
        composer.addView(send, sp);
        send.setOnClickListener(v -> sendNow());
        root.addView(composer);
        setContentView(root);

        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        cache = new com.talksy.messenger.data.CommentsCache(this, groupId, postId);
        com.talksy.messenger.data.CommentsCache.State st = cache.load();
        if (st.hasData) {
            confirmed.addAll(st.comments); pending.addAll(st.pending);
            for (ApiClient.Comment c : confirmed) lastId = Math.max(lastId, c.id);
            enabled = st.enabled; canModerate = st.canModerate; total = st.total;
            if (st.postText != null && !st.postText.isEmpty()) txtPost.setText(st.postText);
            rebuild();
            updateHeader();
        }
    }

    private void rebuild() {
        items.clear(); items.addAll(confirmed); items.addAll(pending);
        rv.getAdapter().notifyDataSetChanged();
    }

    private void updateHeader() {
        long n = Math.max(total, confirmed.size());
        txtTitle.setText(n > 0 ? getResources().getQuantityString(R.plurals.comments_count, (int) n, (int) n) : getString(R.string.comments_title));
        txtDisabled.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        txtDisabled.setText(loadedFromServer && !enabled ? R.string.comments_off : R.string.comments_empty);
        composer.setVisibility(!loadedFromServer || enabled ? View.VISIBLE : View.GONE);
    }

    private void persist() {
        com.talksy.messenger.data.CommentsCache.State st = new com.talksy.messenger.data.CommentsCache.State();
        st.comments.addAll(confirmed); st.pending.addAll(pending); st.enabled = enabled; st.canModerate = canModerate; st.total = total;
        st.postText = txtPost.getText() == null ? "" : txtPost.getText().toString();
        cache.save(st);
    }

    @Override protected void onResume() { super.onResume(); refresh(true); flushPending(); h.postDelayed(poll, 5000); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(poll); }

    private void refresh(boolean first) {
        if (loading) return;
        loading = true;
        api.getComments(groupId, postId, lastId, (ok, d, err) -> {
            loading = false;
            if (!ok || d == null) { updateHeader(); return; }
            loadedFromServer = true;
            canModerate = d.canModerate; enabled = d.enabled; total = d.total;
            if (d.postText != null && !d.postText.isEmpty()) txtPost.setText(d.postText);
            boolean added = false;
            for (ApiClient.Comment c : d.comments) {
                boolean known = false;
                for (ApiClient.Comment x : confirmed) if (x.id == c.id) { x.deleted = c.deleted; x.text = c.text; known = true; break; }
                if (!known) { confirmed.add(c); added = true; }
                lastId = Math.max(lastId, c.id);
            }
            if (added || first) rebuild();
            updateHeader();
            persist();
        });
    }

    private boolean isNearBottom() {
        LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
        return lm == null || items.isEmpty() || lm.findLastVisibleItemPosition() >= items.size() - 3;
    }

    private void setReply(ApiClient.Comment c) {
        replyId = c == null ? 0 : c.id;
        replyBar.setVisibility(c == null ? View.GONE : View.VISIBLE);
        if (c != null) { txtReplyTo.setText(getString(R.string.comments_reply_to, c.senderName)); input.requestFocus(); ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input, 0); }
    }

    private void sendNow() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        long reply = replyId;
        ApiClient.Comment mine = new ApiClient.Comment();
        mine.id = localSeq--; mine.pending = true; mine.mine = true; mine.text = text; mine.timeMs = System.currentTimeMillis();
        mine.senderName = new com.talksy.messenger.data.Prefs(this).get(com.talksy.messenger.data.Prefs.KEY_MY_NAME, "");
        mine.senderColor = new com.talksy.messenger.data.Prefs(this).get(com.talksy.messenger.data.Prefs.KEY_MY_COLOR, "#6366F1");
        mine.replyToId = reply;
        for (ApiClient.Comment x : confirmed) if (x.id == reply) { mine.replyName = x.senderName; mine.replyText = x.text; }
        setReply(null);
        pending.add(mine);
        rebuild(); updateHeader(); persist();
        rv.post(() -> rv.scrollToPosition(Math.max(0, items.size() - 1)));
        flushPending();
    }

    private void flushPending() {
        if (sending || pending.isEmpty()) return;
        final ApiClient.Comment c = pending.get(0);
        sending = true;
        api.sendComment(groupId, postId, c.text, c.replyToId > 0 ? c.replyToId : 0, (ok, sent, err) -> {
            sending = false;
            if (ok) {
                pending.remove(c);
                if (sent != null) { confirmed.add(sent); lastId = Math.max(lastId, sent.id); total++; }
                rebuild(); updateHeader(); persist();
                flushPending();
                return;
            }

            if (err != null && (err.contains("отключены") || err.contains("подписчики") || err.contains("Пустой") || err.contains("длинный") || err.contains("Нет доступа"))) {
                pending.remove(c);
                Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                rebuild(); updateHeader(); persist();
            }
        });
    }

    private void actions(ApiClient.Comment c) {
        if (c.deleted) return;
        List<CharSequence> labels = new ArrayList<>(); final List<Integer> ids = new ArrayList<>();
        labels.add(getString(R.string.comment_reply)); ids.add(0);
        labels.add(getString(R.string.copy)); ids.add(1);
        if (c.canDelete || canModerate && !c.deleted) { labels.add(getString(R.string.comment_delete)); ids.add(2); }
        new TDialog.Builder(this).setItems(labels.toArray(new CharSequence[0]), (d, which) -> {
            int a = ids.get(which);
            if (a == 0) setReply(c);
            else if (a == 1) LinkRouter.copy(this, c.text);
            else api.deleteComment(groupId, c.id, (ok, v, e) -> {
                if (ok) { c.deleted = true; c.text = ""; rv.getAdapter().notifyDataSetChanged(); persist(); Toast.makeText(this, R.string.comment_deleted, Toast.LENGTH_SHORT).show(); }
                else Toast.makeText(this, e != null ? e : getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
            });
        }).show();
    }

    private int colorOf(String hex) { try { return Color.parseColor(hex); } catch (Exception e) { return ContextCompat.getColor(this, R.color.accent); } }

    private final class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        final class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            LinearLayout row = new LinearLayout(CommentsActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(SettingsUi.dp(CommentsActivity.this, 4), SettingsUi.dp(CommentsActivity.this, 4), SettingsUi.dp(CommentsActivity.this, 40), SettingsUi.dp(CommentsActivity.this, 4));
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(row);
        }

        @Override public void onBindViewHolder(VH vh, int pos) {
            ApiClient.Comment c = items.get(pos);
            LinearLayout row = (LinearLayout) vh.itemView;
            row.removeAllViews();
            int dp36 = SettingsUi.dp(CommentsActivity.this, 36);
            TextView av = new TextView(CommentsActivity.this);
            av.setText(c.senderName == null || c.senderName.isEmpty() ? "?" : c.senderName.substring(0, 1).toUpperCase());
            av.setGravity(Gravity.CENTER); av.setTextColor(Color.WHITE); av.setTextSize(15); av.setTypeface(av.getTypeface(), android.graphics.Typeface.BOLD);
            GradientDrawable ag = new GradientDrawable(); ag.setShape(GradientDrawable.OVAL); ag.setColor(colorOf(c.senderColor)); av.setBackground(ag);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp36, dp36); ap.setMarginEnd(SettingsUi.dp(CommentsActivity.this, 8));
            ap.gravity = Gravity.BOTTOM;
            row.addView(av, ap);

            LinearLayout bubble = new LinearLayout(CommentsActivity.this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(SettingsUi.dp(CommentsActivity.this, 16));
            bg.setColor(ContextCompat.getColor(CommentsActivity.this, c.mine ? R.color.bg3 : R.color.bg2));
            bubble.setBackground(bg);
            int pad = SettingsUi.dp(CommentsActivity.this, 12);
            bubble.setPadding(pad, SettingsUi.dp(CommentsActivity.this, 8), pad, SettingsUi.dp(CommentsActivity.this, 6));
            TextView name = new TextView(CommentsActivity.this);
            name.setText(c.senderName); name.setTextSize(13); name.setTextColor(colorOf(c.senderColor)); name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
            bubble.addView(name);
            if (c.replyToId > 0 && c.replyName != null && !c.replyName.isEmpty()) {
                LinearLayout q = new LinearLayout(CommentsActivity.this);
                q.setOrientation(LinearLayout.VERTICAL);
                GradientDrawable qg = new GradientDrawable(); qg.setCornerRadius(SettingsUi.dp(CommentsActivity.this, 8)); qg.setColor(0x226366F1); q.setBackground(qg);
                q.setPadding(SettingsUi.dp(CommentsActivity.this, 8), SettingsUi.dp(CommentsActivity.this, 4), SettingsUi.dp(CommentsActivity.this, 8), SettingsUi.dp(CommentsActivity.this, 4));
                TextView qn = new TextView(CommentsActivity.this); qn.setText(c.replyName); qn.setTextSize(12); qn.setTextColor(ContextCompat.getColor(CommentsActivity.this, R.color.accent));
                qn.setTypeface(qn.getTypeface(), android.graphics.Typeface.BOLD);
                TextView qt = new TextView(CommentsActivity.this); qt.setText(c.replyText); qt.setTextSize(12); qt.setSingleLine(true); qt.setEllipsize(android.text.TextUtils.TruncateAt.END);
                qt.setTextColor(ContextCompat.getColor(CommentsActivity.this, R.color.text2));
                q.addView(qn); q.addView(qt);
                LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                qp.topMargin = SettingsUi.dp(CommentsActivity.this, 3);
                bubble.addView(q, qp);
            }
            TextView body = new TextView(CommentsActivity.this);
            body.setText(c.deleted ? getString(R.string.comment_deleted) : c.text);
            body.setTextSize(15); body.setTextColor(ContextCompat.getColor(CommentsActivity.this, c.deleted ? R.color.text3 : R.color.text1));
            if (!c.deleted) com.talksy.messenger.data.MessageTextUtils.render(body, api);
            bubble.addView(body);
            TextView time = new TextView(CommentsActivity.this);
            if (c.pending) {
                android.text.SpannableStringBuilder tb = new android.text.SpannableStringBuilder(fmt.format(new Date(c.timeMs)));
                tb.append("  ").append(IconManager.inline(CommentsActivity.this, "pending", 13, IconManager.tint(CommentsActivity.this)));
                time.setText(tb);
            } else time.setText(fmt.format(new Date(c.timeMs)));
            time.setTextSize(10); time.setTextColor(ContextCompat.getColor(CommentsActivity.this, R.color.text3));
            time.setGravity(Gravity.END);
            bubble.addView(time, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(bubble);
            row.setOnClickListener(v -> { if (!c.deleted) setReply(c); });
            row.setOnLongClickListener(v -> { actions(c); return true; });
            bubble.setOnLongClickListener(v -> { actions(c); return true; });
        }

        @Override public int getItemCount() { return items.size(); }
    }
}
