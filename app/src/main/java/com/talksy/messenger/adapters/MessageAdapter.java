package com.talksy.messenger.adapters;

import com.talksy.messenger.data.IconManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.model.InlineKeyboard;
import com.talksy.messenger.data.model.MediaAttachment;
import com.talksy.messenger.data.model.Message;
import com.talksy.messenger.data.model.Reaction;
import com.talksy.messenger.net.AvatarLoader;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_OUT = 1;
    private static final int TYPE_IN = 2;

    public interface OnLongPress { void onLongPress(Message message); }
    public interface OnMediaClick { void onMediaClick(MediaAttachment media); }
    public interface OnKeyboardButtonClick { void onClick(Message message, InlineKeyboard.Button button); }

    private final List<Message> items;
    private final String baseUrl;
    private final OnLongPress longPressListener;
    private final OnMediaClick mediaClickListener;
    private OnKeyboardButtonClick keyboardButtonListener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public MessageAdapter(List<Message> items, String baseUrl, OnLongPress longPressListener, OnMediaClick mediaClickListener) {
        this.items = items;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.longPressListener = longPressListener;
        this.mediaClickListener = mediaClickListener;
    }

    public void setOnKeyboardButtonClick(OnKeyboardButtonClick l) {
        this.keyboardButtonListener = l;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).outgoing ? TYPE_OUT : TYPE_IN;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_OUT ? R.layout.item_message_out : R.layout.item_message_in;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message m = items.get(position);
        VH h = (VH) holder;

        if (position == items.size() - 1) com.talksy.messenger.data.AnimUtils.popIn(h.itemView);

        if (m.deletedForEveryone) {
            h.text.setText(h.text.getContext().getString(R.string.message_deleted));
            h.text.setTypeface(h.text.getTypeface(), android.graphics.Typeface.ITALIC);
            h.text.setAlpha(0.6f);
            h.text.setVisibility(View.VISIBLE);
            if (h.specialCard != null) h.specialCard.setVisibility(View.GONE);
            h.time.setText(fmt.format(m.time));
            bindMedia(h.frameImage, h.imgMedia, h.frameSticker, h.imgSticker, h.videoSticker, h.rowFile, h.imgFileIcon, h.txtFileName, h.txtFileSize, null);
            VideoBubble.applyTime(h.frameImage, h.time, false);
            h.reactions.setVisibility(View.GONE);
            if (h.voiceBubble != null) h.voiceBubble.setVisibility(View.GONE);
            if (h.roundVideo != null) h.roundVideo.setVisibility(View.GONE);
            if (h.containerKeyboard != null) h.containerKeyboard.setVisibility(View.GONE);
            if (h.frameGift != null) h.frameGift.setVisibility(View.GONE);
            View.OnLongClickListener onLongDeleted = v -> { longPressListener.onLongPress(m); return true; };
            h.itemView.setOnLongClickListener(onLongDeleted);
            h.text.setOnLongClickListener(onLongDeleted);
            View.OnClickListener onTapDeleted = v -> longPressListener.onLongPress(m);
            h.text.setOnClickListener(onTapDeleted);
            h.time.setOnClickListener(onTapDeleted);
            return;
        }
        h.text.setTypeface(h.text.getTypeface(), android.graphics.Typeface.NORMAL);
        h.text.setAlpha(1f);

        bindText(h.text, m);

        final com.talksy.messenger.data.SpecialMessage special = com.talksy.messenger.data.SpecialMessage.parse(m.text);
        if (h.specialCard != null) {
            if (special != null) { h.specialCard.bind(special, m.outgoing); h.specialCard.setVisibility(View.VISIBLE);
                h.specialCard.setOnLongClickListener(v -> { longPressListener.onLongPress(m); return true; }); }
            else h.specialCard.setVisibility(View.GONE);
        }
        h.text.setVisibility(special != null || m.text == null || m.text.isEmpty() ? View.GONE : View.VISIBLE);
        if (special == null) com.talksy.messenger.data.MessageTextUtils.render(h.text, new com.talksy.messenger.net.ApiClient(h.text.getContext()), m.outgoing);
        com.talksy.messenger.data.LiquidGlass.applyBubble(h.text, m.outgoing
                ? androidx.core.content.ContextCompat.getColor(h.text.getContext(), R.color.accent) : null, m.outgoing);
        bindTime(h.time, m);
        if (h.containerKeyboard != null) bindKeyboard(h.containerKeyboard, m);

        bindMedia(h.frameImage, h.imgMedia, h.frameSticker, h.imgSticker, h.videoSticker, h.rowFile, h.imgFileIcon, h.txtFileName, h.txtFileSize, m.media);
        VideoBubble.applyTime(h.frameImage, h.time,
                m.media != null && m.media.isVideo() && (m.text == null || m.text.isEmpty()));
        bindVoiceAndRound(h, m);
        bindUpload(h, m);
        bindGift(h, m);

        if (m.reactions == null || m.reactions.isEmpty()) {
            h.reactions.setVisibility(View.GONE);
        } else {
            StringBuilder sb = new StringBuilder();
            for (Reaction r : m.reactions) sb.append(r.emoji).append(" ").append(r.count).append("  ");
            h.reactions.setText(sb.toString().trim());
            h.reactions.setVisibility(View.VISIBLE);
            h.reactions.setOnClickListener(v -> longPressListener.onLongPress(m));
        }

        View.OnLongClickListener onLong = v -> { longPressListener.onLongPress(m); return true; };
        h.text.setOnLongClickListener(onLong);
        h.itemView.setOnLongClickListener(onLong);
        View.OnClickListener onTap = v -> longPressListener.onLongPress(m);
        h.text.setOnClickListener(onTap);
        h.time.setOnClickListener(onTap);
    }

    private void bindUpload(VH h, Message m) {
        if (h.progress == null) return;
        if (!m.uploading) {
            h.progress.setVisibility(View.GONE);
            return;
        }
        h.rowFile.setVisibility(View.VISIBLE);
        h.imgFileIcon.setImageResource(R.drawable.ic_file);
        h.txtFileName.setText(m.uploadName);
        h.progress.setVisibility(View.VISIBLE);
        applyProgress(h, m);
    }

    private void applyProgress(VH h, Message m) {
        long total = Math.max(m.uploadTotal, 1);
        int pct = (int) Math.min(100, m.uploadDone * 100 / total);
        h.progress.setMax(100);
        h.progress.setProgress(pct, true);
        h.txtFileSize.setText(h.txtFileSize.getContext().getString(
                R.string.upload_progress, mb(m.uploadDone), mb(total), pct));
    }

    public void refreshProgress(RecyclerView rv, int position) {
        if (position < 0 || position >= items.size()) return;
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(position);
        if (vh instanceof VH) {
            Message m = items.get(position);
            VH h = (VH) vh;
            if (m.uploading && h.progress != null) applyProgress(h, m);
        }
    }

    private static String mb(long bytes) {
        return String.format(Locale.getDefault(), "%.1f", bytes / 1048576.0);
    }

    private void bindText(TextView tv, Message m) {
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, chatFontSizeSp(tv.getContext()));
        if ((m.replyToId <= 0 && m.storyId <= 0) || m.text == null || m.text.isEmpty()) { tv.setText(m.text); return; }
        String quote = m.storyId > 0
                ? "\u00A0 " + tv.getContext().getString(R.string.story_reply_label)
                : "\u00A0 " + replyPreview(tv.getContext(), m.replyToId);
        int c = (tv.getCurrentTextColor() & 0x00FFFFFF) | 0xB0000000;
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(quote + "\n" + m.text);
        sb.replace(0, 1, IconManager.inline(tv.getContext(), m.storyId > 0 ? "photo" : "reply", 12, c));
        int flag = android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        sb.setSpan(new android.text.style.ForegroundColorSpan(c), 0, quote.length(), flag);
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.85f), 0, quote.length(), flag);
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), 0, quote.length(), flag);
        tv.setText(sb);
    }

    private static float chatFontSizeSp(android.content.Context ctx) {
        String v = new com.talksy.messenger.data.Prefs(ctx).get(com.talksy.messenger.data.Prefs.KEY_FONT_SIZE, "medium");
        if ("small".equals(v)) return 13f;
        if ("large".equals(v)) return 18f;
        return 15f;
    }

    private String replyPreview(android.content.Context ctx, long id) {
        for (Message x : items) {
            if (x.id != id) continue;
            if (x.deletedForEveryone) return ctx.getString(R.string.message_deleted);
            String t = x.text == null ? "" : x.text.replace('\n', ' ').trim();
            if (t.isEmpty() && x.media != null) t = (x.media.filename == null || x.media.filename.isEmpty()) ? ctx.getString(R.string.attach_file) : x.media.filename;
            if (t.length() > 60) t = t.substring(0, 60) + "…";
            return t;
        }
        return ctx.getString(R.string.reply_original_missing);
    }

    private void bindTime(TextView tv, Message m) {
        if (m.uploading) { tv.setText(IconManager.inline(tv.getContext(), "pending", 15, IconManager.tint(tv.getContext()))); return; }
        String t = (m.edited ? tv.getContext().getString(R.string.edited_mark) + " " : "") + fmt.format(m.time);
        if (!m.outgoing) { tv.setText(t); return; }
        int color = IconManager.tint(tv.getContext());
        android.text.SpannableStringBuilder s = new android.text.SpannableStringBuilder(t);
        if (com.talksy.messenger.sync.SyncStatus.FAILED.equals(m.syncStatus)) {
            int start = s.length();
            s.append("  !");
            s.setSpan(new android.text.style.ForegroundColorSpan(0xFFE5484D), start, s.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (m.id < 0 || com.talksy.messenger.sync.SyncStatus.PENDING.equals(m.syncStatus)
                || com.talksy.messenger.sync.SyncStatus.SENDING.equals(m.syncStatus)) {
            s.append("  ").append(IconManager.inline(tv.getContext(), "pending", 15, color));
        } else {
            s.append("  ").append(IconManager.inline(tv.getContext(), m.read ? "check_double" : "check_single", 15, color));
        }
        tv.setText(s);
    }

    private void bindVoiceAndRound(VH h, Message m) {
        boolean voice = m.media != null && m.media.isVoice();
        boolean round = m.media != null && m.media.isRound();
        if (h.voiceBubble != null) h.voiceBubble.setVisibility(voice ? View.VISIBLE : View.GONE);
        if (h.roundVideo != null) h.roundVideo.setVisibility(round ? View.VISIBLE : View.GONE);
        if (voice || round) {
            h.rowFile.setVisibility(View.GONE);
            h.frameImage.setVisibility(View.GONE);
        }
        String key = "m" + (m.localId > 0 ? m.localId : m.id);
        String base = new com.talksy.messenger.net.ApiClient(h.time.getContext()).getBaseUrl();
        if (voice && h.voiceBubble != null) {
            h.voiceBubble.bind(key, m.media, base, m.outgoing);
            h.voiceBubble.setOnLongClickListener(v -> { longPressListener.onLongPress(m); return true; });
        }
        if (round && h.roundVideo != null) {
            h.roundVideo.bind(key, m.media, base);
            h.roundVideo.setOnLongClickListener(v -> { longPressListener.onLongPress(m); return true; });
        }
    }

    private void bindMedia(View frameImage, ImageView imgMedia, View frameSticker, ImageView imgSticker,
                            android.widget.VideoView videoSticker, View rowFile, ImageView imgFileIcon,
                            TextView txtFileName, TextView txtFileSize, MediaAttachment media) {
        frameImage.setVisibility(View.GONE);
        rowFile.setVisibility(View.GONE);
        imgMedia.setImageBitmap(null);
        VideoBubble.reset(frameImage);
        if (frameSticker != null) frameSticker.setVisibility(View.GONE);
        if (videoSticker != null) { videoSticker.stopPlayback(); videoSticker.setVisibility(View.GONE); }
        if (media == null || media.isGift()) return;

        if (media.isStickerImage() || media.isStickerVideo()) {
            if (frameSticker == null) return;
            frameSticker.setVisibility(View.VISIBLE);
            final String stickerMediaUrl = media.url;
            frameSticker.setOnClickListener(v -> com.talksy.messenger.ui.StickerShare.onTap(v.getContext(), stickerMediaUrl));
            if (media.isStickerImage()) {
                imgSticker.setVisibility(View.VISIBLE);
                if (videoSticker != null) videoSticker.setVisibility(View.GONE);
                AvatarLoader.loadRaw(baseUrl + media.url, 400, com.talksy.messenger.data.TrafficStats.CAT_STICKERS, bmp -> imgSticker.setImageBitmap(bmp));
            } else if (videoSticker != null) {
                imgSticker.setVisibility(View.GONE);
                videoSticker.setVisibility(View.VISIBLE);
                final String stickerUrl = baseUrl + media.url;
                videoSticker.setTag(stickerUrl);
                videoSticker.setOnPreparedListener(mp -> {
                    mp.setLooping(true);
                    mp.setVolume(0f, 0f);
                    videoSticker.start();
                });
                com.talksy.messenger.net.MediaCache.withLocalPath(stickerUrl, com.talksy.messenger.data.TrafficStats.CAT_STICKERS, path -> {
                    if (path != null && stickerUrl.equals(videoSticker.getTag())) videoSticker.setVideoPath(path);
                });
            }
            return;
        }

        if (media.isImage()) {
            frameImage.setVisibility(View.VISIBLE);
            final boolean hidden = media.isHidden();
            AvatarLoader.loadRaw(baseUrl + media.url, 900,
                    bmp -> imgMedia.setImageBitmap(hidden ? com.talksy.messenger.data.BlurUtils.blur(bmp) : bmp));
            frameImage.setOnClickListener(v -> {
                if (hidden) { MediaAttachment.REVEALED.add(media.url); notifyDataSetChanged(); }
                else mediaClickListener.onMediaClick(media);
            });
        } else if (media.isVideo()) {
            frameImage.setVisibility(View.VISIBLE);
            final boolean hidden = media.isHidden();
            VideoBubble.bind(frameImage, imgMedia, baseUrl + media.url, hidden);
            frameImage.setOnClickListener(v -> {
                if (hidden) { MediaAttachment.REVEALED.add(media.url); notifyDataSetChanged(); }
                else mediaClickListener.onMediaClick(media);
            });
        } else {
            rowFile.setVisibility(View.VISIBLE);
            final boolean hidden = media.isHidden();
            imgFileIcon.setImageResource(media.isVideo() ? R.drawable.ic_video : R.drawable.ic_file);
            txtFileName.setText(hidden ? IconManager.prefix(txtFileName.getContext(), "secure_chat", txtFileName.getContext().getString(R.string.spoiler_hint)) : media.displayName());
            txtFileSize.setText(media.sizeText(txtFileSize.getContext()));
            rowFile.setOnClickListener(v -> {
                if (hidden) { MediaAttachment.REVEALED.add(media.url); notifyDataSetChanged(); }
                else mediaClickListener.onMediaClick(media);
            });
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    public Message getMessageAt(int position) { return items.get(position); }

    private void bindKeyboard(android.widget.LinearLayout container, Message m) {
        InlineKeyboard kb = InlineKeyboard.parse(m.replyMarkup);
        container.removeAllViews();
        if (kb == null) { container.setVisibility(View.GONE); return; }
        container.setVisibility(View.VISIBLE);
        android.content.Context ctx = container.getContext();
        float dp = ctx.getResources().getDisplayMetrics().density;

        for (List<InlineKeyboard.Button> row : kb.rows) {
            android.widget.LinearLayout rowLayout = new android.widget.LinearLayout(ctx);
            rowLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            android.widget.LinearLayout.LayoutParams rowLp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = (int) (4 * dp);
            rowLayout.setLayoutParams(rowLp);

            for (InlineKeyboard.Button btn : row) {
                TextView tv = new TextView(ctx);
                int accentC = androidx.core.content.ContextCompat.getColor(ctx, R.color.accent);
                CharSequence label = btn.miniApp ? IconManager.prefix(ctx, "mini_app", btn.text, accentC, 14)
                        : (btn.url != null ? IconManager.prefix(ctx, "link", btn.text, accentC, 14) : btn.text);
                tv.setText(label);
                tv.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.accent));
                tv.setTextSize(13f);
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setBackgroundResource(R.drawable.bg_keyboard_button);
                tv.setPadding((int) (10 * dp), (int) (9 * dp), (int) (10 * dp), (int) (9 * dp));
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                        0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMarginStart((int) (3 * dp));
                lp.setMarginEnd((int) (3 * dp));
                tv.setLayoutParams(lp);
                com.talksy.messenger.data.AnimUtils.pressScale(tv);
                tv.setOnClickListener(v -> { if (keyboardButtonListener != null) keyboardButtonListener.onClick(m, btn); });
                rowLayout.addView(tv);
            }
            container.addView(rowLayout);
        }
    }

    private void bindGift(VH h, Message m) {
        if (h.frameGift == null) return;
        boolean gift = m.media != null && m.media.isGift();
        h.frameGift.setVisibility(gift ? View.VISIBLE : View.GONE);
        if (!gift) return;
        android.content.Context c = h.itemView.getContext();
        com.talksy.messenger.net.ApiClient api = new com.talksy.messenger.net.ApiClient(c);
        final String giftId = m.media.filename == null ? "" : m.media.filename;
        final boolean premium = "premium".equals(giftId);
        final String tag = "gift-" + m.id;
        h.frameGift.setTag(tag);
        h.frameGift.setBackground(com.talksy.messenger.ui.EconomyUi.round(c,
                androidx.core.content.ContextCompat.getColor(c, m.outgoing ? R.color.bg3 : R.color.bg2), 18));
        h.btnGiftView.setBackground(com.talksy.messenger.ui.EconomyUi.round(c,
                androidx.core.content.ContextCompat.getColor(c, R.color.bg4), 16));
        h.frameImage.setVisibility(View.GONE);
        h.rowFile.setVisibility(View.GONE);
        h.text.setVisibility(View.GONE);

        if (premium) {
            int own = com.talksy.messenger.ui.EconomyUi.resId(c, "eco_premium");
            if (own != 0) { h.imgGift.clearColorFilter(); h.imgGift.setImageResource(own); }
            else com.talksy.messenger.data.IconManager.set(h.imgGift, "premium");
            h.txtGiftLabel.setText(m.outgoing ? "Вы подарили Talksy Premium" : "Вам подарили Talksy Premium");
            h.txtGiftSub.setText("на " + m.media.size + " мес.");
            h.btnGiftView.setVisibility(View.VISIBLE);
            h.btnGiftView.setOnClickListener(v -> c.startActivity(new android.content.Intent(c, com.talksy.messenger.ui.PremiumActivity.class)));
            h.frameGift.setOnClickListener(null);
            h.frameGift.setOnLongClickListener(v -> { longPressListener.onLongPress(m); return true; });
            return;
        }

        com.talksy.messenger.ui.EconomyUi.giftImage(c, h.imgGift, api, giftId, null);
        h.txtGiftLabel.setText(m.outgoing ? "Ваш подарок" : "Подарок для Вас");
        h.txtGiftSub.setText(m.text == null ? "" : m.text);
        final long itemId = parseGiftItemId(m.media.url);

        if (itemId > 0) com.talksy.messenger.ui.GiftFlow.fetchItem(api, itemId, (ok, item, err) -> {
            if (!ok || item == null || !tag.equals(h.frameGift.getTag())) return;
            String from = item.optString("from_name");
            h.txtGiftLabel.setText(m.outgoing ? "Ваш подарок" : (from.isEmpty() ? "Подарок для Вас" : "Подарок от " + from));
            String msg = item.optString("message");
            h.txtGiftSub.setText(!msg.isEmpty() ? msg : item.optJSONObject("gift").optString("name"));
            if (item.optBoolean("sold")) h.txtGiftSub.setText("Подарок продан");
        });
        h.btnGiftView.setVisibility(View.VISIBLE);
        View.OnClickListener open = v -> {
            if (itemId > 0 && c instanceof android.app.Activity)
                com.talksy.messenger.ui.GiftFlow.openFromChat((android.app.Activity) c, api, itemId);
        };
        h.btnGiftView.setOnClickListener(open);
        h.frameGift.setOnClickListener(open);
        h.frameGift.setOnLongClickListener(v -> { longPressListener.onLongPress(m); return true; });
    }

    private static long parseGiftItemId(String url) {
        try { return url != null && url.startsWith("gift:") ? Long.parseLong(url.substring(5)) : 0; }
        catch (NumberFormatException e) { return 0; }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView text, time, reactions, txtFileName, txtFileSize;
        View frameImage, rowFile, frameSticker;
        com.talksy.messenger.ui.widget.VoiceBubbleView voiceBubble;
        com.talksy.messenger.ui.widget.SpecialCardView specialCard;
        com.talksy.messenger.ui.widget.RoundVideoView roundVideo;
        ImageView imgMedia, imgFileIcon, imgSticker;
        android.widget.VideoView videoSticker;
        android.widget.ProgressBar progress;
        android.widget.LinearLayout containerKeyboard;
        View frameGift;
        ImageView imgGift;
        TextView txtGiftLabel, txtGiftSub, btnGiftView;
        VH(View v) {
            super(v);
            text = v.findViewById(R.id.txt_msg);
            time = v.findViewById(R.id.txt_time);
            reactions = v.findViewById(R.id.txt_reactions);
            frameImage = v.findViewById(R.id.frame_media_image);
            imgMedia = v.findViewById(R.id.img_media);
            rowFile = v.findViewById(R.id.row_media_file);
            imgFileIcon = v.findViewById(R.id.img_file_icon);
            txtFileName = v.findViewById(R.id.txt_file_name);
            txtFileSize = v.findViewById(R.id.txt_file_size);
            progress = v.findViewById(R.id.progress_upload);
            containerKeyboard = v.findViewById(R.id.container_keyboard);
            frameSticker = v.findViewById(R.id.frame_sticker);
            imgSticker = v.findViewById(R.id.img_sticker);
            videoSticker = v.findViewById(R.id.video_sticker);
            voiceBubble = v.findViewById(R.id.voice_bubble);
            specialCard = v.findViewById(R.id.special_card);
            roundVideo = v.findViewById(R.id.round_video);
            frameGift = v.findViewById(R.id.frame_gift);
            imgGift = v.findViewById(R.id.img_gift);
            txtGiftLabel = v.findViewById(R.id.txt_gift_label);
            txtGiftSub = v.findViewById(R.id.txt_gift_sub);
            btnGiftView = v.findViewById(R.id.btn_gift_view);
        }
    }
}
