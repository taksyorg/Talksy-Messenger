package com.talksy.messenger.stickers;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.model.Sticker;
import com.talksy.messenger.data.model.StickerPack;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.net.AvatarLoader;

import java.util.ArrayList;
import java.util.List;

public class StickerPanel {

    public interface Host {
        void onStickerChosen(Sticker sticker);

        void onCreateSticker(long packId);
    }

    public interface ShownListener { void onShownChanged(boolean shown); }

    private static final String[] EMOJI = {
            "😀","😃","😄","😁","😆","😅","😂","🤣","🙂","😉","😊","😇","🥰","😍","🤩","😘",
            "😋","😛","😜","🤪","🤔","🤨","😐","😑","😶","🙄","😏","😴","😌","😎","🤓","🥳",
            "😢","😭","😤","😡","🤬","😱","😨","😰","🥺","😳","🤯","🤗","🤭","🤫","🫡","😬",
            "👍","👎","👌","✌️","🤞","🤝","👏","🙌","🙏","💪","👋","🤙","👀","🧠","❤️","🧡",
            "💛","💚","💙","💜","🖤","🤍","💔","💯","🔥","✨","⭐","🎉","🎁","🏆","⚡","💥",
            "☀️","🌙","🌈","☕","🍕","🍔","🍺","🎵","🎮","📱","💻","🚀","✅","❌","❓","❗"
    };

    private final Activity activity;
    private final ViewGroup container;
    private final EditText input;
    private final ApiClient api;
    private final Host host;

    private final List<StickerPack> packs = new ArrayList<>();
    private final List<Sticker> stickers = new ArrayList<>();
    private long selectedPackId = -1;
    private boolean packsLoaded = false;
    private int tab = 2;
    private ShownListener shownListener;

    private RecyclerView rvEmoji, rvPacks, rvStickers;
    private View pageGif, pageStickers, txtNoPacks;
    private TextView tabEmoji, tabGif, tabStickers;
    private PacksAdapter packsAdapter;
    private StickersAdapter stickersAdapter;

    public StickerPanel(Activity activity, ViewGroup container, EditText input, ApiClient api, Host host) {
        this.activity = activity;
        this.container = container;
        this.input = input;
        this.api = api;
        this.host = host;

        View root = LayoutInflater.from(activity).inflate(R.layout.panel_stickers, container, false);
        container.addView(root);

        rvEmoji = root.findViewById(R.id.rv_emoji);
        rvPacks = root.findViewById(R.id.rv_packs);
        rvStickers = root.findViewById(R.id.rv_stickers);
        pageGif = root.findViewById(R.id.page_gif);
        pageStickers = root.findViewById(R.id.page_stickers);
        txtNoPacks = root.findViewById(R.id.txt_no_packs);
        tabEmoji = root.findViewById(R.id.tab_emoji);
        tabGif = root.findViewById(R.id.tab_gif);
        tabStickers = root.findViewById(R.id.tab_stickers);

        rvEmoji.setLayoutManager(new GridLayoutManager(activity, 8));
        rvEmoji.setAdapter(new EmojiAdapter());

        packsAdapter = new PacksAdapter();
        rvPacks.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false));
        rvPacks.setAdapter(packsAdapter);

        stickersAdapter = new StickersAdapter();
        rvStickers.setLayoutManager(new GridLayoutManager(activity, 4));
        rvStickers.setAdapter(stickersAdapter);

        root.findViewById(R.id.btn_add_sticker).setOnClickListener(v ->
                host.onCreateSticker(selectedPackId > 0 ? selectedPackId : -1));

        tabEmoji.setOnClickListener(v -> selectTab(0));
        tabGif.setOnClickListener(v -> selectTab(1));
        tabStickers.setOnClickListener(v -> selectTab(2));

        input.setOnClickListener(v -> { if (isShown()) hide(true); });
    }

    public void setShownListener(ShownListener l) { this.shownListener = l; }

    public boolean isShown() { return container.getVisibility() == View.VISIBLE; }

    public void toggle() {
        if (isShown()) hide(true); else show();
    }

    public void show() {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        container.setVisibility(View.VISIBLE);
        selectTab(tab);
        if (shownListener != null) shownListener.onShownChanged(true);
    }

    public void hide(boolean showKeyboard) {
        container.setVisibility(View.GONE);
        if (shownListener != null) shownListener.onShownChanged(false);
        if (showKeyboard) {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void reloadPacks() {
        packsLoaded = false;
        if (isShown() && tab == 2) loadPacks();
    }

    private void selectTab(int t) {
        tab = t;
        rvEmoji.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        pageGif.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        pageStickers.setVisibility(t == 2 ? View.VISIBLE : View.GONE);
        styleTab(tabEmoji, t == 0);
        styleTab(tabGif, t == 1);
        styleTab(tabStickers, t == 2);
        if (t == 2 && !packsLoaded) loadPacks();
    }

    private void styleTab(TextView tv, boolean selected) {
        tv.setBackgroundResource(selected ? R.drawable.bg_tab_pill_selected : 0);
        tv.setTextColor(selected ? Color.WHITE : ContextCompat.getColor(activity, R.color.text2));
    }

    private void loadPacks() {
        packsLoaded = true;
        api.listStickerPacks((ok, list, error) -> {
            packs.clear();
            if (ok && list != null) packs.addAll(list);
            packsAdapter.notifyDataSetChanged();
            if (!packs.isEmpty()) {
                txtNoPacks.setVisibility(View.GONE);
                selectPack(packs.get(0).id);
            } else {
                stickers.clear();
                stickersAdapter.notifyDataSetChanged();
                txtNoPacks.setVisibility(View.VISIBLE);
            }
        });
    }

    private void selectPack(long packId) {
        selectedPackId = packId;
        packsAdapter.notifyDataSetChanged();
        api.getStickerPack(packId, (ok, list, error) -> {
            stickers.clear();
            if (ok && list != null) stickers.addAll(list);
            stickersAdapter.notifyDataSetChanged();
        });
    }

    private float dp(float v) {
        return v * activity.getResources().getDisplayMetrics().density;
    }

    private class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            final TextView tv;
            VH(TextView v) { super(v); tv = v; }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setTextSize(26);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, (int) dp(8), 0, (int) dp(8));
            tv.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            final String e = EMOJI[position];
            h.tv.setText(e);
            h.tv.setOnClickListener(v -> {
                int a = Math.max(0, input.getSelectionStart());
                int b = Math.max(0, input.getSelectionEnd());
                input.getText().replace(Math.min(a, b), Math.max(a, b), e);
            });
        }

        @Override public int getItemCount() { return EMOJI.length; }
    }

    private class PacksAdapter extends RecyclerView.Adapter<PacksAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            final TextView txt;
            VH(TextView v) { super(v); txt = v; }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView txt = new TextView(parent.getContext());
            txt.setPadding((int) dp(12), (int) dp(6), (int) dp(12), (int) dp(6));
            txt.setTextSize(14);
            txt.setGravity(Gravity.CENTER);
            txt.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new VH(txt);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            StickerPack pack = packs.get(position);
            h.txt.setText(pack.name);
            boolean sel = pack.id == selectedPackId;
            h.txt.setTextColor(sel ? ContextCompat.getColor(activity, R.color.accent)
                                   : ContextCompat.getColor(activity, R.color.text2));
            h.txt.setOnClickListener(v -> selectPack(pack.id));
        }

        @Override public int getItemCount() { return packs.size(); }
    }

    private class StickersAdapter extends RecyclerView.Adapter<StickersAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            final ImageView img;
            final View videoMark;
            VH(View v) {
                super(v);
                img = v.findViewById(R.id.img_sticker);
                videoMark = v.findViewById(R.id.txt_sticker_video);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sticker_panel, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            final Sticker s = stickers.get(position);
            final String url = api.getBaseUrl() + s.mediaUrl;
            h.img.setImageDrawable(null);
            h.img.setTag(url);
            h.img.setBackgroundColor(Color.parseColor("#14000000"));
            if (s.isVideo()) {

                h.videoMark.setVisibility(View.VISIBLE);
            } else {
                h.videoMark.setVisibility(View.GONE);
                AvatarLoader.loadRaw(url, 256, com.talksy.messenger.data.TrafficStats.CAT_STICKERS, bmp -> {
                    if (url.equals(h.img.getTag())) h.img.setImageBitmap(bmp);
                });
            }
            h.itemView.setOnClickListener(v -> host.onStickerChosen(s));
        }

        @Override public int getItemCount() { return stickers.size(); }
    }
}
