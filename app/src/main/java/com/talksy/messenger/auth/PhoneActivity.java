package com.talksy.messenger.auth;

import com.talksy.messenger.ui.TDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.Countries;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.data.model.Country;
import com.talksy.messenger.net.ApiClient;

import java.util.ArrayList;
import java.util.List;

public class PhoneActivity extends BaseActivity {

    private Spinner spinnerCountry;
    private TextView txtDialCode;
    private EditText inpPhone;
    private TextView txtError;
    private TextView btnServer;
    private ApiClient apiClient;
    private List<Country> countries;
    private boolean formatting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone);

        apiClient = new ApiClient(this);
        countries = Countries.all();

        spinnerCountry = findViewById(R.id.spinner_country);
        txtDialCode = findViewById(R.id.txt_dial_code);
        inpPhone = findViewById(R.id.inp_phone);
        txtError = findViewById(R.id.txt_error);
        btnServer = findViewById(R.id.btn_server);
        btnServer.setVisibility(android.view.View.GONE);
        Button btnContinue = findViewById(R.id.btn_continue);

        ArrayAdapter<Country> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, countries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCountry.setAdapter(adapter);

        int defaultIndex = Countries.guessDefaultIndex();
        spinnerCountry.setSelection(defaultIndex);
        applyCountry(countries.get(defaultIndex));

        spinnerCountry.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                applyCountry(countries.get(position));
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        inpPhone.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { reformat(); }
        });

        btnContinue.setOnClickListener(v -> onContinue());
        findViewById(R.id.btn_number_login).setOnClickListener(v -> {
            startActivity(new Intent(this, NumberLoginActivity.class));
            AnimUtils.forward(this);
        });
        btnServer.setOnClickListener(v -> showServerDialog());

        refreshServerLabel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshServerLabel();
    }

    private void applyCountry(Country country) {
        txtDialCode.setText("+" + country.dialCode);
        inpPhone.setText("");
        inpPhone.setHint(buildMask(country.maxDigits));
    }

    private void reformat() {
        if (formatting) return;
        formatting = true;

        Country country = countries.get(spinnerCountry.getSelectedItemPosition());
        String raw = inpPhone.getText().toString().replaceAll("[^0-9]", "");
        if (raw.length() > country.maxDigits) raw = raw.substring(0, country.maxDigits);

        String formatted = formatDigits(raw, groupSizes(country.maxDigits));
        inpPhone.setText(formatted);
        inpPhone.setSelection(formatted.length());

        formatting = false;
    }

    private List<Integer> groupSizes(int total) {
        List<Integer> sizes = new ArrayList<>();
        int remaining = total;
        for (int i = 0; i < 2 && remaining > 3; i++) {
            sizes.add(3);
            remaining -= 3;
        }
        if (remaining > 0) sizes.add(remaining);
        return sizes;
    }

    private String formatDigits(String digits, List<Integer> groups) {
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (int i = 0; i < groups.size() && pos < digits.length(); i++) {
            if (i > 0) sb.append('-');
            int size = groups.get(i);
            int end = Math.min(pos + size, digits.length());
            sb.append(digits, pos, end);
            pos = end;
        }
        return sb.toString();
    }

    private String buildMask(int maxDigits) {
        StringBuilder zeros = new StringBuilder();
        for (int i = 0; i < maxDigits; i++) zeros.append('0');
        return formatDigits(zeros.toString(), groupSizes(maxDigits));
    }

    private void refreshServerLabel() {
        String url = apiClient.getBaseUrl();
        btnServer.setText(url.isEmpty()
                ? getString(R.string.server_not_set_hint)
                : getString(R.string.server_label_prefix) + url);
    }

    private void showServerDialog() {
        EditText input = new EditText(this);
        input.setHint("host:port");
        input.setText(apiClient.getBaseUrl());
        input.setSingleLine(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new TDialog.Builder(this)
                .setTitle(getString(R.string.server_address_title))
                .setMessage(getString(R.string.server_address_message))
                .setView(input)
                .setPositiveButton(getString(R.string.save), (dialog, which) -> {
                    apiClient.setBaseUrl(input.getText().toString().trim());
                    refreshServerLabel();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onContinue() {
        Country country = countries.get(spinnerCountry.getSelectedItemPosition());
        String digits = inpPhone.getText().toString().replaceAll("[^0-9]", "");

        if (digits.length() < country.minDigits || digits.length() > country.maxDigits) {
            txtError.setText(getString(R.string.auth_phone_error) +
                    " (" + country.name + ": " + country.minDigits +
                    (country.minDigits == country.maxDigits ? "" : "–" + country.maxDigits) + getString(R.string.digits_suffix));
            txtError.setVisibility(android.view.View.VISIBLE);
            return;
        }
        txtError.setVisibility(android.view.View.GONE);

        String fullPhone = "+" + country.dialCode + digits;
        new Prefs(this).put(Prefs.KEY_PHONE, fullPhone);

        startActivity(new Intent(this, EmailActivity.class));
        AnimUtils.forward(this);
    }
}
