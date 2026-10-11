package com.talksy.messenger.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.IconManager;
import com.talksy.messenger.data.SpecialMessage;
import com.talksy.messenger.ui.widget.SettingsUi;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ArticleEditorActivity extends BaseActivity {
    public static final String EXTRA_PAYLOAD = "payload";
    private static final int MAX_BYTES = 60_000;

    private EditText title;
    private LinearLayout blocksBox;
    private final List<JSONObject> state = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private int hIndex = -1;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean restoring;
    private final Runnable debounced = this::pullAndSnapshot;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(SettingsUi.dp(this, 8), 0, SettingsUi.dp(this, 8), 0);
        ImageButton back = new ImageButton(this);
        back.setBackgroundResource(R.drawable.ripple_item_round); back.setImageResource(R.drawable.ic_back);
        back.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        bar.addView(back, new LinearLayout.LayoutParams(SettingsUi.dp(this, 44), SettingsUi.dp(this, 44)));
        TextView t = new TextView(this); t.setText(R.string.article_title); t.setTextSize(18); t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(ContextCompat.getColor(this, R.color.text1));
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(toolText("↶", v -> undo()), new LinearLayout.LayoutParams(SettingsUi.dp(this, 44), SettingsUi.dp(this, 44)));
        bar.addView(toolText("↷", v -> redo()), new LinearLayout.LayoutParams(SettingsUi.dp(this, 44), SettingsUi.dp(this, 44)));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 56)));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(SettingsUi.dp(this, 16), SettingsUi.dp(this, 4), SettingsUi.dp(this, 16), SettingsUi.dp(this, 24));
        title = new EditText(this);
        title.setHint(R.string.article_name_hint); title.setTextSize(24); title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.text1)); title.setHintTextColor(ContextCompat.getColor(this, R.color.text3));
        title.setBackground(null); title.setMaxLines(3);
        title.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        title.addTextChangedListener(watcher());
        col.addView(title);
        blocksBox = new LinearLayout(this); blocksBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(blocksBox);
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setBackgroundColor(ContextCompat.getColor(this, R.color.bg2));
        tools.setPadding(SettingsUi.dp(this, 6), SettingsUi.dp(this, 4), SettingsUi.dp(this, 10), SettingsUi.dp(this, 4));
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.addView(toolText("Aa", v -> addBlock(newBlock("p"), true)));
        View listBtn = toolText("☰", v -> showListMenu(v));
        row.addView(listBtn);
        row.addView(toolText("▦", v -> tableDialog()));
        row.addView(toolText("ƒ", v -> formulaDialog(null)));
        for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(SettingsUi.dp(this, 48), SettingsUi.dp(this, 44)));
        hs.addView(row);
        tools.addView(hs, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton send = new ImageButton(this);
        send.setBackgroundResource(R.drawable.bg_btn_record); int pad = SettingsUi.dp(this, 11); send.setPadding(pad, pad, pad, pad);
        IconManager.set(send, "send", Color.WHITE);
        send.setOnClickListener(v -> send());
        tools.addView(send, new LinearLayout.LayoutParams(SettingsUi.dp(this, 44), SettingsUi.dp(this, 44)));
        root.addView(tools);
        setContentView(root);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        addBlock(newBlock("p"), false);
        snapshot();
    }

    private TextView toolText(String s, View.OnClickListener c) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(20); t.setGravity(Gravity.CENTER); t.setTextColor(ContextCompat.getColor(this, R.color.accent));
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setBackgroundResource(R.drawable.ripple_item_round);
        t.setOnClickListener(c);
        return t;
    }

    private TextWatcher watcher() {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) { if (!restoring) { h.removeCallbacks(debounced); h.postDelayed(debounced, 800); } }
        };
    }

    private JSONObject newBlock(String type) {
        JSONObject o = new JSONObject();
        try {
            o.put("t", type);
            switch (type) {
                case "p": o.put("text", ""); break;
                case "ul": case "ol": o.put("items", new JSONArray().put("")); break;
                case "check": o.put("items", new JSONArray().put(new JSONObject().put("text", "").put("done", false))); break;
                case "fold": o.put("title", "").put("text", ""); break;
                default: break;
            }
        } catch (Exception ignored) {}
        return o;
    }

    private void addBlock(JSONObject block, boolean focus) {
        pullViews();
        state.add(block);
        rebuild(focus ? state.size() - 1 : -1);
        snapshot();
    }

    private void rebuild(int focusIndex) {
        restoring = true;
        blocksBox.removeAllViews();
        for (int i = 0; i < state.size(); i++) blocksBox.addView(blockView(i, state.get(i), i == focusIndex));
        restoring = false;
    }

    private void pullViews() {
        for (int i = 0; i < blocksBox.getChildCount() && i < state.size(); i++) {
            View v = blocksBox.getChildAt(i);
            Object tag = v.getTag();
            if (tag instanceof Reader) { try { ((Reader) tag).read(state.get(i)); } catch (Exception ignored) {} }
        }
    }

    private interface Reader { void read(JSONObject block) throws Exception; }

    private void pullAndSnapshot() { pullViews(); snapshot(); }

    private JSONObject toJson() {
        pullViews();
        JSONObject o = new JSONObject();
        try {
            o.put("title", title.getText().toString().trim());
            JSONArray arr = new JSONArray();
            for (JSONObject b : state) arr.put(new JSONObject(b.toString()));
            o.put("blocks", arr);
        } catch (Exception ignored) {}
        return o;
    }

    private void snapshot() {
        String s = toJson().toString();
        if (hIndex >= 0 && history.get(hIndex).equals(s)) return;
        while (history.size() > hIndex + 1) history.remove(history.size() - 1);
        history.add(s); hIndex = history.size() - 1;
        if (history.size() > 60) { history.remove(0); hIndex--; }
    }

    private void undo() { h.removeCallbacks(debounced); pullAndSnapshot(); if (hIndex > 0) restore(history.get(--hIndex)); }
    private void redo() { if (hIndex < history.size() - 1) restore(history.get(++hIndex)); }

    private void restore(String json) {
        try {
            JSONObject o = new JSONObject(json);
            restoring = true;
            title.setText(o.optString("title"));
            state.clear();
            JSONArray a = o.optJSONArray("blocks");
            for (int i = 0; a != null && i < a.length(); i++) state.add(a.getJSONObject(i));
            restoring = false;
            rebuild(-1);
        } catch (Exception ignored) { restoring = false; }
    }

    private EditText edit(String hint, String text, boolean multi) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(text); e.setTextSize(16);
        e.setTextColor(ContextCompat.getColor(this, R.color.text1)); e.setHintTextColor(ContextCompat.getColor(this, R.color.text3));
        e.setBackground(null);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | (multi ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        e.addTextChangedListener(watcher());
        return e;
    }

    private View blockView(int index, JSONObject b, boolean focus) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.TOP);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String type = b.optString("t");
        EditText first = null;
        switch (type) {
            case "p": {
                EditText e = edit(getString(R.string.article_text_hint), b.optString("text"), true); body.addView(e); first = e;
                wrap.setTag((Reader) o -> o.put("text", e.getText().toString()));
                break;
            }
            case "ul": case "ol": {
                body.addView(label(getString("ul".equals(type) ? R.string.article_list_bullet : R.string.article_list_number)));
                JSONArray it = b.optJSONArray("items");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; it != null && i < it.length(); i++) { if (i > 0) sb.append('\n'); sb.append(it.optString(i)); }
                EditText e = edit(getString(R.string.article_items_hint), sb.toString(), true); body.addView(e); first = e;
                wrap.setTag((Reader) o -> { JSONArray a = new JSONArray(); for (String line : e.getText().toString().split("\n", -1)) a.put(line); o.put("items", a); });
                break;
            }
            case "check": {
                body.addView(label(getString(R.string.article_list_check)));
                LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); body.addView(rows);
                final List<EditText> texts = new ArrayList<>(); final List<CheckBox> boxes = new ArrayList<>();
                JSONArray it = b.optJSONArray("items");
                for (int i = 0; it != null && i < it.length(); i++) {
                    JSONObject o = it.optJSONObject(i); if (o == null) continue;
                    first = addCheckRow(rows, texts, boxes, o.optString("text"), o.optBoolean("done"), first);
                }
                Button add = new Button(this); add.setText(R.string.article_add_item); add.setAllCaps(false);
                add.setOnClickListener(v -> { EditText n = addCheckRow(rows, texts, boxes, "", false, null); n.requestFocus(); });
                body.addView(add);
                wrap.setTag((Reader) o -> { JSONArray a = new JSONArray(); for (int i = 0; i < texts.size(); i++) a.put(new JSONObject().put("text", texts.get(i).getText().toString()).put("done", boxes.get(i).isChecked())); o.put("items", a); });
                break;
            }
            case "fold": {
                body.addView(label(getString(R.string.article_list_fold)));
                EditText ti = edit(getString(R.string.article_fold_title), b.optString("title"), false); ti.setTypeface(ti.getTypeface(), android.graphics.Typeface.BOLD);
                EditText tx = edit(getString(R.string.article_fold_text), b.optString("text"), true);
                body.addView(ti); body.addView(tx); first = ti;
                wrap.setTag((Reader) o -> { o.put("title", ti.getText().toString()); o.put("text", tx.getText().toString()); });
                break;
            }
            case "table": buildTable(body, wrap, b); break;
            case "math": {
                body.addView(label(getString(R.string.article_formula)));
                TextView tv = new TextView(this);
                tv.setText("ƒ  " + b.optString("tex")); tv.setTextSize(17); tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                tv.setTextColor(ContextCompat.getColor(this, R.color.text1));
                GradientDrawable g = new GradientDrawable(); g.setCornerRadius(SettingsUi.dp(this, 12)); g.setColor(ContextCompat.getColor(this, R.color.bg2)); tv.setBackground(g);
                tv.setPadding(SettingsUi.dp(this, 14), SettingsUi.dp(this, 12), SettingsUi.dp(this, 14), SettingsUi.dp(this, 12));
                tv.setOnClickListener(v -> formulaDialog(b));
                body.addView(tv);
                break;
            }
            default: break;
        }
        ImageButton del = new ImageButton(this);
        del.setBackgroundResource(R.drawable.ripple_item_round); IconManager.set(del, "close", ContextCompat.getColor(this, R.color.text3));
        del.setOnClickListener(v -> { pullViews(); state.remove(index); if (state.isEmpty()) state.add(newBlock("p")); rebuild(-1); snapshot(); });
        wrap.addView(del, new LinearLayout.LayoutParams(SettingsUi.dp(this, 32), SettingsUi.dp(this, 32)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = SettingsUi.dp(this, 6);
        wrap.setLayoutParams(lp);
        if (focus && first != null) { final EditText f = first; f.post(() -> { f.requestFocus(); ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(f, 0); }); }
        return wrap;
    }

    private TextView label(String s) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(11); t.setTextColor(ContextCompat.getColor(this, R.color.accent));
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD); return t;
    }

    private EditText addCheckRow(LinearLayout rows, List<EditText> texts, List<CheckBox> boxes, String text, boolean done, EditText first) {
        LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox cb = new CheckBox(this); cb.setChecked(done); cb.setOnCheckedChangeListener((x, y) -> { if (!restoring) { h.removeCallbacks(debounced); h.postDelayed(debounced, 400); } });
        EditText e = edit(getString(R.string.article_item_hint), text, false);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        e.setOnEditorActionListener((v, a, ev) -> { EditText n = addCheckRow(rows, texts, boxes, "", false, null); n.requestFocus(); return true; });
        r.addView(cb); r.addView(e, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        rows.addView(r); texts.add(e); boxes.add(cb);
        return first != null ? first : e;
    }

    private void buildTable(LinearLayout body, LinearLayout wrap, JSONObject b) {
        body.addView(label(getString(R.string.article_table)));
        final List<List<EditText>> cells = new ArrayList<>();
        LinearLayout grid = new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView hs = new HorizontalScrollView(this); hs.addView(grid); body.addView(hs);
        JSONArray rows = b.optJSONArray("rows");
        for (int r = 0; rows != null && r < rows.length(); r++) {
            JSONArray row = rows.optJSONArray(r); if (row == null) continue;
            addTableRow(grid, cells, row.length(), row);
        }
        LinearLayout ctl = new LinearLayout(this); body.addView(ctl);
        ctl.addView(smallBtn("+ " + getString(R.string.article_row), v -> { int cols = cells.isEmpty() ? 2 : cells.get(0).size(); addTableRow(grid, cells, cols, null); }));
        ctl.addView(smallBtn("+ " + getString(R.string.article_col), v -> { for (int r = 0; r < cells.size(); r++) { EditText e = cell(""); ((LinearLayout) grid.getChildAt(r)).addView(e); cells.get(r).add(e); } }));
        ctl.addView(smallBtn("− " + getString(R.string.article_row), v -> { if (cells.size() > 1) { grid.removeViewAt(cells.size() - 1); cells.remove(cells.size() - 1); } }));
        ctl.addView(smallBtn("− " + getString(R.string.article_col), v -> { if (!cells.isEmpty() && cells.get(0).size() > 1) for (int r = 0; r < cells.size(); r++) { List<EditText> rc = cells.get(r); ((LinearLayout) grid.getChildAt(r)).removeView(rc.remove(rc.size() - 1)); } }));
        wrap.setTag((Reader) o -> { JSONArray a = new JSONArray(); for (List<EditText> rc : cells) { JSONArray ra = new JSONArray(); for (EditText e : rc) ra.put(e.getText().toString()); a.put(ra); } o.put("rows", a); });
    }

    private EditText cell(String text) {
        EditText e = edit("", text, false);
        e.setMinWidth(SettingsUi.dp(this, 96)); e.setSingleLine(true);
        GradientDrawable g = new GradientDrawable(); g.setStroke(1, ContextCompat.getColor(this, R.color.bg3)); e.setBackground(g);
        e.setPadding(SettingsUi.dp(this, 8), SettingsUi.dp(this, 8), SettingsUi.dp(this, 8), SettingsUi.dp(this, 8));
        return e;
    }

    private void addTableRow(LinearLayout grid, List<List<EditText>> cells, int cols, JSONArray values) {
        LinearLayout r = new LinearLayout(this); List<EditText> rc = new ArrayList<>();
        for (int c = 0; c < cols; c++) { EditText e = cell(values == null ? "" : values.optString(c)); r.addView(e); rc.add(e); }
        grid.addView(r); cells.add(rc);
    }

    private Button smallBtn(String s, View.OnClickListener c) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(12); b.setOnClickListener(c);
        b.setMinWidth(0); b.setMinimumWidth(0); return b;
    }

    private void tableDialog() {
        final String[] sizes = {"2 × 2", "3 × 2", "3 × 3", "4 × 3", "5 × 4"};
        final int[][] dims = {{2, 2}, {3, 2}, {3, 3}, {4, 3}, {5, 4}};
        new TDialog.Builder(this).setTitle(R.string.article_table).setItems(sizes, (d, w) -> {
            JSONObject t = newBlock("table");
            try { JSONArray rows = new JSONArray(); for (int r = 0; r < dims[w][0]; r++) { JSONArray row = new JSONArray(); for (int c = 0; c < dims[w][1]; c++) row.put(""); rows.put(row); } t.put("rows", rows); } catch (Exception ignored) {}
            addBlock(t, false);
        }).show();
    }

    private void showListMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, R.string.article_list_bullet);
        pm.getMenu().add(0, 2, 1, R.string.article_list_number);
        pm.getMenu().add(0, 3, 2, R.string.article_list_check);
        pm.getMenu().add(0, 4, 3, R.string.article_list_fold);
        pm.setOnMenuItemClickListener(it -> { addBlock(newBlock(it.getItemId() == 1 ? "ul" : it.getItemId() == 2 ? "ol" : it.getItemId() == 3 ? "check" : "fold"), true); return true; });
        pm.show();
    }

    private final class FormulaBridge {
        final Button ok;
        FormulaBridge(Button ok) { this.ok = ok; }
        @JavascriptInterface public void result(boolean valid) { runOnUiThread(() -> ok.setEnabled(valid)); }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void formulaDialog(JSONObject existing) {
        BottomSheetDialog d = new BottomSheetDialog(this);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(SettingsUi.dp(this, 16), SettingsUi.dp(this, 16), SettingsUi.dp(this, 16), SettingsUi.dp(this, 16));
        box.setBackgroundColor(ContextCompat.getColor(this, R.color.bg2));
        WebView web = new WebView(this);
        WebSettings st = web.getSettings(); st.setJavaScriptEnabled(true); st.setAllowFileAccess(false); st.setBlockNetworkLoads(true);
        web.setBackgroundColor(0);
        box.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 110)));
        EditText in = new EditText(this);
        in.setHint(R.string.article_formula_hint); in.setSingleLine(false); in.setMaxLines(3);
        in.setTextColor(ContextCompat.getColor(this, R.color.text1)); in.setHintTextColor(ContextCompat.getColor(this, R.color.text3));
        in.setTypeface(android.graphics.Typeface.MONOSPACE); in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        GradientDrawable g = new GradientDrawable(); g.setCornerRadius(SettingsUi.dp(this, 12)); g.setColor(ContextCompat.getColor(this, R.color.bg3)); in.setBackground(g);
        in.setPadding(SettingsUi.dp(this, 14), SettingsUi.dp(this, 12), SettingsUi.dp(this, 14), SettingsUi.dp(this, 12));
        box.addView(in);
        Button ok = new Button(this, null, 0, R.style.TalksyButton); ok.setText(R.string.done); ok.setEnabled(false);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(this, 50)); op.topMargin = SettingsUi.dp(this, 12);
        box.addView(ok, op);
        web.addJavascriptInterface(new FormulaBridge(ok), "Host");
        boolean dark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        String page = "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><link rel=stylesheet href=katex.min.css><script src=katex.min.js></script>"
                + "<style>body{margin:0;display:flex;align-items:center;justify-content:center;height:100vh;background:transparent;color:" + (dark ? "#eef0f6" : "#161824") + ";font:22px system-ui}"
                + ".err{color:#ef4444;font:700 26px Georgia,serif}.hint{color:#8b90a3;font:15px system-ui}</style></head><body><div id=o class=hint>LaTeX</div>"
                + "<script>function render(t){var o=document.getElementById('o');if(!t.trim()){o.className='hint';o.textContent='LaTeX';Host.result(false);return;}"
                + "try{katex.render(t,o,{displayMode:true,throwOnError:true,trust:false,maxExpand:1000});o.className='';Host.result(true);}catch(e){o.className='err';o.textContent='Ошибка';Host.result(false);}}</script></body></html>";
        web.loadDataWithBaseURL("file:///android_asset/katex/", page, "text/html", "utf-8", null);
        in.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { web.evaluateJavascript("render(" + JSONObject.quote(s.toString()) + ")", null); }
            @Override public void afterTextChanged(Editable s) {}
        });
        if (existing != null) { in.setText(existing.optString("tex")); in.setSelection(in.getText().length()); }
        else web.postDelayed(() -> web.evaluateJavascript("render('')", null), 300);
        ok.setOnClickListener(v -> {
            String tex = in.getText().toString().trim();
            try {
                if (existing != null) { pullViews(); existing.put("tex", tex); rebuild(-1); snapshot(); }
                else { JSONObject m = new JSONObject().put("t", "math").put("tex", tex); addBlock(m, false); }
            } catch (Exception ignored) {}
            d.dismiss();
        });
        d.setContentView(box);
        d.setOnDismissListener(x -> web.destroy());
        d.show();
        in.requestFocus();
    }

    private void send() {
        JSONObject a = toJson();
        boolean empty = a.optString("title").isEmpty();
        JSONArray bl = a.optJSONArray("blocks");
        for (int i = 0; bl != null && i < bl.length() && empty; i++) {
            JSONObject b = bl.optJSONObject(i); if (b == null) continue;
            if (!b.optString("text").trim().isEmpty() || !b.optString("tex").isEmpty() || !b.optString("title").isEmpty() || "table".equals(b.optString("t"))) empty = false;
            JSONArray items = b.optJSONArray("items");
            for (int k = 0; items != null && k < items.length() && empty; k++) { Object it = items.opt(k); if (it instanceof String ? !((String) it).trim().isEmpty() : it instanceof JSONObject && !((JSONObject) it).optString("text").trim().isEmpty()) empty = false; }
        }
        if (empty) { Toast.makeText(this, R.string.article_empty, Toast.LENGTH_SHORT).show(); return; }
        String payload = SpecialMessage.encode(SpecialMessage.ARTICLE, a);
        if (payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) { Toast.makeText(this, R.string.article_too_big, Toast.LENGTH_LONG).show(); return; }
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_PAYLOAD, payload));
        finish();
    }
}
