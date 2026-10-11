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
import com.talksy.messenger.data.model.SearchItem;
import com.talksy.messenger.net.AvatarLoader;

import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.VH> {

    public interface OnClick { void onClick(SearchItem item); }

    private final List<SearchItem> items;
    private final String baseUrl;
    private final OnClick listener;

    public SearchResultAdapter(List<SearchItem> items, String baseUrl, OnClick listener) {
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
        SearchItem it = items.get(position);
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

        h.name.setText((it.isGroup ? h.name.getContext().getString(R.string.group_prefix) : "") + it.name);
        h.handle.setText(it.handle == null ? "" : it.handle);
        h.itemView.setOnClickListener(v -> listener.onClick(it));
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
