package com.talksy.messenger.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.adapters.BotAdapter;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.model.Bot;
import com.talksy.messenger.net.ApiClient;

import java.util.ArrayList;
import java.util.List;

public class MyBotsActivity extends BaseActivity {

    private final List<Bot> bots = new ArrayList<>();
    private ApiClient apiClient;
    private BotAdapter adapter;
    private TextView txtEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_bots);
        apiClient = new ApiClient(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        findViewById(R.id.btn_new_bot).setOnClickListener(v -> {
            startActivity(new Intent(this, BotFatherActivity.class));
            AnimUtils.forward(this);
        });

        txtEmpty = findViewById(R.id.txt_empty);
        RecyclerView rv = findViewById(R.id.rv_bots);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BotAdapter(bots, bot -> {
            Intent i = new Intent(this, BotDetailActivity.class);
            i.putExtra("bot_id", bot.id);
            startActivity(i);
            AnimUtils.forward(this);
        });
        rv.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        apiClient.getMyBots((ok, list, error) -> {
            if (!ok || list == null) return;
            bots.clear();
            bots.addAll(list);
            adapter.notifyDataSetChanged();
            txtEmpty.setVisibility(bots.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }
}
