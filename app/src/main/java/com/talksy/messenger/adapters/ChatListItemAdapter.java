package com.talksy.messenger.adapters;

import com.talksy.messenger.data.IconManager;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.model.ChatListItem;
import com.talksy.messenger.net.AvatarLoader;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ChatListItemAdapter extends RecyclerView.Adapter<ChatListItemAdapter.VH> {

    public interface OnClick { void onClick(ChatListItem item); }
    public interface OnLongClick { void onLongClick(ChatListItem item); }

    private final List<ChatListItem> items;
    private final String baseUrl;
    private final OnClick listener;
    private final OnLongClick longListener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChatListItemAdapter(List<ChatListItem> items, String baseUrl, OnClick listener, OnLongClick longListener) {
        this.items = items;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.listener = listener;
        this.longListener = longListener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ChatListItem it = items.get(position);
        if (it.isGroup) { IconManager.set(h.avatar, "group", 22, Color.WHITE); }
        else { IconManager.clear(h.avatar); h.avatar.setText(initials(it.name)); }
        try { h.avatar.getBackground().setTint(Color.parseColor(it.colorHex)); } catch (Exception ignored) {}

        h.avatarImg.setVisibility(View.GONE);
        final String wantedUrl = it.avatarUrl;
        h.avatarImg.setTag(wantedUrl);
        if (it.avatarUrl != null && !it.avatarUrl.isEmpty()) {
            AvatarLoader.load(baseUrl + it.avatarUrl, bmp -> {
                if (!wantedUrl.equals(h.avatarImg.getTag())) return;
                h.avatarImg.setImageBitmap(bmp);
                h.avatarImg.setVisibility(View.VISIBLE);
            });
        }

        android.content.Context cx = h.itemView.getContext();
        CharSequence nm = it.name;
        if (it.isBot) nm = IconManager.prefix(cx, "bot_active", nm);
        if (it.pinned) nm = IconManager.prefix(cx, "pin", nm);
        if (it.muted) nm = IconManager.suffix(cx, "mute", nm);
        h.name.setText(nm);
        h.lastMsg.setText(it.lastMessage == null || it.lastMessage.isEmpty() ? "" : it.lastMessage);
        final String draft = com.talksy.messenger.data.Drafts.get(h.itemView.getContext(), (it.isGroup ? "g" : "u") + it.id);
        if (!draft.isEmpty()) {
            String label = h.itemView.getContext().getString(R.string.draft_label) + " ";
            android.text.SpannableString sp = new android.text.SpannableString(label + draft.replace('\n', ' '));
            sp.setSpan(new android.text.style.ForegroundColorSpan(0xFFE5484D), 0, label.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            h.lastMsg.setText(sp);
        }
        h.time.setText(it.lastMessageTime > 0 ? fmt.format(it.lastMessageTime) : "");

        boolean hasUnread = it.unreadCount > 0;
        h.unreadBadge.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
        if (hasUnread) h.unreadBadge.setText(it.unreadCount > 99 ? "99+" : String.valueOf(it.unreadCount));
        h.lastMsg.setTypeface(null, hasUnread ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        h.lastMsg.setTextColor(h.lastMsg.getContext().getResources().getColor(
                hasUnread ? R.color.text1 : R.color.text2));

        h.itemView.setOnClickListener(v -> listener.onClick(it));
        h.itemView.setOnLongClickListener(v -> { longListener.onLongClick(it); return true; });
    }

    @Override
    public int getItemCount() { return items.size(); }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] p = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.length && i < 2; i++) if (!p[i].isEmpty()) sb.append(Character.toUpperCase(p[i].charAt(0)));
        return sb.toString();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView avatar, name, lastMsg, time, unreadBadge;
        ImageView avatarImg;
        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.txt_avatar);
            avatarImg = v.findViewById(R.id.img_avatar);
            name = v.findViewById(R.id.txt_name);
            lastMsg = v.findViewById(R.id.txt_last_msg);
            time = v.findViewById(R.id.txt_time);
            unreadBadge = v.findViewById(R.id.txt_unread_badge);
        }
    }
}
