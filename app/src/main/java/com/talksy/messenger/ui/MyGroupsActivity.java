package com.talksy.messenger.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.adapters.GroupListAdapter;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.model.Group;
import com.talksy.messenger.net.ApiClient;

import java.util.ArrayList;
import java.util.List;

public class MyGroupsActivity extends BaseActivity {

    public static final String EXTRA_PICK_BOT_ID = "pick_bot_id";

    private RecyclerView rvGroups;
    private TextView txtEmpty;
    private TextView txtSectionLabel;
    private GroupListAdapter adapter;
    private final List<Group> groups = new ArrayList<>();
    private ApiClient apiClient;
    private long pickBotId = 0;

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_groups);

        apiClient = new ApiClient(this);
        pickBotId = getIntent().getLongExtra(EXTRA_PICK_BOT_ID, 0);
        if (pickBotId > 0) setTitle(getString(R.string.pick_group_for_bot));

        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnCreate = findViewById(R.id.btn_create_group);
        EditText inpSearch = findViewById(R.id.inp_search);
        rvGroups = findViewById(R.id.rv_groups);
        txtEmpty = findViewById(R.id.txt_empty);
        txtSectionLabel = findViewById(R.id.txt_section_label);

        rvGroups.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GroupListAdapter(groups, apiClient.getBaseUrl(), this::openGroup, this::joinGroup);
        rvGroups.setAdapter(adapter);
        if (!AnimUtils.isEnabled(this)) rvGroups.setItemAnimator(null);

        btnBack.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        btnCreate.setOnClickListener(v -> {
            startActivity(new Intent(this, CreateGroupActivity.class));
            AnimUtils.forward(this);
        });

        inpSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { scheduleSearch(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMyGroups();
    }

    private void loadMyGroups() {
        txtSectionLabel.setText(getString(R.string.my_groups_header));
        apiClient.getMyGroups((ok, list, error) -> {
            groups.clear();
            if (ok && list != null) groups.addAll(list);
            adapter.notifyDataSetChanged();
            refreshEmpty();
        });
    }

    private void scheduleSearch(String query) {
        if (pendingSearch != null) debounceHandler.removeCallbacks(pendingSearch);
        pendingSearch = () -> search(query);
        debounceHandler.postDelayed(pendingSearch, 350);
    }

    private void search(String query) {
        String q = query.trim();
        if (q.isEmpty()) { loadMyGroups(); return; }
        txtSectionLabel.setText(getString(R.string.found_header));
        apiClient.searchGroups(q, (ok, list, error) -> {
            groups.clear();
            if (ok && list != null) groups.addAll(list);
            adapter.notifyDataSetChanged();
            refreshEmpty();
        });
    }

    private void refreshEmpty() {
        boolean empty = groups.isEmpty();
        txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvGroups.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openGroup(Group g) {
        if (pickBotId > 0) {
            apiClient.addGroupMember(g.id, pickBotId, true, (ok, v, error) -> {
                if (ok) {
                    Toast.makeText(this, getString(R.string.bot_added_to_group), Toast.LENGTH_SHORT).show();
                    finish();
                    AnimUtils.back(this);
                } else {
                    Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        Intent i = new Intent(this, GroupChatActivity.class);
        i.putExtra("group_id", g.id);
        i.putExtra("group_name", g.name);
        i.putExtra("my_role", g.myRole);
        i.putExtra("admin_only_mode", g.adminOnlyMode);
        startActivity(i);
        AnimUtils.forward(this);
    }

    private void joinGroup(Group g) {
        apiClient.joinGroup(g.id, (ok, joined, error) -> {
            if (ok && joined != null) {
                Toast.makeText(this, getString(R.string.joined_group_prefix) + joined.name + "»", Toast.LENGTH_SHORT).show();
                openGroup(joined);
            } else {
                Toast.makeText(this, error != null ? error : getString(R.string.join_group_failed), Toast.LENGTH_LONG).show();
            }
        });
    }
}
