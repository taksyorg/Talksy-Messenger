package com.talksy.messenger.ui;

import com.talksy.messenger.ui.TDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.adapters.ChatListItemAdapter;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.model.Chat;
import com.talksy.messenger.data.model.ChatListItem;
import com.talksy.messenger.data.model.Group;
import com.talksy.messenger.net.ApiClient;

import java.util.ArrayList;
import java.util.List;

public class ArchivedActivity extends BaseActivity {

    private ApiClient apiClient;
    private ChatListItemAdapter adapter;
    private final List<ChatListItem> items = new ArrayList<>();
    private RecyclerView rv;
    private TextView txtEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archived);

        apiClient = new ApiClient(this);
        ImageButton btnBack = findViewById(R.id.btn_back);
        rv = findViewById(R.id.rv_archived);
        txtEmpty = findViewById(R.id.txt_empty);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatListItemAdapter(items, apiClient.getBaseUrl(), this::openItem, this::showUnarchiveMenu);
        rv.setAdapter(adapter);
        if (!AnimUtils.isEnabled(this)) rv.setItemAnimator(null);

        btnBack.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        apiClient.getChats(true, (okC, chats, errC) -> apiClient.getMyGroups(true, (okG, groups, errG) -> {
            items.clear();
            if (okC && chats != null) for (Chat c : chats) items.add(ChatListItem.fromChat(c));
            if (okG && groups != null) for (Group g : groups) items.add(ChatListItem.fromGroup(g));
            adapter.notifyDataSetChanged();
            boolean empty = items.isEmpty();
            txtEmpty.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
            rv.setVisibility(empty ? android.view.View.GONE : android.view.View.VISIBLE);
        }));
    }

    private void openItem(ChatListItem it) {
        if (it.isGroup) {
            Intent i = new Intent(this, GroupChatActivity.class);
            i.putExtra("group_id", it.id);
            i.putExtra("group_name", it.name);
            i.putExtra("my_role", it.myRole);
            i.putExtra("admin_only_mode", it.adminOnlyMode);
            startActivity(i);
        } else {
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra("peer_id", it.id);
            i.putExtra("peer_name", it.name);
            i.putExtra("peer_color", it.colorHex);
            i.putExtra("peer_avatar_url", it.avatarUrl);
            startActivity(i);
        }
        AnimUtils.forward(this);
    }

    private void showUnarchiveMenu(ChatListItem it) {
        new TDialog.Builder(this)
                .setTitle(it.name)
                .setItems(new CharSequence[]{getString(R.string.unarchive)}, (dialog, which) -> {
                    String peerType = it.isGroup ? "group" : "user";
                    apiClient.setChatState(peerType, it.id, null, false, null, (ok, d, e) -> reload());
                })
                .show();
    }
}
