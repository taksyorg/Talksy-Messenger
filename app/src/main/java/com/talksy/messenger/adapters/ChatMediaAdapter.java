package com.talksy.messenger.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.model.Message;
import com.talksy.messenger.net.AvatarLoader;

import java.util.List;

public class ChatMediaAdapter extends RecyclerView.Adapter<ChatMediaAdapter.VH> {

    public interface OnClick { void onClick(Message message); }

    private final List<Message> items;
    private final String baseUrl;
    private final OnClick listener;

    public ChatMediaAdapter(List<Message> items, String baseUrl, OnClick listener) {
        this.items = items;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_media, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Message m = items.get(position);
        h.img.setImageBitmap(null);
        boolean isVideo = m.media != null && "video".equals(m.media.type);
        h.videoBadge.setVisibility(isVideo ? View.VISIBLE : View.GONE);
        if (m.media != null && m.media.url != null) {
            AvatarLoader.loadRaw(baseUrl + m.media.url, 300, h.img::setImageBitmap);
        }
        h.itemView.setOnClickListener(v -> listener.onClick(m));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img;
        TextView videoBadge;
        VH(View v) {
            super(v);
            img = v.findViewById(R.id.img_media);
            videoBadge = v.findViewById(R.id.txt_video_badge);
        }
    }
}
