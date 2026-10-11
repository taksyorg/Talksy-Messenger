package com.talksy.messenger.ui;

import com.talksy.messenger.ui.TDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.adapters.GroupMemberAdapter;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.model.Group;
import com.talksy.messenger.data.model.GroupMember;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.net.AvatarLoader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class GroupInfoActivity extends BaseActivity {

    private long groupId;
    private ApiClient apiClient;
    private final List<GroupMember> members = new ArrayList<>();
    private boolean canManage;
    private boolean iAmOwner;
    private TextView txtGroupName, txtGroupHandle;
    private ImageView imgAvatar;

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) onImagePicked(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_info);

        apiClient = new ApiClient(this);
        groupId = getIntent().getLongExtra("group_id", -1);
        String groupName = getIntent().getStringExtra("group_name");
        String myRole = getIntent().getStringExtra("my_role");
        boolean adminOnly = getIntent().getBooleanExtra("admin_only_mode", false);
        canManage = "owner".equals(myRole) || "admin".equals(myRole);
        iAmOwner = "owner".equals(myRole);

        ImageButton btnBack = findViewById(R.id.btn_back);
        View frameAvatar = findViewById(R.id.frame_avatar);
        imgAvatar = findViewById(R.id.img_avatar);
        txtGroupName = findViewById(R.id.txt_group_name);
        txtGroupHandle = findViewById(R.id.txt_group_handle);
        View rowAdminOnly = findViewById(R.id.row_admin_only);
        Switch switchAdminOnly = findViewById(R.id.switch_admin_only);
        View rowReactions = findViewById(R.id.row_reactions);
        Switch switchReactions = findViewById(R.id.switch_reactions);
        RecyclerView rvMembers = findViewById(R.id.rv_members);
        View rowAddParticipant = findViewById(R.id.row_add_participant);
        rowAddParticipant.setVisibility(canManage ? View.VISIBLE : View.GONE);
        rowAddParticipant.setOnClickListener(v -> {
            Intent i = new Intent(this, NewChatActivity.class);
            i.putExtra(NewChatActivity.EXTRA_PICK_FOR_GROUP, groupId);
            startActivity(i);
            AnimUtils.forward(this);
        });

        txtGroupName.setText(groupName);
        btnBack.setOnClickListener(v -> { finish(); AnimUtils.back(this); });

        if (canManage) {
            frameAvatar.setOnClickListener(v -> pickImage.launch("image/*"));
            txtGroupName.setOnClickListener(v -> showRenameDialog());
            txtGroupHandle.setOnClickListener(v -> showHandleDialog());
        }

        switchAdminOnly.setChecked(adminOnly);
        switchAdminOnly.setEnabled(canManage);
        rowAdminOnly.setAlpha(canManage ? 1f : 0.5f);
        switchAdminOnly.setOnCheckedChangeListener((btn, checked) -> {
            if (!canManage) return;
            apiClient.setGroupAdminOnlyMode(groupId, checked, (ok, group, error) -> {
                if (!ok) {
                    Toast.makeText(this, error != null ? error : getString(R.string.group_setting_change_failed), Toast.LENGTH_SHORT).show();
                    switchAdminOnly.setChecked(!checked);
                }
            });
        });

        switchReactions.setChecked(true);
        switchReactions.setEnabled(canManage);
        rowReactions.setAlpha(canManage ? 1f : 0.5f);
        switchReactions.setOnCheckedChangeListener((btn, checked) -> {
            if (!canManage) return;
            apiClient.setGroupReactionsEnabled(groupId, checked, (ok, group, error) -> {
                if (!ok) {
                    Toast.makeText(this, error != null ? error : getString(R.string.group_setting_change_failed), Toast.LENGTH_SHORT).show();
                    switchReactions.setChecked(!checked);
                }
            });
        });

        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        GroupMemberAdapter adapter = new GroupMemberAdapter(members, apiClient.getBaseUrl(), iAmOwner, (member, newRole) -> {
            apiClient.setMemberRole(groupId, member.id, newRole, (ok, data, error) -> {
                if (ok) {
                    member.role = newRole;
                    rvMembers.getAdapter().notifyDataSetChanged();
                } else {
                    Toast.makeText(this, error != null ? error : getString(R.string.group_role_change_failed), Toast.LENGTH_SHORT).show();
                }
            });
        });
        rvMembers.setAdapter(adapter);

        loadGroupInfo();

        apiClient.getGroupMembers(groupId, (ok, list, error) -> {
            if (ok && list != null) {
                members.clear();
                members.addAll(list);
                adapter.notifyDataSetChanged();
            } else {

                rvMembers.setVisibility(View.GONE);
                TextView note = findViewById(R.id.txt_members_hidden);
                note.setText(R.string.members_hidden_note);
                note.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadGroupInfo() {

        apiClient.getGroup(groupId, (ok, g, error) -> {
            if (ok && g != null) bindGroup(g);
        });
    }

    private void bindChannelExtras(Group g) {
        TextView title = findViewById(R.id.txt_members_title);
        title.setText(getString(R.string.members_title_count, g.membersCount));
        View row = findViewById(R.id.row_members_visible);
        final Switch sw = findViewById(R.id.switch_members_visible);
        row.setVisibility(iAmOwner && g.isChannel ? View.VISIBLE : View.GONE);
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(g.membersVisible);
        sw.setOnCheckedChangeListener((btn, checked) -> {
            if (!iAmOwner) return;
            apiClient.setGroupMembersVisible(groupId, checked, (ok, group, error) -> {
                if (!ok) {
                    Toast.makeText(this, error != null ? error : getString(R.string.group_setting_change_failed),
                            Toast.LENGTH_SHORT).show();
                    sw.setChecked(!checked);
                }
            });
        });
    }

    private void bindCommentsSetting(Group g) {
        if (!canManage || !g.isChannel) return;
        View rowAdminOnly = findViewById(R.id.row_admin_only);
        android.view.ViewGroup parent = (android.view.ViewGroup) rowAdminOnly.getParent();
        if (parent.findViewWithTag("comments_row") != null) return;
        apiClient.getChannelStats(groupId, (okS, st, errS) -> {
            if (!okS || st == null || parent.findViewWithTag("comments_row") != null) return;
            Switch sw = new Switch(this);
            com.talksy.messenger.ui.widget.SettingsUi.Row row = com.talksy.messenger.ui.widget.SettingsUi.row(this, "secure_chat",
                    getString(R.string.comments_setting), getString(R.string.comments_setting_sub), null, v -> sw.toggle());
            row.view.setTag("comments_row");
            row.view.setBackgroundResource(R.drawable.bg_panel);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(12 * 3, 12 * 3 / 2, 12 * 3, 12 * 3 / 2);
            row.view.addView(sw);
            sw.setChecked(st.commentsEnabled);
            sw.setOnCheckedChangeListener((b, on) -> apiClient.setCommentsEnabled(groupId, on, (ok2, v2, e2) -> {
                if (!ok2) { Toast.makeText(this, e2 != null ? e2 : getString(R.string.group_setting_change_failed), Toast.LENGTH_SHORT).show(); b.setOnCheckedChangeListener(null); b.setChecked(!on); }
            }));
            parent.addView(row.view, parent.indexOfChild(rowAdminOnly) + 1, lp);
        });
    }

    private void bindGroup(Group g) {
        bindChannelExtras(g);
        bindCommentsSetting(g);
        txtGroupName.setText(g.name);
        txtGroupHandle.setText(g.handle == null ? (canManage ? getString(R.string.group_set_username) : "") : g.handle);
        if (g.avatarUrl != null && !g.avatarUrl.isEmpty()) {
            AvatarLoader.load(apiClient.getBaseUrl() + g.avatarUrl, bmp -> {
                imgAvatar.setImageBitmap(bmp);
                imgAvatar.setVisibility(View.VISIBLE);
            });
        }
        Switch switchReactions = findViewById(R.id.switch_reactions);
        switchReactions.setOnCheckedChangeListener(null);
        switchReactions.setChecked(g.reactionsEnabled);
        switchReactions.setOnCheckedChangeListener((btn, checked) -> {
            if (!canManage) return;
            apiClient.setGroupReactionsEnabled(groupId, checked, (ok, group, error) -> {
                if (!ok) {
                    Toast.makeText(this, error != null ? error : getString(R.string.group_setting_change_failed), Toast.LENGTH_SHORT).show();
                    switchReactions.setChecked(!checked);
                }
            });
        });

        View rowChannel = findViewById(R.id.row_channel);
        Switch switchChannel = findViewById(R.id.switch_channel);
        View rowPrivate = findViewById(R.id.row_private);
        Switch switchPrivate = findViewById(R.id.switch_private);

        rowChannel.setVisibility(iAmOwner ? View.VISIBLE : View.GONE);
        rowPrivate.setVisibility(iAmOwner ? View.VISIBLE : View.GONE);

        switchChannel.setOnCheckedChangeListener(null);
        switchChannel.setChecked(g.isChannel);
        switchPrivate.setOnCheckedChangeListener(null);
        switchPrivate.setChecked(g.isPrivate);

        switchChannel.setOnCheckedChangeListener((btn, checked) -> {
            if (!iAmOwner) return;
            apiClient.setGroupVisibility(groupId, checked, switchPrivate.isChecked(), (ok, group, error) -> {
                if (!ok) {
                    Toast.makeText(this, error != null ? error : getString(R.string.group_setting_change_failed), Toast.LENGTH_SHORT).show();
                    switchChannel.setChecked(!checked);
                }
            });
        });
        switchPrivate.setOnCheckedChangeListener((btn, checked) -> {
            if (!iAmOwner) return;
            apiClient.setGroupVisibility(groupId, switchChannel.isChecked(), checked, (ok, group, error) -> {
                if (!ok) {
                    Toast.makeText(this, error != null ? error : getString(R.string.group_setting_change_failed), Toast.LENGTH_SHORT).show();
                    switchPrivate.setChecked(!checked);
                }
            });
        });
    }

    private void showRenameDialog() {
        EditText input = new EditText(this);
        input.setText(txtGroupName.getText());
        new TDialog.Builder(this)
                .setTitle(getString(R.string.group_name_title))
                .setView(input)
                .setPositiveButton(getString(R.string.save), (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) return;
                    apiClient.updateGroupInfo(groupId, newName, null, (ok, group, error) -> {
                        if (ok && group != null) txtGroupName.setText(group.name);
                        else Toast.makeText(this, error != null ? error : getString(R.string.group_rename_failed), Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showHandleDialog() {
        EditText input = new EditText(this);
        input.setHint(getString(R.string.group_username_hint));
        new TDialog.Builder(this)
                .setTitle(getString(R.string.group_username_title))
                .setView(input)
                .setPositiveButton(getString(R.string.save), (d, w) -> {
                    String newHandle = input.getText().toString().trim();
                    if (newHandle.isEmpty()) return;
                    apiClient.updateGroupInfo(groupId, null, newHandle, (ok, group, error) -> {
                        if (ok && group != null) txtGroupHandle.setText(group.handle);
                        else Toast.makeText(this, error != null ? error : getString(R.string.group_username_save_failed), Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onImagePicked(Uri uri) {
        Toast.makeText(this, getString(R.string.uploading_photo), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Bitmap bitmap = decodeSampled(uri, 512);
                if (bitmap == null) return;
                Bitmap square = centerCropSquare(bitmap, 512);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                square.compress(Bitmap.CompressFormat.JPEG, 80, bos);
                String base64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);

                runOnUiThread(() -> apiClient.uploadGroupAvatar(groupId, base64, (ok, group, error) -> {
                    if (ok) {
                        imgAvatar.setImageBitmap(AvatarLoader.toRoundedSquare(square));
                        imgAvatar.setVisibility(View.VISIBLE);
                    } else {
                        Toast.makeText(this, error != null ? error : getString(R.string.photo_upload_failed), Toast.LENGTH_LONG).show();
                    }
                }));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.photo_process_error) + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private Bitmap decodeSampled(Uri uri, int maxDim) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, bounds);
        }
        int sample = 1;
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(is, null, opts);
        }
    }

    private Bitmap centerCropSquare(Bitmap src, int targetSize) {
        int size = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - size) / 2;
        int y = (src.getHeight() - size) / 2;
        Bitmap cropped = Bitmap.createBitmap(src, x, y, size, size);
        return Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true);
    }
}
