package com.talksy.messenger.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.model.Chat;
import com.talksy.messenger.net.AvatarLoader;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.VH> {

    public interface OnChatClick { void onClick(Chat chat); }

    private final List<Chat> items;
    private final OnChatClick listener;
    private final String baseUrl;
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChatListAdapter(List<Chat> items, String baseUrl, OnChatClick listener) {
        this.items = items;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Chat c = items.get(position);
        h.avatar.setText(initials(c.peerName));
        try {
            h.avatar.getBackground().setTint(Color.parseColor(c.peerColorHex));
        } catch (Exception ignored) {}

        h.avatarImg.setVisibility(View.GONE);
        h.avatarImg.setImageBitmap(null);
        if (c.peerAvatarUrl != null && !c.peerAvatarUrl.isEmpty()) {
            AvatarLoader.load(baseUrl + c.peerAvatarUrl, bmp -> {
                h.avatarImg.setImageBitmap(bmp);
                h.avatarImg.setVisibility(View.VISIBLE);
            });
        }

        h.name.setText(c.peerName);
        h.lastMsg.setText(c.lastMessage == null || c.lastMessage.isEmpty() ? "" : c.lastMessage);
        h.time.setText(c.lastMessageTime > 0 ? fmt.format(c.lastMessageTime) : "");
        h.itemView.setOnClickListener(v -> listener.onClick(c));
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
        TextView avatar, name, lastMsg, time;
        ImageView avatarImg;
        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.txt_avatar);
            avatarImg = v.findViewById(R.id.img_avatar);
            name = v.findViewById(R.id.txt_name);
            lastMsg = v.findViewById(R.id.txt_last_msg);
            time = v.findViewById(R.id.txt_time);
        }
    }
}
