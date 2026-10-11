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
import com.talksy.messenger.data.model.User;
import com.talksy.messenger.net.AvatarLoader;

import java.util.List;

public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.VH> {

    public interface OnUserClick { void onClick(User user); }

    private final List<User> items;
    private final String baseUrl;
    private final OnUserClick listener;

    public UserSearchAdapter(List<User> items, String baseUrl, OnUserClick listener) {
        this.items = items;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        User u = items.get(position);
        h.avatar.setText(u.initials());
        try { h.avatar.getBackground().setTint(Color.parseColor(u.colorHex)); } catch (Exception ignored) {}

        h.avatarImg.setVisibility(View.GONE);
        h.avatarImg.setImageBitmap(null);
        if (u.avatarUrl != null && !u.avatarUrl.isEmpty()) {
            AvatarLoader.load(baseUrl + u.avatarUrl, bmp -> {
                h.avatarImg.setImageBitmap(bmp);
                h.avatarImg.setVisibility(View.VISIBLE);
            });
        }

        h.name.setText(u.isBot ? IconManager.prefix(h.itemView.getContext(), "bot_active", u.name) : u.name);
        h.handle.setText(u.handle);
        h.itemView.setOnClickListener(v -> listener.onClick(u));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView avatar, name, handle;
        ImageView avatarImg;
        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.txt_avatar);
            avatarImg = v.findViewById(R.id.img_avatar);
            name = v.findViewById(R.id.txt_name);
            handle = v.findViewById(R.id.txt_handle);
        }
    }
}
