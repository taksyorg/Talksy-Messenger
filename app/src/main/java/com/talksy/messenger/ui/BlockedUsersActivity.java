package com.talksy.messenger.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.talksy.messenger.ui.TDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.adapters.BlockedUsersAdapter;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.model.User;
import com.talksy.messenger.net.ApiClient;

import java.util.ArrayList;
import java.util.List;

public class BlockedUsersActivity extends BaseActivity {

    private final List<User> items = new ArrayList<>();
    private BlockedUsersAdapter adapter;
    private ApiClient apiClient;
    private TextView txtEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked_users);

        apiClient = new ApiClient(this);
        ImageButton btnBack = findViewById(R.id.btn_back);
        txtEmpty = findViewById(R.id.txt_empty);
        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new BlockedUsersAdapter(items, apiClient.getBaseUrl(), this::confirmUnblock);
        recycler.setAdapter(adapter);

        btnBack.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        AnimUtils.pressScale(btnBack);

        load();
    }

    private void load() {
        apiClient.getBlockedUsers((ok, users, error) -> {
            if (!ok || users == null) {
                Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
                return;
            }
            items.clear();
            items.addAll(users);
            adapter.notifyDataSetChanged();
            txtEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void confirmUnblock(User user, int position) {
        new TDialog.Builder(this)
                .setTitle(user.name)
                .setMessage(R.string.blocked_unblock_confirm)
                .setPositiveButton(R.string.unblock, (d, w) -> unblock(user, position))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void unblock(User user, int position) {
        apiClient.unblockUser(user.id, (ok, v, error) -> {
            if (!ok) {
                Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
                return;
            }
            if (position >= 0 && position < items.size()) {
                items.remove(position);
                adapter.notifyItemRemoved(position);
                txtEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }
}
