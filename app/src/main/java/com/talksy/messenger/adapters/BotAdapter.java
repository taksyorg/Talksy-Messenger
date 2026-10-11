package com.talksy.messenger.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.model.Bot;

import java.util.List;

public class BotAdapter extends RecyclerView.Adapter<BotAdapter.VH> {

    public interface OnBotClick { void onClick(Bot bot); }

    private final List<Bot> items;
    private final OnBotClick listener;

    public BotAdapter(List<Bot> items, OnBotClick listener) {
        this.items = items;
        this.listener = listener;
    }

    public Bot getBotAt(int position) { return items.get(position); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bot, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Bot bot = items.get(position);
        h.name.setText(bot.name);
        h.username.setText(bot.username);
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onClick(bot); });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, username;
        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.txt_name);
            username = itemView.findViewById(R.id.txt_username);
        }
    }
}
