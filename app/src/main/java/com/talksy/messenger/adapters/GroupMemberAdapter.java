package com.talksy.messenger.adapters;

import com.talksy.messenger.ui.TDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.model.GroupMember;
import com.talksy.messenger.net.AvatarLoader;

import java.util.List;

public class GroupMemberAdapter extends RecyclerView.Adapter<GroupMemberAdapter.VH> {

    public interface OnRoleChange { void onChange(GroupMember member, String newRole); }

    private final List<GroupMember> items;
    private final String baseUrl;
    private final boolean iAmOwner;
    private final OnRoleChange listener;

    public GroupMemberAdapter(List<GroupMember> items, String baseUrl, boolean iAmOwner, OnRoleChange listener) {
        this.items = items;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.iAmOwner = iAmOwner;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_member, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        GroupMember m = items.get(position);
        h.avatar.setText(m.initials());
        try { h.avatar.getBackground().setTint(Color.parseColor(m.colorHex)); } catch (Exception ignored) {}

        h.avatarImg.setVisibility(View.GONE);
        if (m.avatarUrl != null && !m.avatarUrl.isEmpty()) {
            AvatarLoader.load(baseUrl + m.avatarUrl, bmp -> {
                h.avatarImg.setImageBitmap(bmp);
                h.avatarImg.setVisibility(View.VISIBLE);
            });
        }

        h.name.setText(m.name);
        h.handle.setText(m.handle);
        h.role.setText(roleLabel(h.role.getContext(), m.role));

        h.itemView.setOnClickListener(v -> {
            if (iAmOwner && !"owner".equals(m.role)) showRoleDialog(v.getContext(), m);
        });
    }

    private void showRoleDialog(Context ctx, GroupMember m) {
        boolean isAdmin = "admin".equals(m.role);
        String action = isAdmin ? ctx.getString(R.string.revoke_admin) : ctx.getString(R.string.make_admin);
        new TDialog.Builder(ctx)
                .setTitle(m.name)
                .setItems(new CharSequence[]{action}, (d, which) -> {
                    listener.onChange(m, isAdmin ? "member" : "admin");
                })
                .show();
    }

    private String roleLabel(Context ctx, String role) {
        if ("owner".equals(role)) return ctx.getString(R.string.role_owner);
        if ("admin".equals(role)) return ctx.getString(R.string.role_admin);
        return "";
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView avatar, name, handle, role;
        android.widget.ImageView avatarImg;
        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.txt_avatar);
            avatarImg = v.findViewById(R.id.img_avatar);
            name = v.findViewById(R.id.txt_name);
            handle = v.findViewById(R.id.txt_handle);
            role = v.findViewById(R.id.txt_role);
        }
    }
}
