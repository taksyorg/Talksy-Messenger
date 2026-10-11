package com.talksy.messenger.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.adapters.BotFatherAdapter;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.model.Bot;
import com.talksy.messenger.net.ApiClient;

import java.util.LinkedHashMap;

public class BotFatherActivity extends BaseActivity {

    private enum State { MENU, AWAIT_NAME, AWAIT_USERNAME, DONE }

    private ApiClient apiClient;
    private BotFatherAdapter adapter;
    private RecyclerView rv;
    private LinearLayout composerBar;
    private EditText inpText;

    private State state = State.MENU;
    private String pendingName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_botfather);
        apiClient = new ApiClient(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> { finish(); AnimUtils.back(this); });

        rv = findViewById(R.id.rv_conversation);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BotFatherAdapter();
        rv.setAdapter(adapter);

        composerBar = findViewById(R.id.composer_bar);
        inpText = findViewById(R.id.inp_text);
        ImageButton btnSend = findViewById(R.id.btn_send);
        btnSend.setOnClickListener(v -> onSendTyped());
        inpText.setOnEditorActionListener((v, actionId, event) -> { onSendTyped(); return true; });

        showMainMenu(true);
    }

    private void scrollToEnd() {
        rv.post(() -> rv.smoothScrollToPosition(adapter.getCount() - 1));
    }

    private void showMainMenu(boolean greet) {
        state = State.MENU;
        composerBar.setVisibility(View.GONE);
        if (greet) {
            adapter.addBotText(getString(R.string.botfather_greeting));
        }
        LinkedHashMap<String, Runnable> menu = new LinkedHashMap<>();
        menu.put(getString(R.string.botfather_btn_create), this::startCreateFlow);
        menu.put(getString(R.string.botfather_btn_my_bots), this::openMyBots);
        adapter.addBotButtons(getString(R.string.botfather_menu_prompt), menu);
        scrollToEnd();
    }

    private void startCreateFlow() {
        adapter.addUserText(getString(R.string.botfather_btn_create));
        adapter.addBotText(getString(R.string.botfather_ask_name));
        state = State.AWAIT_NAME;
        composerBar.setVisibility(View.VISIBLE);
        inpText.setText("");
        inpText.requestFocus();
        scrollToEnd();
    }

    private void openMyBots() {
        adapter.addUserText(getString(R.string.botfather_btn_my_bots));
        scrollToEnd();
        startActivity(new Intent(this, MyBotsActivity.class));
        AnimUtils.forward(this);
    }

    private void onSendTyped() {
        String typed = inpText.getText().toString().trim();
        if (typed.isEmpty()) return;
        inpText.setText("");

        if (state == State.AWAIT_NAME) {
            if (typed.length() > 64) {
                adapter.addBotText(getString(R.string.botfather_name_too_long));
                scrollToEnd();
                return;
            }
            pendingName = typed;
            adapter.addUserText(typed);
            adapter.addBotText(getString(R.string.botfather_ask_username));
            state = State.AWAIT_USERNAME;
            scrollToEnd();

        } else if (state == State.AWAIT_USERNAME) {
            adapter.addUserText(typed);
            composerBar.setVisibility(View.GONE);
            apiClient.createBot(pendingName, typed, (ok, bot, error) -> {
                if (!ok || bot == null) {
                    adapter.addBotText((error != null ? error : getString(R.string.error_generic)));
                    adapter.addBotText(getString(R.string.botfather_ask_username_again));
                    composerBar.setVisibility(View.VISIBLE);
                    scrollToEnd();
                    return;
                }
                onBotCreated(bot);
            });
        }
    }

    private void onBotCreated(Bot bot) {
        state = State.DONE;
        adapter.addBotText(getString(R.string.botfather_done, bot.name, bot.username));
        adapter.addBotText(bot.token);
        LinkedHashMap<String, Runnable> actions = new LinkedHashMap<>();
        actions.put(getString(R.string.botfather_copy_token), () -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("bot token", bot.token));
            Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show();
        });
        actions.put(getString(R.string.botfather_open_settings), () -> {
            Intent i = new Intent(this, BotDetailActivity.class);
            i.putExtra("bot_id", bot.id);
            startActivity(i);
            AnimUtils.forward(this);
        });
        actions.put(getString(R.string.botfather_btn_create), this::startCreateFlow);
        adapter.addBotButtons(getString(R.string.botfather_what_next), actions);
        scrollToEnd();
    }
}
