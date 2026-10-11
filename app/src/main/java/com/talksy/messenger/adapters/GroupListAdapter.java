package com.talksy.messenger.adapters;

import android.graphics.Color;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.model.Group;
import com.talksy.messenger.net.AvatarLoader;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class GroupListAdapter extends RecyclerView.Adapter<GroupListAdapter.VH> {

    public interface OnGroupClick { void onClick(Group group); }
    public interface OnJoinClick { void onJoin(Group group); }

    private final List<Group> items;
    private final String baseUrl;
    private final OnGroupClick listener;
    private final OnJoinClick joinListener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public GroupListAdapter(List<Group> items, String baseUrl, OnGroupClick listener, OnJoinClick joinListener) {
        this.items = items;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.listener = listener;
        this.joinListener = joinListener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Group g = items.get(position);
        h.name.setText(g.name);

        boolean isMember = g.myRole != null;
        h.joinBtn.setVisibility(isMember ? View.GONE : View.VISIBLE);
        h.role.setText(isMember ? roleLabel(h.role.getContext(), g.myRole) : "");

        h.lastMsg.setText(g.lastMessage == null || g.lastMessage.isEmpty()
                ? (isMember ? h.lastMsg.getContext().getString(R.string.no_messages_yet) : "")
                : g.lastMessage);

        h.avatarImg.setVisibility(View.GONE);
        if (g.avatarUrl != null && !g.avatarUrl.isEmpty()) {
            AvatarLoader.load(baseUrl + g.avatarUrl, bmp -> {
                h.avatarImg.setImageBitmap(bmp);
                h.avatarImg.setVisibility(View.VISIBLE);
            });
        }

        h.itemView.setOnClickListener(v -> {
            if (isMember) listener.onClick(g); else joinListener.onJoin(g);
        });
        h.joinBtn.setOnClickListener(v -> joinListener.onJoin(g));
    }

    private String roleLabel(Context ctx, String role) {
        if ("owner".equals(role)) return ctx.getString(R.string.role_owner);
        if ("admin".equals(role)) return ctx.getString(R.string.role_admin);
        return "";
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, lastMsg, role, joinBtn;
        ImageView avatarImg;
        VH(View v) {
            super(v);
            name = v.findViewById(R.id.txt_name);
            lastMsg = v.findViewById(R.id.txt_last_msg);
            role = v.findViewById(R.id.txt_role);
            joinBtn = v.findViewById(R.id.txt_join);
            avatarImg = v.findViewById(R.id.img_avatar);
        }
    }
}
