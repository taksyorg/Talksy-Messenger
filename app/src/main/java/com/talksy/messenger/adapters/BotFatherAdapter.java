package com.talksy.messenger.adapters;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BotFatherAdapter extends RecyclerView.Adapter<BotFatherAdapter.VH> {

    public static class Msg {
        public final String text;
        public final boolean outgoing;

        public final Map<String, Runnable> buttons;

        public Msg(String text, boolean outgoing, Map<String, Runnable> buttons) {
            this.text = text;
            this.outgoing = outgoing;
            this.buttons = buttons;
        }
    }

    private final List<Msg> items = new ArrayList<>();

    public void addBotText(String text) {
        items.add(new Msg(text, false, null));
        notifyItemInserted(items.size() - 1);
    }

    public void addBotButtons(String text, LinkedHashMap<String, Runnable> buttons) {
        items.add(new Msg(text, false, buttons));
        notifyItemInserted(items.size() - 1);
    }

    public void addUserText(String text) {
        items.add(new Msg(text, true, null));
        notifyItemInserted(items.size() - 1);
    }

    public int getCount() { return items.size(); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_botfather_message, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Msg m = items.get(position);
        h.bubbleRow.setGravity(m.outgoing ? Gravity.END : Gravity.START);
        h.text.setText(m.text);
        h.text.setBackgroundResource(m.outgoing ? R.drawable.bg_bubble_out : R.drawable.bg_bubble_in);
        h.text.setTextColor(h.itemView.getResources().getColor(m.outgoing ? R.color.white : R.color.text1));

        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) h.text.getLayoutParams();
        lp.leftMargin = m.outgoing ? 48 : 0;
        lp.rightMargin = m.outgoing ? 0 : 48;
        h.text.setLayoutParams(lp);

        h.buttonsContainer.removeAllViews();
        if (m.buttons != null && !m.buttons.isEmpty()) {
            h.buttonsContainer.setVisibility(View.VISIBLE);
            for (Map.Entry<String, Runnable> e : m.buttons.entrySet()) {
                Button btn = new Button(h.itemView.getContext(), null, 0, R.style.TalksyButton);
                btn.setText(e.getKey());
                btn.setAllCaps(false);
                btn.setTextSize(13);
                btn.setPadding(28, 10, 28, 10);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.topMargin = 4;
                btn.setLayoutParams(blp);
                btn.setOnClickListener(v -> e.getValue().run());
                h.buttonsContainer.addView(btn);
            }
        } else {
            h.buttonsContainer.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        LinearLayout bubbleRow, buttonsContainer;
        TextView text;
        VH(@NonNull View itemView) {
            super(itemView);
            bubbleRow = itemView.findViewById(R.id.bubble_row);
            text = itemView.findViewById(R.id.txt_bubble);
            buttonsContainer = itemView.findViewById(R.id.buttons_container);
        }
    }
}
