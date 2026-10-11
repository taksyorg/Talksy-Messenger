package com.talksy.messenger.sync;

import android.content.Context;

import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.net.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HttpRemoteApi implements RemoteApi {
    private final ApiClient api;
    private final Prefs prefs;
    private final Context ctx;

    public HttpRemoteApi(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        api = new ApiClient(ctx);
        api.setBackground(true);
        prefs = new Prefs(ctx);
    }

    private String token() throws SyncException {
        String t = prefs.get(Prefs.KEY_AUTH_TOKEN, "");
        if (t.isEmpty()) throw SyncException.auth("нет токена");
        return URLEncoder.encode(t);
    }

    private JSONObject call(String method, String path, JSONObject body) throws SyncException {
        try {
            JSONObject r = api.syncCall(method, path + (path.contains("?") ? "&" : "?") + "token=" + token(), body);
            if (!r.optBoolean("ok", false)) {
                String err = r.optString("error", "Ошибка сервера");
                if (err.contains("авторизован")) throw SyncException.auth(err);
                throw SyncException.network(err);
            }
            return r;
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw SyncException.network(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void uploadLocalMedia(Op op) throws SyncException {
        Object lp = op.payload.get("local_media_path");
        if (lp == null || op.payload.get("media_url") != null) return;
        java.io.File f = new java.io.File(String.valueOf(lp));
        if (!f.exists()) { op.payload.remove("local_media_path"); return; }
        final com.talksy.messenger.data.model.MediaAttachment[] res = new com.talksy.messenger.data.model.MediaAttachment[1];
        final String[] err = new String[1];
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        String name = String.valueOf(op.payload.get("media_filename"));
        String mime = op.payload.get("media_mime") == null ? "application/octet-stream" : String.valueOf(op.payload.get("media_mime"));
        api.uploadMediaStream(android.net.Uri.fromFile(f), name, mime, f.length(), null, null, (ok, media, error) -> {
            if (ok && media != null) res[0] = media; else err[0] = error;
            done.countDown();
        });
        try { if (!done.await(10, java.util.concurrent.TimeUnit.MINUTES)) throw SyncException.network("загрузка файла: таймаут"); }
        catch (InterruptedException e) { throw SyncException.network("загрузка файла прервана"); }
        if (res[0] == null) throw SyncException.network("загрузка файла: " + err[0]);
        op.payload.put("media_url", res[0].url); op.payload.put("media_type", res[0].type);
        op.payload.put("media_filename", res[0].filename); op.payload.put("media_size", res[0].size);
        op.payload.remove("media_mime");
        LocalDb.get(ctx).savePayload(op);
    }

    @Override public List<OpResult> push(List<Op> ops) throws SyncException {
        try {
            for (Op op : ops) if (Op.MESSAGE_SEND.equals(op.type)) uploadLocalMedia(op);
            JSONArray arr = new JSONArray();
            for (Op op : ops) {
                JSONObject o = LocalDb.toJson(op.payload);
                o.remove("local_media_path");
                o.put("op_id", op.opId); o.put("type", op.type);
                arr.put(o);
            }
            JSONObject body = new JSONObject().put("ops", arr);
            JSONObject r = call("POST", "/sync/push", body);
            JSONArray res = r.getJSONArray("results");
            List<OpResult> out = new ArrayList<>();
            for (int i = 0; i < res.length(); i++) {
                JSONObject o = res.getJSONObject(i); OpResult x = new OpResult();
                x.opId = o.optString("op_id"); x.status = o.optString("status"); x.error = o.optString("error", null);
                x.reason = o.optString("reason", null); x.retryable = o.optBoolean("retryable", false);
                if (o.optJSONObject("entity") != null) x.entity = LocalDb.toMap(o.optJSONObject("entity"));
                JSONArray cf = o.optJSONArray("conflict_fields");
                if (cf != null) { x.conflictFields = new ArrayList<>(); for (int j = 0; j < cf.length(); j++) x.conflictFields.add(cf.getString(j)); }
                out.add(x);
            }
            return out;
        } catch (SyncException e) { throw e; }
        catch (Exception e) { throw SyncException.network("push: " + e.getMessage()); }
    }

    @Override public PullResult pull(long cursor, int limit) throws SyncException {
        try {
            JSONObject r = call("GET", "/sync/pull?cursor=" + cursor + "&limit=" + limit, null);
            PullResult p = new PullResult();
            p.cursor = r.optLong("cursor", cursor); p.hasMore = r.optBoolean("has_more"); p.fullResync = r.optBoolean("full_resync");
            p.serverTime = r.optDouble("server_time");
            p.changes = new ArrayList<>();
            JSONArray arr = r.optJSONArray("changes");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i); Change c = new Change();
                c.entity = o.optString("entity"); c.op = o.optString("op"); c.seq = o.optLong("seq");
                Map<String, Object> data = LocalDb.toMap(o.optJSONObject("data")); c.data = data;
                p.changes.add(c);
            }
            return p;
        } catch (SyncException e) { throw e; }
        catch (Exception e) { throw SyncException.network("pull: " + e.getMessage()); }
    }

    public boolean registerPushToken(String deviceId, String platform, String token) {
        try {
            call("POST", "/sync/devices", new JSONObject().put("device_id", deviceId).put("platform", platform).put("push_token", token));
            return true;
        } catch (Exception e) { return false; }
    }
}
