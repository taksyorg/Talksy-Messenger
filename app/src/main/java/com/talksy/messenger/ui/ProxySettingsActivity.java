package com.talksy.messenger.ui;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.R;
import com.talksy.messenger.net.ProxyManager;

public class ProxySettingsActivity extends BaseActivity {

    private ProxyManager proxyManager;

    private RadioGroup radioMode;
    private android.widget.RadioButton radioOff, radioSocks5, radioCustom;
    private View groupFields;
    private EditText inpHost, inpPort, inpUser, inpPass, inpSecret;
    private TextView lblUser, lblPass, lblSecret;
    private TextView txtResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_proxy_settings);

        proxyManager = new ProxyManager(this);

        ImageButton btnBack = findViewById(R.id.btn_back);
        radioMode = findViewById(R.id.radio_mode);
        radioOff = findViewById(R.id.radio_off);
        radioSocks5 = findViewById(R.id.radio_socks5);
        radioCustom = findViewById(R.id.radio_custom);
        groupFields = findViewById(R.id.group_fields);
        inpHost = findViewById(R.id.inp_host);
        inpPort = findViewById(R.id.inp_port);
        inpUser = findViewById(R.id.inp_user);
        inpPass = findViewById(R.id.inp_pass);
        inpSecret = findViewById(R.id.inp_secret);
        lblUser = findViewById(R.id.lbl_user);
        lblPass = findViewById(R.id.lbl_pass);
        lblSecret = findViewById(R.id.lbl_secret);
        txtResult = findViewById(R.id.txt_result);
        Button btnTest = findViewById(R.id.btn_test);
        Button btnSave = findViewById(R.id.btn_save);

        btnBack.setOnClickListener(v -> { finish(); AnimUtils.back(this); });

        String mode = proxyManager.getMode();
        if (ProxyManager.MODE_SOCKS5.equals(mode)) radioSocks5.setChecked(true);
        else if (ProxyManager.MODE_CUSTOM.equals(mode)) radioCustom.setChecked(true);
        else radioOff.setChecked(true);

        inpHost.setText(proxyManager.getHost());
        if (proxyManager.getPort() > 0) inpPort.setText(String.valueOf(proxyManager.getPort()));
        inpUser.setText(proxyManager.getUser());
        inpPass.setText(proxyManager.getPass());
        inpSecret.setText(proxyManager.getSecret());

        updateFieldsVisibility();
        radioMode.setOnCheckedChangeListener((group, checkedId) -> updateFieldsVisibility());

        btnTest.setOnClickListener(v -> runTest());
        btnSave.setOnClickListener(v -> save(true));
    }

    private String selectedMode() {
        if (radioSocks5.isChecked()) return ProxyManager.MODE_SOCKS5;
        if (radioCustom.isChecked()) return ProxyManager.MODE_CUSTOM;
        return ProxyManager.MODE_OFF;
    }

    private void updateFieldsVisibility() {
        String mode = selectedMode();
        boolean showFields = !ProxyManager.MODE_OFF.equals(mode);
        groupFields.setVisibility(showFields ? View.VISIBLE : View.GONE);

        boolean isSocks5 = ProxyManager.MODE_SOCKS5.equals(mode);
        lblUser.setVisibility(isSocks5 ? View.VISIBLE : View.GONE);
        inpUser.setVisibility(isSocks5 ? View.VISIBLE : View.GONE);
        lblPass.setVisibility(isSocks5 ? View.VISIBLE : View.GONE);
        inpPass.setVisibility(isSocks5 ? View.VISIBLE : View.GONE);

        boolean isCustom = ProxyManager.MODE_CUSTOM.equals(mode);
        lblSecret.setVisibility(isCustom ? View.VISIBLE : View.GONE);
        inpSecret.setVisibility(isCustom ? View.VISIBLE : View.GONE);

        txtResult.setVisibility(View.GONE);
    }

    private void save(boolean showToast) {
        String mode = selectedMode();
        String host = inpHost.getText().toString().trim();
        int port = parsePort();
        String user = inpUser.getText().toString().trim();
        String pass = inpPass.getText().toString();
        String secret = inpSecret.getText().toString().trim();

        proxyManager.save(mode, host, port, user, pass, secret);
        if (showToast) Toast.makeText(this, R.string.save, Toast.LENGTH_SHORT).show();
    }

    private int parsePort() {
        try { return Integer.parseInt(inpPort.getText().toString().trim()); }
        catch (Exception e) { return 0; }
    }

    private void runTest() {
        save(false);
        txtResult.setVisibility(View.VISIBLE);
        txtResult.setText("…");
        txtResult.setTextColor(getResources().getColor(R.color.text2));

        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                return proxyManager.testConnection();
            }
            @Override
            protected void onPostExecute(Boolean ok) {
                if (isFinishing()) return;
                txtResult.setText(ok ? getString(R.string.proxy_ok) : getString(R.string.proxy_fail));
                txtResult.setTextColor(getResources().getColor(ok ? R.color.ok : R.color.err));
            }
        }.execute();
    }
}
