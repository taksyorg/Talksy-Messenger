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
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.model.User;
import com.talksy.messenger.net.AvatarLoader;

import java.util.List;

public class BlockedUsersAdapter extends RecyclerView.Adapter<BlockedUsersAdapter.VH> {

    public interface OnUnblock { void onUnblock(User user, int position); }

    private final List<User> items;
    private final String baseUrl;
    private final OnUnblock listener;

    public BlockedUsersAdapter(List<User> items, String baseUrl, OnUnblock listener) {
        this.items = items;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_blocked_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        User u = items.get(position);
        h.avatar.setText(u.initials());
        try { h.avatar.getBackground().setTint(Color.parseColor(u.colorHex)); } catch (Exception ignored) {}

        h.avatarImg.setVisibility(View.GONE);
        if (u.avatarUrl != null && !u.avatarUrl.isEmpty()) {
            AvatarLoader.load(baseUrl + u.avatarUrl, bmp -> {
                h.avatarImg.setImageBitmap(bmp);
                h.avatarImg.setVisibility(View.VISIBLE);
            });
        }

        h.name.setText(u.name);
        h.handle.setText(u.handle == null ? "" : "@" + u.handle);
        AnimUtils.pressScale(h.btnUnblock);
        h.btnUnblock.setOnClickListener(v -> listener.onUnblock(u, h.getAdapterPosition()));
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
        TextView avatar, name, handle, btnUnblock;
        ImageView avatarImg;
        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.txt_avatar);
            avatarImg = v.findViewById(R.id.img_avatar);
            name = v.findViewById(R.id.txt_name);
            handle = v.findViewById(R.id.txt_handle);
            btnUnblock = v.findViewById(R.id.btn_unblock);
        }
    }
}
