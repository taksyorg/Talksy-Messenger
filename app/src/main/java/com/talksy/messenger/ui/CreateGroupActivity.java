package com.talksy.messenger.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.net.CryptoManager;

public class CreateGroupActivity extends BaseActivity {

    private ApiClient apiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        apiClient = new ApiClient(this);

        ImageButton btnBack = findViewById(R.id.btn_back);
        EditText inpName = findViewById(R.id.inp_name);
        Button btnCreate = findViewById(R.id.btn_create);

        btnBack.setOnClickListener(v -> { finish(); AnimUtils.back(this); });

        btnCreate.setOnClickListener(v -> {
            String name = inpName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.enter_group_name), Toast.LENGTH_SHORT).show();
                return;
            }
            btnCreate.setEnabled(false);
            apiClient.createGroup(name, (ok, group, error) -> {
                if (!ok || group == null) {
                    btnCreate.setEnabled(true);
                    Toast.makeText(this, error != null ? error : getString(R.string.group_create_failed), Toast.LENGTH_LONG).show();
                    return;
                }
                setupEncryptionAndOpen(group);
            });
        });
    }

    private void setupEncryptionAndOpen(com.talksy.messenger.data.model.Group group) {
        new Thread(() -> {
            try {
                CryptoManager.ensureKeyPairExists();
                byte[] groupKey = CryptoManager.generateGroupKey();
                String wrapped = CryptoManager.wrapGroupKeyForSelf(groupKey);
                runOnUiThread(() -> apiClient.setOwnGroupKey(group.id, wrapped, (ok, d, error) -> {
                    Intent i = new Intent(this, GroupChatActivity.class);
                    i.putExtra("group_id", group.id);
                    i.putExtra("group_name", group.name);
                    i.putExtra("my_role", group.myRole);
                    i.putExtra("admin_only_mode", group.adminOnlyMode);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                    AnimUtils.forward(this);
                    finish();
                }));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    findViewById(R.id.btn_create).setEnabled(true);
                    Toast.makeText(this, getString(R.string.encryption_setup_error) + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
