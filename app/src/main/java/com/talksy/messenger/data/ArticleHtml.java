package com.talksy.messenger.data;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ArticleHtml {
    private ArticleHtml() {}

    private static String e(String s) { return TextUtils.htmlEncode(s == null ? "" : s); }
    private static String nl(String s) { return e(s).replace("\n", "<br>"); }

    public static String build(JSONObject article, boolean dark) {
        StringBuilder b = new StringBuilder(2048);
        b.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
         .append("<link rel=\"stylesheet\" href=\"katex.min.css\"><script src=\"katex.min.js\"></script><style>")
         .append(":root{--bg:").append(dark ? "#0f1117" : "#ffffff").append(";--txt:").append(dark ? "#eef0f6" : "#161824")
         .append(";--dim:").append(dark ? "#8b90a3" : "#6b7084").append(";--line:").append(dark ? "#2a2e3d" : "#dfe2ee").append(";--acc:#6366f1}")
         .append("body{margin:0;padding:18px 18px 40px;background:var(--bg);color:var(--txt);font:17px/1.55 system-ui,Roboto,sans-serif;word-wrap:break-word}")
         .append("h1{font-size:26px;line-height:1.25;margin:0 0 18px}p{margin:0 0 14px}ul,ol{margin:0 0 14px;padding-left:24px}li{margin:3px 0}")
         .append(".chk{list-style:none;padding-left:2px}.chk li{display:flex;gap:10px;align-items:flex-start}.chk .box{width:20px;height:20px;border:2px solid var(--acc);border-radius:6px;flex:none;margin-top:3px;display:flex;align-items:center;justify-content:center;font-size:14px;color:#fff}")
         .append(".chk .on{background:var(--acc)}.chk .done{color:var(--dim);text-decoration:line-through}")
         .append("details{border:1px solid var(--line);border-radius:12px;padding:10px 14px;margin:0 0 14px}summary{font-weight:600;cursor:pointer}details p{margin:10px 0 0}")
         .append("table{border-collapse:collapse;margin:0 0 14px;min-width:100%}td,th{border:1px solid var(--line);padding:8px 10px;text-align:left}tr:first-child td{font-weight:600;background:rgba(99,102,241,.12)}")
         .append(".wrap{overflow-x:auto;margin:0 0 14px}.math{margin:0 0 14px;overflow-x:auto;text-align:center;font-size:1.15em}.err{color:#ef4444}")
         .append("</style></head><body>");
        String title = article.optString("title");
        if (!title.isEmpty()) b.append("<h1>").append(e(title)).append("</h1>");
        JSONArray blocks = article.optJSONArray("blocks");
        for (int i = 0; blocks != null && i < blocks.length(); i++) {
            JSONObject bl = blocks.optJSONObject(i);
            if (bl == null) continue;
            switch (bl.optString("t")) {
                case "p": if (!bl.optString("text").trim().isEmpty()) b.append("<p>").append(nl(bl.optString("text"))).append("</p>"); break;
                case "ul": case "ol": {
                    String tag = bl.optString("t");
                    b.append("<").append(tag).append(">");
                    JSONArray items = bl.optJSONArray("items");
                    for (int k = 0; items != null && k < items.length(); k++) if (!items.optString(k).trim().isEmpty()) b.append("<li>").append(e(items.optString(k))).append("</li>");
                    b.append("</").append(tag).append(">");
                    break;
                }
                case "check": {
                    b.append("<ul class=\"chk\">");
                    JSONArray items = bl.optJSONArray("items");
                    for (int k = 0; items != null && k < items.length(); k++) {
                        JSONObject it = items.optJSONObject(k);
                        if (it == null || it.optString("text").trim().isEmpty()) continue;
                        boolean on = it.optBoolean("done");
                        b.append("<li><span class=\"box").append(on ? " on" : "").append("\">").append(on ? "✓" : "").append("</span><span")
                         .append(on ? " class=\"done\"" : "").append(">").append(e(it.optString("text"))).append("</span></li>");
                    }
                    b.append("</ul>");
                    break;
                }
                case "fold":
                    b.append("<details><summary>").append(e(bl.optString("title").isEmpty() ? "Заголовок" : bl.optString("title"))).append("</summary><p>")
                     .append(nl(bl.optString("text"))).append("</p></details>");
                    break;
                case "table": {
                    b.append("<div class=\"wrap\"><table>");
                    JSONArray rows = bl.optJSONArray("rows");
                    for (int r = 0; rows != null && r < rows.length(); r++) {
                        JSONArray row = rows.optJSONArray(r);
                        if (row == null) continue;
                        b.append("<tr>");
                        for (int c = 0; c < row.length(); c++) b.append("<td>").append(e(row.optString(c))).append("</td>");
                        b.append("</tr>");
                    }
                    b.append("</table></div>");
                    break;
                }
                case "math":
                    b.append("<div class=\"math\" data-tex=\"").append(e(bl.optString("tex"))).append("\"></div>");
                    break;
                default: break;
            }
        }
        b.append("<script>document.querySelectorAll('.math').forEach(function(el){try{katex.render(el.getAttribute('data-tex'),el,{displayMode:true,throwOnError:true,trust:false,maxExpand:1000});}catch(x){el.className='math err';el.textContent='Ошибка в формуле';}});</script>");
        return b.append("</body></html>").toString();
    }

    public static String snippet(JSONObject article, int maxChars) {
        StringBuilder s = new StringBuilder();
        JSONArray blocks = article.optJSONArray("blocks");
        for (int i = 0; blocks != null && i < blocks.length() && s.length() < maxChars; i++) {
            JSONObject bl = blocks.optJSONObject(i);
            if (bl == null) continue;
            String t = bl.optString("t");
            String piece = "";
            if ("p".equals(t)) piece = bl.optString("text");
            else if ("fold".equals(t)) piece = bl.optString("title");
            else if ("math".equals(t)) piece = "ƒ " + bl.optString("tex");
            else if ("table".equals(t)) piece = "▦ таблица";
            else if ("ul".equals(t) || "ol".equals(t)) { JSONArray a = bl.optJSONArray("items"); if (a != null && a.length() > 0) piece = "• " + a.optString(0); }
            else if ("check".equals(t)) { JSONArray a = bl.optJSONArray("items"); if (a != null && a.length() > 0 && a.optJSONObject(0) != null) piece = "☐ " + a.optJSONObject(0).optString("text"); }
            piece = piece.trim().replace("\n", " ");
            if (!piece.isEmpty()) { if (s.length() > 0) s.append(" · "); s.append(piece); }
        }
        String out = s.toString();
        return out.length() > maxChars ? out.substring(0, maxChars) + "…" : out;
    }
}
