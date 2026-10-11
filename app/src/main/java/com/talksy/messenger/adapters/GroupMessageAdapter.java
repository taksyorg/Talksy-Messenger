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
import com.talksy.messenger.data.model.GroupMessage;
import com.talksy.messenger.data.model.MediaAttachment;
import com.talksy.messenger.data.model.Reaction;
import com.talksy.messenger.net.AvatarLoader;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class GroupMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_OUT = 1;
    private static final int TYPE_IN = 2;

    public interface OnLongPress { void onLongPress(GroupMessage message); }
    public interface OnMediaClick { void onMediaClick(MediaAttachment media); }

    public interface OnCommentsClick { void onCommentsClick(GroupMessage post); }
    public interface OnPostShown { void onPostShown(GroupMessage post); }

    private boolean channelMode, commentsEnabled;
    private OnCommentsClick commentsClick;
    private OnPostShown postShown;

    public void setChannelMode(boolean channel, boolean commentsOn, OnCommentsClick click, OnPostShown shown) {
        this.channelMode = channel; this.commentsEnabled = commentsOn; this.commentsClick = click; this.postShown = shown;
    }

    private final List<GroupMessage> items;
    private final String baseUrl;
    private final boolean reactionsEnabled;
    private final OnLongPress longPressListener;
    private final OnMediaClick mediaClickListener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public GroupMessageAdapter(List<GroupMessage> items, String baseUrl, boolean reactionsEnabled,
                                OnLongPress longPressListener, OnMediaClick mediaClickListener) {
        this.items = items;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.reactionsEnabled = reactionsEnabled;
        this.longPressListener = longPressListener;
        this.mediaClickListener = mediaClickListener;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).outgoing ? TYPE_OUT : TYPE_IN;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_OUT ? R.layout.item_group_message_out : R.layout.item_group_message_in;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return viewType == TYPE_OUT ? new VHOut(v) : new VHIn(v);
    }

    static String compact(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return String.format(Locale.getDefault(), "%.1fK", n / 1000.0).replace(".0", "");
        return String.format(Locale.getDefault(), "%.1fM", n / 1_000_000.0).replace(".0", "");
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        GroupMessage m = items.get(position);
        TextView text, time, reactions, txtFileName, txtFileSize;
        View frameImage, rowFile, frameSticker;
        ImageView imgMedia, imgFileIcon, imgSticker;
        android.widget.VideoView videoSticker;

        if (position == items.size() - 1) com.talksy.messenger.data.AnimUtils.popIn(holder.itemView);

        if (holder instanceof VHOut) {
            VHOut h = (VHOut) holder;
            text = h.text; time = h.time; reactions = h.reactions;
            frameImage = h.frameImage; imgMedia = h.imgMedia; rowFile = h.rowFile;
            imgFileIcon = h.imgFileIcon; txtFileName = h.txtFileName; txtFileSize = h.txtFileSize;
            frameSticker = h.frameSticker; imgSticker = h.imgSticker; videoSticker = h.videoSticker;
        } else {
            VHIn h = (VHIn) holder;
            h.sender.setText(m.senderName);
            text = h.text; time = h.time; reactions = h.reactions;
            frameImage = h.frameImage; imgMedia = h.imgMedia; rowFile = h.rowFile;
            imgFileIcon = h.imgFileIcon; txtFileName = h.txtFileName; txtFileSize = h.txtFileSize;
            frameSticker = h.frameSticker; imgSticker = h.imgSticker; videoSticker = h.videoSticker;
        }

        text.setText(m.text);
        final com.talksy.messenger.data.SpecialMessage special = com.talksy.messenger.data.SpecialMessage.parse(m.text);
        com.talksy.messenger.ui.widget.SpecialCardView sc = holder instanceof VHOut ? ((VHOut) holder).specialCard : ((VHIn) holder).specialCard;
        if (sc != null) {
            if (special != null) { sc.bind(special, m.outgoing); sc.setVisibility(View.VISIBLE); sc.setOnLongClickListener(v -> { longPressListener.onLongPress(m); return true; }); }
            else sc.setVisibility(View.GONE);
        }
        text.setVisibility(special != null || m.text == null || m.text.isEmpty() ? View.GONE : View.VISIBLE);
        if (special == null) com.talksy.messenger.data.MessageTextUtils.render(text, new com.talksy.messenger.net.ApiClient(text.getContext()), m.outgoing);
        com.talksy.messenger.data.LiquidGlass.applyBubble(text, m.outgoing
                ? androidx.core.content.ContextCompat.getColor(text.getContext(), R.color.accent) : null, m.outgoing);
        boolean postInChannel = channelMode && m.id > 0 && !m.deletedForEveryone;
        TextView txtComments = holder instanceof VHOut ? ((VHOut) holder).txtComments : ((VHIn) holder).txtComments;
        if (txtComments != null) {
            if (postInChannel && commentsEnabled) {
                android.content.Context cc = txtComments.getContext();
                String label = m.commentsCount > 0 ? cc.getResources().getQuantityString(R.plurals.comments_count, (int) m.commentsCount, (int) m.commentsCount)
                        : cc.getString(R.string.comments_leave);
                txtComments.setText(com.talksy.messenger.data.IconManager.prefix(cc, "secure_chat", label + "  ›", androidx.core.content.ContextCompat.getColor(cc, R.color.accent), 15));
                txtComments.setVisibility(View.VISIBLE);
                txtComments.setOnClickListener(v -> { if (commentsClick != null) commentsClick.onCommentsClick(m); });
            } else txtComments.setVisibility(View.GONE);
        }
        if (postInChannel && postShown != null) postShown.onPostShown(m);
        if (postInChannel) {
            android.content.Context tc = time.getContext();
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
            sb.append(com.talksy.messenger.data.IconManager.inline(tc, "eye", 13, com.talksy.messenger.data.IconManager.tint(tc)));
            sb.append(" ").append(compact(Math.max(m.views, 1))).append("   ").append(fmt.format(m.time));
            time.setText(sb);
        } else if (m.outgoing && !m.deletedForEveryone) {
            int tickColor = IconManager.tint(time.getContext());
            android.text.SpannableStringBuilder ts = new android.text.SpannableStringBuilder(fmt.format(m.time));
            if (com.talksy.messenger.sync.SyncStatus.FAILED.equals(m.syncStatus)) {
                int st = ts.length();
                ts.append("  !");
                ts.setSpan(new android.text.style.ForegroundColorSpan(0xFFE5484D), st, ts.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.id < 0 || com.talksy.messenger.sync.SyncStatus.PENDING.equals(m.syncStatus)
                    || com.talksy.messenger.sync.SyncStatus.SENDING.equals(m.syncStatus)) {
                ts.append("  ").append(IconManager.inline(time.getContext(), "pending", 15, tickColor));
            } else {
                ts.append("  ").append(IconManager.inline(time.getContext(), "check_single", 15, tickColor));
            }
            time.setText(ts);
        } else {
            time.setText(fmt.format(m.time));
        }

        if (m.deletedForEveryone) {
            text.setText(text.getContext().getString(R.string.message_deleted));
            text.setTypeface(text.getTypeface(), android.graphics.Typeface.ITALIC);
            text.setAlpha(0.6f);
            text.setVisibility(View.VISIBLE);
            { com.talksy.messenger.ui.widget.SpecialCardView dsc = holder instanceof VHOut ? ((VHOut) holder).specialCard : ((VHIn) holder).specialCard; if (dsc != null) dsc.setVisibility(View.GONE); }
            frameImage.setVisibility(View.GONE);
            rowFile.setVisibility(View.GONE);
            com.talksy.messenger.ui.widget.VoiceBubbleView dvb = holder instanceof VHOut ? ((VHOut) holder).voiceBubble : ((VHIn) holder).voiceBubble;
            com.talksy.messenger.ui.widget.RoundVideoView drv = holder instanceof VHOut ? ((VHOut) holder).roundVideo : ((VHIn) holder).roundVideo;
            if (dvb != null) dvb.setVisibility(View.GONE);
            if (drv != null) drv.setVisibility(View.GONE);
            if (frameSticker != null) frameSticker.setVisibility(View.GONE);
            if (videoSticker != null) { videoSticker.stopPlayback(); videoSticker.setVisibility(View.GONE); }
            VideoBubble.applyTime(frameImage, time, false);
            reactions.setVisibility(View.GONE);
            View.OnLongClickListener onLongDeleted = v -> { longPressListener.onLongPress(m); return true; };
            holder.itemView.setOnLongClickListener(onLongDeleted);
            text.setOnLongClickListener(onLongDeleted);
            text.setOnClickListener(v -> longPressListener.onLongPress(m));
            return;
        }
        text.setTypeface(text.getTypeface(), android.graphics.Typeface.NORMAL);
        text.setAlpha(1f);

        frameImage.setVisibility(View.GONE);
        rowFile.setVisibility(View.GONE);
        imgMedia.setImageBitmap(null);
        VideoBubble.reset(frameImage);
        if (frameSticker != null) frameSticker.setVisibility(View.GONE);
        if (videoSticker != null) { videoSticker.stopPlayback(); videoSticker.setVisibility(View.GONE); }

        com.talksy.messenger.ui.widget.VoiceBubbleView vb = holder instanceof VHOut ? ((VHOut) holder).voiceBubble : ((VHIn) holder).voiceBubble;
        com.talksy.messenger.ui.widget.RoundVideoView rv = holder instanceof VHOut ? ((VHOut) holder).roundVideo : ((VHIn) holder).roundVideo;
        boolean isVoice = m.media != null && m.media.isVoice(), isRound = m.media != null && m.media.isRound();
        if (vb != null) vb.setVisibility(isVoice ? View.VISIBLE : View.GONE);
        if (rv != null) rv.setVisibility(isRound ? View.VISIBLE : View.GONE);
        if (isVoice || isRound) {
            String mkey = "g" + (m.localId > 0 ? m.localId : m.id);
            String base = new com.talksy.messenger.net.ApiClient(text.getContext()).getBaseUrl();
            if (isVoice && vb != null) { vb.bind(mkey, m.media, base, m.outgoing); vb.setOnLongClickListener(v -> { longPressListener.onLongPress(m); return true; }); }
            if (isRound && rv != null) { rv.bind(mkey, m.media, base); rv.setOnLongClickListener(v -> { longPressListener.onLongPress(m); return true; }); }
        }
        if (m.media != null && !isVoice && !isRound) {
            if (m.media.isStickerImage() || m.media.isStickerVideo()) {
                if (frameSticker != null) {
                    frameSticker.setVisibility(View.VISIBLE);
                    final String stickerMediaUrl = m.media.url;
                    frameSticker.setOnClickListener(v -> com.talksy.messenger.ui.StickerShare.onTap(v.getContext(), stickerMediaUrl));
                    if (m.media.isStickerImage()) {
                        imgSticker.setVisibility(View.VISIBLE);
                        if (videoSticker != null) videoSticker.setVisibility(View.GONE);
                        AvatarLoader.loadRaw(baseUrl + m.media.url, 400, com.talksy.messenger.data.TrafficStats.CAT_STICKERS, bmp -> imgSticker.setImageBitmap(bmp));
                    } else if (videoSticker != null) {
                        imgSticker.setVisibility(View.GONE);
                        videoSticker.setVisibility(View.VISIBLE);
                        final String stickerUrl = baseUrl + m.media.url;
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
                }
            } else if (m.media.isImage()) {
                frameImage.setVisibility(View.VISIBLE);
                final boolean hidden = m.media.isHidden();
                AvatarLoader.loadRaw(baseUrl + m.media.url, 900,
                        bmp -> imgMedia.setImageBitmap(hidden ? com.talksy.messenger.data.BlurUtils.blur(bmp) : bmp));
                frameImage.setOnClickListener(v -> {
                    if (hidden) { MediaAttachment.REVEALED.add(m.media.url); notifyDataSetChanged(); }
                    else mediaClickListener.onMediaClick(m.media);
                });
            } else if (m.media.isVideo()) {
                frameImage.setVisibility(View.VISIBLE);
                final boolean hidden = m.media.isHidden();
                VideoBubble.bind(frameImage, imgMedia, baseUrl + m.media.url, hidden);
                frameImage.setOnClickListener(v -> {
                    if (hidden) { MediaAttachment.REVEALED.add(m.media.url); notifyDataSetChanged(); }
                    else mediaClickListener.onMediaClick(m.media);
                });
            } else {
                rowFile.setVisibility(View.VISIBLE);
                final boolean hidden = m.media.isHidden();
                imgFileIcon.setImageResource(m.media.isVideo() ? R.drawable.ic_video : R.drawable.ic_file);
                txtFileName.setText(hidden ? IconManager.prefix(txtFileName.getContext(), "secure_chat", txtFileName.getContext().getString(R.string.spoiler_hint)) : m.media.displayName());
                txtFileSize.setText(m.media.sizeText(txtFileSize.getContext()));
                rowFile.setOnClickListener(v -> {
                    if (hidden) { MediaAttachment.REVEALED.add(m.media.url); notifyDataSetChanged(); }
                    else mediaClickListener.onMediaClick(m.media);
                });
            }
        }

        VideoBubble.applyTime(frameImage, time,
                m.media != null && m.media.isVideo() && (m.text == null || m.text.isEmpty()));

        if (m.reactions == null || m.reactions.isEmpty()) {
            reactions.setVisibility(View.GONE);
        } else {
            StringBuilder sb = new StringBuilder();
            for (Reaction r : m.reactions) sb.append(r.emoji).append(" ").append(r.count).append("  ");
            reactions.setText(sb.toString().trim());
            reactions.setVisibility(View.VISIBLE);
            if (reactionsEnabled) reactions.setOnClickListener(v -> longPressListener.onLongPress(m));
        }

        if (reactionsEnabled) {
            View.OnLongClickListener onLong = v -> { longPressListener.onLongPress(m); return true; };
            text.setOnLongClickListener(onLong);
            holder.itemView.setOnLongClickListener(onLong);
            View.OnClickListener onTap = v -> longPressListener.onLongPress(m);
            text.setOnClickListener(onTap);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VHOut extends RecyclerView.ViewHolder {
        TextView text, time, reactions, txtFileName, txtFileSize;
        View frameImage, rowFile, frameSticker;
        TextView txtComments;
        com.talksy.messenger.ui.widget.VoiceBubbleView voiceBubble;
        com.talksy.messenger.ui.widget.SpecialCardView specialCard;
        com.talksy.messenger.ui.widget.RoundVideoView roundVideo;
        ImageView imgMedia, imgFileIcon, imgSticker;
        android.widget.VideoView videoSticker;
        VHOut(View v) {
            super(v);
            text = v.findViewById(R.id.txt_msg);
            time = v.findViewById(R.id.txt_time);
            txtComments = v.findViewById(R.id.txt_comments);
            reactions = v.findViewById(R.id.txt_reactions);
            frameImage = v.findViewById(R.id.frame_media_image);
            imgMedia = v.findViewById(R.id.img_media);
            rowFile = v.findViewById(R.id.row_media_file);
            voiceBubble = v.findViewById(R.id.voice_bubble);
            specialCard = v.findViewById(R.id.special_card);
            roundVideo = v.findViewById(R.id.round_video);
            imgFileIcon = v.findViewById(R.id.img_file_icon);
            txtFileName = v.findViewById(R.id.txt_file_name);
            txtFileSize = v.findViewById(R.id.txt_file_size);
            frameSticker = v.findViewById(R.id.frame_sticker);
            imgSticker = v.findViewById(R.id.img_sticker);
            videoSticker = v.findViewById(R.id.video_sticker);
        }
    }

    static class VHIn extends RecyclerView.ViewHolder {
        TextView sender, text, time, reactions, txtFileName, txtFileSize;
        View frameImage, rowFile, frameSticker;
        TextView txtComments;
        com.talksy.messenger.ui.widget.VoiceBubbleView voiceBubble;
        com.talksy.messenger.ui.widget.SpecialCardView specialCard;
        com.talksy.messenger.ui.widget.RoundVideoView roundVideo;
        ImageView imgMedia, imgFileIcon, imgSticker;
        android.widget.VideoView videoSticker;
        VHIn(View v) {
            super(v);
            sender = v.findViewById(R.id.txt_sender);
            text = v.findViewById(R.id.txt_msg);
            time = v.findViewById(R.id.txt_time);
            txtComments = v.findViewById(R.id.txt_comments);
            reactions = v.findViewById(R.id.txt_reactions);
            frameImage = v.findViewById(R.id.frame_media_image);
            imgMedia = v.findViewById(R.id.img_media);
            rowFile = v.findViewById(R.id.row_media_file);
            voiceBubble = v.findViewById(R.id.voice_bubble);
            specialCard = v.findViewById(R.id.special_card);
            roundVideo = v.findViewById(R.id.round_video);
            imgFileIcon = v.findViewById(R.id.img_file_icon);
            txtFileName = v.findViewById(R.id.txt_file_name);
            txtFileSize = v.findViewById(R.id.txt_file_size);
            frameSticker = v.findViewById(R.id.frame_sticker);
            imgSticker = v.findViewById(R.id.img_sticker);
            videoSticker = v.findViewById(R.id.video_sticker);
        }
    }
}
