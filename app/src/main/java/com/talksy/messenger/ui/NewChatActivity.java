package com.talksy.messenger.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.R;
import com.talksy.messenger.adapters.UserSearchAdapter;
import com.talksy.messenger.data.model.User;
import com.talksy.messenger.net.ApiClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NewChatActivity extends BaseActivity {

    public static final String EXTRA_PICK_FOR_GROUP = "pick_for_group";

    private RecyclerView rvResults;
    private TextView txtEmpty;
    private TextView txtSectionLabel;
    private UserSearchAdapter adapter;
    private final List<User> results = new ArrayList<>();
    private ApiClient apiClient;
    private long pickForGroupId = 0;

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private final ActivityResultLauncher<String> requestContactsPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) loadContacts();
                else showContactsPlaceholder();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_chat);

        apiClient = new ApiClient(this);
        pickForGroupId = getIntent().getLongExtra(EXTRA_PICK_FOR_GROUP, 0);

        ImageButton btnBack = findViewById(R.id.btn_back);
        EditText inpSearch = findViewById(R.id.inp_search);
        rvResults = findViewById(R.id.rv_results);
        txtEmpty = findViewById(R.id.txt_empty);
        txtSectionLabel = findViewById(R.id.txt_section_label);

        rvResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserSearchAdapter(results, apiClient.getBaseUrl(), this::openChatWith);
        rvResults.setAdapter(adapter);
        if (!AnimUtils.isEnabled(this)) rvResults.setItemAnimator(null);

        btnBack.setOnClickListener(v -> { finish(); AnimUtils.back(this); });

        if (pickForGroupId > 0) {
            setTitle(getString(R.string.add_participant));
            findViewById(R.id.row_action_chat).setVisibility(android.view.View.GONE);
            findViewById(R.id.row_action_group).setVisibility(android.view.View.GONE);
        } else {
            findViewById(R.id.row_action_chat).setOnClickListener(v -> inpSearch.requestFocus());
            findViewById(R.id.row_action_group).setOnClickListener(v -> {
                startActivity(new Intent(this, CreateGroupActivity.class));
                AnimUtils.forward(this);
            });
        }
        findViewById(R.id.row_action_channel).setVisibility(android.view.View.GONE);

        inpSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { scheduleSearch(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        requestContactsAndLoad();
    }

    private void requestContactsAndLoad() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContacts();
        } else {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS);
        }
    }

    private void loadContacts() {
        txtSectionLabel.setText(getString(R.string.contacts_in_app_header));
        txtEmpty.setText(getString(R.string.searching_contacts));
        txtEmpty.setVisibility(android.view.View.VISIBLE);
        rvResults.setVisibility(android.view.View.GONE);

        new Thread(() -> {
            List<String> phones = readDeviceContactPhones();
            runOnUiThread(() -> {
                if (!apiClient.isConfigured()) {
                    showContactsPlaceholder();
                    return;
                }
                if (phones.isEmpty()) {
                    txtEmpty.setText(getString(R.string.no_phone_contacts));
                    return;
                }
                apiClient.findUsersByPhones(phones, (ok, list, error) -> {
                    results.clear();
                    if (ok && list != null) results.addAll(list);
                    adapter.notifyDataSetChanged();
                    boolean empty = results.isEmpty();
                    txtEmpty.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
                    rvResults.setVisibility(empty ? android.view.View.GONE : android.view.View.VISIBLE);
                    txtEmpty.setText(getString(R.string.no_registered_contacts));
                });
            });
        }).start();
    }

    private void showContactsPlaceholder() {
        txtSectionLabel.setText(getString(R.string.contacts_in_app_header));
        txtEmpty.setText(getString(R.string.grant_contacts_permission));
        txtEmpty.setVisibility(android.view.View.VISIBLE);
        rvResults.setVisibility(android.view.View.GONE);
    }

    private List<String> readDeviceContactPhones() {
        Set<String> phones = new HashSet<>();
        try (Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, null)) {
            if (cursor != null) {
                int idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                while (cursor.moveToNext()) {
                    String number = cursor.getString(idx);
                    if (number != null && !number.trim().isEmpty()) phones.add(number.trim());
                }
            }
        } catch (Exception ignored) {}
        return new ArrayList<>(phones);
    }

    private void scheduleSearch(String query) {
        if (pendingSearch != null) debounceHandler.removeCallbacks(pendingSearch);
        pendingSearch = () -> search(query);
        debounceHandler.postDelayed(pendingSearch, 350);
    }

    private void search(String query) {
        String q = query.trim();
        if (EasterEggs.handle(this, q)) {
            android.widget.EditText box = findViewById(R.id.inp_search);
            if (box != null) box.setText("");
            return;
        }
        if (q.isEmpty()) {
            loadContacts();
            return;
        }
        txtSectionLabel.setText(getString(R.string.search_results_header));

        if (!apiClient.isConfigured()) {
            txtEmpty.setText(getString(R.string.server_not_configured_settings));
            txtEmpty.setVisibility(android.view.View.VISIBLE);
            rvResults.setVisibility(android.view.View.GONE);
            return;
        }

        apiClient.searchUsers(q, (ok, list, error) -> {
            results.clear();
            if (ok && list != null) results.addAll(list);
            adapter.notifyDataSetChanged();
            boolean empty = results.isEmpty();
            txtEmpty.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
            rvResults.setVisibility(empty ? android.view.View.GONE : android.view.View.VISIBLE);
            txtEmpty.setText("Пользователь с таким username не найден");
        });
    }

    private void openChatWith(User user) {
        if (pickForGroupId > 0) {
            apiClient.addGroupMember(pickForGroupId, user.id, false, (ok, v, error) -> {
                if (ok) {
                    Toast.makeText(this, getString(R.string.member_added), Toast.LENGTH_SHORT).show();
                    finish();
                    AnimUtils.back(this);
                } else {
                    Toast.makeText(this, error != null ? error : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("peer_id", user.id);
        i.putExtra("peer_name", user.name);
        i.putExtra("peer_color", user.colorHex == null ? "#6366F1" : user.colorHex);
        i.putExtra("peer_avatar_url", user.avatarUrl);
        startActivity(i);
        AnimUtils.forward(this);
        finish();
    }
}
