package com.talksy.messenger.stickers;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.net.AvatarLoader;
import com.talksy.messenger.data.model.Sticker;
import com.talksy.messenger.data.model.StickerPack;
import com.talksy.messenger.net.ApiClient;

import java.util.ArrayList;
import java.util.List;

public class StickerPickerActivity extends BaseActivity {

    public static final String EXTRA_MEDIA_URL = "media_url";
    public static final String EXTRA_MEDIA_TYPE = "media_type";
    public static final String EXTRA_DURATION_MS = "duration_ms";

    private ApiClient apiClient;
    private final List<StickerPack> packs = new ArrayList<>();
    private final List<Sticker> stickers = new ArrayList<>();
    private long selectedPackId = -1;
    private PacksAdapter packsAdapter;
    private StickersAdapter stickersAdapter;

    private final ActivityResultLauncher<Intent> createStickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> loadPacks());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_picker);
        apiClient = new ApiClient(this);

        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnAdd = findViewById(R.id.btn_add_sticker);
        RecyclerView rvPacks = findViewById(R.id.rv_packs);
        RecyclerView rvStickers = findViewById(R.id.rv_stickers);

        btnBack.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        btnAdd.setOnClickListener(v -> {
            Intent i = new Intent(this, StickerCreateActivity.class);
            if (selectedPackId > 0) i.putExtra("pack_id", selectedPackId);
            createStickerLauncher.launch(i);
        });

        packsAdapter = new PacksAdapter();
        rvPacks.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvPacks.setAdapter(packsAdapter);

        stickersAdapter = new StickersAdapter();
        rvStickers.setLayoutManager(new GridLayoutManager(this, 4));
        rvStickers.setAdapter(stickersAdapter);

        loadPacks();
    }

    private void loadPacks() {
        apiClient.listStickerPacks((ok, list, error) -> {
            packs.clear();
            if (ok && list != null) packs.addAll(list);
            packsAdapter.notifyDataSetChanged();
            if (!packs.isEmpty()) {
                selectPack(packs.get(0).id);
            } else {
                stickers.clear();
                stickersAdapter.notifyDataSetChanged();
            }
        });
    }

    private void selectPack(long packId) {
        selectedPackId = packId;
        packsAdapter.notifyDataSetChanged();
        apiClient.getStickerPack(packId, (ok, list, error) -> {
            stickers.clear();
            if (ok && list != null) stickers.addAll(list);
            stickersAdapter.notifyDataSetChanged();
        });
    }

    private void sendSticker(Sticker s) {
        Intent data = new Intent();
        data.putExtra(EXTRA_MEDIA_URL, s.mediaUrl);
        data.putExtra(EXTRA_MEDIA_TYPE, s.mediaType);
        data.putExtra(EXTRA_DURATION_MS, s.durationMs);
        setResult(RESULT_OK, data);
        finish();
    }

    private class PacksAdapter extends RecyclerView.Adapter<PacksAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView txt;
            VH(TextView v) { super(v); txt = v; }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView txt = new TextView(parent.getContext());
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            txt.setPadding(pad, pad / 2, pad, pad / 2);
            txt.setTextSize(14);
            return new VH(txt);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            StickerPack pack = packs.get(position);
            holder.txt.setText(pack.name);
            boolean selected = pack.id == selectedPackId;
            holder.txt.setTextColor(selected ? Color.WHITE : Color.GRAY);
            holder.txt.setBackgroundColor(selected ? Color.parseColor("#6366F1") : Color.TRANSPARENT);
            holder.itemView.setOnClickListener(v -> selectPack(pack.id));
        }

        @Override
        public int getItemCount() { return packs.size(); }
    }

    private class StickersAdapter extends RecyclerView.Adapter<StickersAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            ImageView img;
            TextView badge;
            VH(View v) { super(v); img = v.findViewById(R.id.img_sticker); badge = v.findViewById(R.id.txt_video_badge); }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sticker, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Sticker s = stickers.get(position);
            holder.img.setImageDrawable(null);
            holder.img.setBackgroundColor(Color.parseColor("#22000000"));
            if (s.isVideo()) {
                holder.badge.setVisibility(View.VISIBLE);
            } else {
                holder.badge.setVisibility(View.GONE);
                AvatarLoader.load(apiClient.getBaseUrl() + s.mediaUrl, bmp -> holder.img.setImageBitmap(bmp));
            }
            holder.itemView.setOnClickListener(v -> sendSticker(s));
        }

        @Override
        public int getItemCount() { return stickers.size(); }
    }
}
