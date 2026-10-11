package com.talksy.messenger.sync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;

import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.data.model.ChatListItem;
import com.talksy.messenger.data.model.MediaAttachment;
import com.talksy.messenger.data.model.Message;
import com.talksy.messenger.data.model.Reaction;
import com.talksy.messenger.data.model.User;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class LocalDb extends SQLiteOpenHelper implements LocalStore {
    private static final int DB_VERSION = 4;
    private static LocalDb instance;
    private static String instanceName;

    public static synchronized LocalDb get(Context ctx) {
        Context app = ctx.getApplicationContext();
        String token = new Prefs(app).get(Prefs.KEY_AUTH_TOKEN, "");
        String name = "talksy.db";
        if (instance == null || !name.equals(instanceName)) {
            if (instance != null) instance.close();
            instance = new LocalDb(app, name);
            instanceName = name;
        }
        return instance;
    }

    public static synchronized void wipe(Context ctx) {
        LocalDb db = get(ctx);
        String name = instanceName;
        db.close();
        instance = null; instanceName = null;
        ctx.getApplicationContext().deleteDatabase(name);
    }

    private LocalDb(Context ctx, String name) { super(ctx, name, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users(id INTEGER PRIMARY KEY, name TEXT, handle TEXT, phone TEXT, email TEXT, bio TEXT, " +
                "color TEXT, avatar_url TEXT, is_bot INTEGER DEFAULT 0, public_key TEXT, last_seen INTEGER, " +
                "is_me INTEGER DEFAULT 0, updated_at INTEGER DEFAULT 0, version INTEGER DEFAULT 1, sync_status TEXT DEFAULT 'SYNCED')");
        db.execSQL("CREATE TABLE chats(chat_key TEXT PRIMARY KEY, peer_type TEXT NOT NULL, peer_id INTEGER NOT NULL, name TEXT, " +
                "handle TEXT, color TEXT, avatar_url TEXT, is_bot INTEGER DEFAULT 0, my_role TEXT, admin_only INTEGER DEFAULT 0, " +
                "last_message TEXT, last_message_time INTEGER DEFAULT 0, last_outgoing INTEGER DEFAULT 0, unread_count INTEGER DEFAULT 0, " +
                "pinned INTEGER DEFAULT 0, archived INTEGER DEFAULT 0, muted INTEGER DEFAULT 0, peer_last_read INTEGER DEFAULT 0, " +
                "my_read_sent INTEGER DEFAULT 0, extra_json TEXT, updated_at INTEGER DEFAULT 0, version INTEGER DEFAULT 1, " +
                "sync_status TEXT DEFAULT 'SYNCED')");
        db.execSQL("CREATE TABLE messages(local_id INTEGER PRIMARY KEY AUTOINCREMENT, server_id INTEGER UNIQUE, client_uuid TEXT UNIQUE, " +
                "chat_key TEXT NOT NULL, sender_id INTEGER, outgoing INTEGER DEFAULT 0, text TEXT, time_ms INTEGER, " +
                "media_url TEXT, media_type TEXT, media_filename TEXT, media_size INTEGER, media_duration INTEGER DEFAULT 0, media_waveform TEXT, views INTEGER DEFAULT 0, comments_count INTEGER DEFAULT 0, enc_text TEXT, enc_key_recipient TEXT, " +
                "enc_key_sender TEXT, iv TEXT, reply_to_id INTEGER, story_id INTEGER, edited INTEGER DEFAULT 0, deleted INTEGER DEFAULT 0, " +
                "reactions_json TEXT, reply_markup TEXT, updated_at INTEGER DEFAULT 0, version INTEGER DEFAULT 0, " +
                "sync_status TEXT DEFAULT 'PENDING', last_error TEXT, conflict_text TEXT, sender_name TEXT, media_local_path TEXT)");
        db.execSQL("CREATE INDEX idx_msg_chat ON messages(chat_key, time_ms)");
        db.execSQL("CREATE TABLE outbox(seq INTEGER PRIMARY KEY AUTOINCREMENT, op_id TEXT UNIQUE NOT NULL, type TEXT NOT NULL, " +
                "chat_key TEXT, ref_local_id INTEGER, payload TEXT NOT NULL, created_at INTEGER, attempts INTEGER DEFAULT 0, " +
                "next_retry_at INTEGER DEFAULT 0, state TEXT DEFAULT 'PENDING')");
        db.execSQL("CREATE TABLE sync_state(k TEXT PRIMARY KEY, v TEXT)");
        db.execSQL("CREATE TABLE http_cache(k TEXT PRIMARY KEY, body TEXT, ts INTEGER)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) {
            db.execSQL("ALTER TABLE messages ADD COLUMN sender_name TEXT");
            db.execSQL("ALTER TABLE messages ADD COLUMN media_local_path TEXT");
            db.execSQL("CREATE TABLE IF NOT EXISTS http_cache(k TEXT PRIMARY KEY, body TEXT, ts INTEGER)");
        }
        if (o < 3) {
            db.execSQL("ALTER TABLE messages ADD COLUMN media_duration INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE messages ADD COLUMN media_waveform TEXT");
        }
        if (o < 4) {
            db.execSQL("ALTER TABLE messages ADD COLUMN views INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE messages ADD COLUMN comments_count INTEGER DEFAULT 0");
        }
    }

    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean notifyScheduled;

    public void addListener(Runnable r) { listeners.addIfAbsent(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }
    private void notifyChanged() {
        if (notifyScheduled) return;
        notifyScheduled = true;
        main.post(() -> { notifyScheduled = false; for (Runnable r : listeners) r.run(); });
    }

    public static String userKey(long id) { return "u:" + id; }
    public static String groupKey(long id) { return "g:" + id; }

    public static String normalizeChatKey(String key) {
        if (key == null) return "";

        if (key.startsWith("user:")) {
            return "u:" + key.substring(5);
        }

        if (key.matches("\\d+")) {
            return "u:" + key;
        }

        return key;
    }

private static long now() { return System.currentTimeMillis(); }

    private static Object cv(Map<String, Object> m, String k) { return m == null ? null : m.get(k); }
    private static String str(Map<String, Object> m, String k) { Object o = cv(m, k); return o == null || JSONObject.NULL.equals(o) ? null : String.valueOf(o); }
    private static long lng(Map<String, Object> m, String k) { Object o = cv(m, k); return o instanceof Number ? ((Number) o).longValue() : 0; }
    private static double dbl(Map<String, Object> m, String k) { Object o = cv(m, k); return o instanceof Number ? ((Number) o).doubleValue() : 0; }
    private static boolean bool(Map<String, Object> m, String k) {
        Object o = cv(m, k);
        if (o instanceof Boolean) return (Boolean) o;
        return o instanceof Number && ((Number) o).intValue() != 0;
    }
    private static int b(boolean v) { return v ? 1 : 0; }

    public static Map<String, Object> toMap(JSONObject o) {
        Map<String, Object> m = new HashMap<>();
        if (o == null) return m;
        Iterator<String> it = o.keys();
        while (it.hasNext()) { String k = it.next(); m.put(k, unwrap(o.opt(k))); }
        return m;
    }
    static Object unwrap(Object v) {
        if (v == null || JSONObject.NULL.equals(v)) return null;
        if (v instanceof JSONObject) return toMap((JSONObject) v);
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v; List<Object> l = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) l.add(unwrap(a.opt(i)));
            return l;
        }
        return v;
    }
    static JSONObject toJson(Map<String, Object> m) {
        JSONObject o = new JSONObject();
        try { for (Map.Entry<String, Object> e : m.entrySet()) o.put(e.getKey(), wrap(e.getValue())); } catch (JSONException ignored) {}
        return o;
    }
    @SuppressWarnings("unchecked")
    private static Object wrap(Object v) {
        if (v == null) return JSONObject.NULL;
        if (v instanceof Map) return toJson((Map<String, Object>) v);
        if (v instanceof List) { JSONArray a = new JSONArray(); for (Object x : (List<Object>) v) a.put(wrap(x)); return a; }
        return v;
    }

    private String getState(String k, String def) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT v FROM sync_state WHERE k=?", new String[]{k})) {
            return c.moveToFirst() ? c.getString(0) : def;
        }
    }
    private void setState(SQLiteDatabase db, String k, String v) {
        db.execSQL("INSERT OR REPLACE INTO sync_state(k,v) VALUES (?,?)", new Object[]{k, v});
    }

    public boolean hasAnyData() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM chats LIMIT 1", null)) { return c.moveToFirst(); }
    }

    public List<ChatListItem> getChatList(boolean archived) {
        List<ChatListItem> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM chats WHERE archived=? AND (last_message_time>0 OR peer_type='group') ORDER BY pinned DESC, last_message_time DESC",
                new String[]{archived ? "1" : "0"})) {
            java.util.HashSet<String> seenKeys = new java.util.HashSet<>();
            while (c.moveToNext()) {
                ChatListItem it = new ChatListItem();
                boolean group = "group".equals(s(c, "peer_type"));
                if (!seenKeys.add((group ? "g" : "u") + l(c, "peer_id"))) continue;
                it.id = l(c, "peer_id"); it.isGroup = group;
                it.name = s(c, "name"); it.isBot = !group && l(c, "is_bot") == 1;
                it.handle = s(c, "handle"); it.colorHex = group ? "#06B6D4" : s(c, "color");
                it.avatarUrl = s(c, "avatar_url"); it.lastMessage = s(c, "last_message");
                it.lastMessageTime = l(c, "last_message_time"); it.pinned = l(c, "pinned") == 1; it.muted = l(c, "muted") == 1;
                it.unreadCount = (int) l(c, "unread_count");
                if (group) { it.myRole = s(c, "my_role"); it.adminOnlyMode = l(c, "admin_only") == 1; }
                out.add(it);
            }
        }
        return out;
    }

    public int archivedCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM chats WHERE archived=1 AND (last_message_time>0 OR peer_type='group')", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public List<Message> getMessages(String chatKey) {
        List<Message> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM messages WHERE chat_key=? ORDER BY time_ms ASC, local_id ASC", new String[]{chatKey})) {
            while (c.moveToNext()) out.add(readMessage(c));
        }
        long peerRead = getPeerLastReadMs(chatKey);
        if (peerRead > 0) for (Message m : out) if (m.outgoing && m.id > 0 && m.time <= peerRead) m.read = true;
        return out;
    }

    public void setPeerLastReadMs(String chatKey, long ms) {
        if (ms <= 0) return;
        getWritableDatabase().execSQL("UPDATE chats SET peer_last_read=? WHERE chat_key=? AND peer_last_read<?", new Object[]{ms, chatKey, ms});
    }

    public long getPeerLastReadMs(String chatKey) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT peer_last_read FROM chats WHERE chat_key=?", new String[]{chatKey})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public User getUser(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM users WHERE id=?", new String[]{String.valueOf(id)})) {
            if (!c.moveToFirst()) return null;
            User u = new User();
            u.id = id; u.name = s(c, "name"); u.handle = s(c, "handle"); u.phone = s(c, "phone"); u.email = s(c, "email");
            u.bio = s(c, "bio"); u.colorHex = s(c, "color"); u.avatarUrl = s(c, "avatar_url"); u.publicKey = s(c, "public_key");
            u.isBot = l(c, "is_bot") == 1;
            if (!c.isNull(c.getColumnIndexOrThrow("last_seen"))) u.lastSeen = l(c, "last_seen");
            return u;
        }
    }

    public String getPublicKey(long userId) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT public_key FROM users WHERE id=?", new String[]{String.valueOf(userId)})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    public void deleteChatLocal(String chatKey) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery("SELECT media_local_path FROM messages WHERE chat_key=? AND media_local_path IS NOT NULL", new String[]{chatKey})) {
                while (c.moveToNext()) new java.io.File(c.getString(0)).delete();
            }
            db.execSQL("DELETE FROM outbox WHERE chat_key=?", new Object[]{chatKey});
            db.execSQL("DELETE FROM messages WHERE chat_key=?", new Object[]{chatKey});
            db.execSQL("DELETE FROM chats WHERE chat_key=?", new Object[]{chatKey});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        notifyChanged();
    }

    public void seedLegacyChats(List<com.talksy.messenger.data.model.Chat> chats, List<com.talksy.messenger.data.model.Group> groups, boolean archived) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {

            db.execSQL("DELETE FROM chats WHERE chat_key LIKE 'user:%' OR chat_key LIKE 'group:%'");
            if (chats != null) for (com.talksy.messenger.data.model.Chat c : chats) {
                ContentValues v = new ContentValues();
                v.put("chat_key", userKey(c.peerUserId)); v.put("peer_type", "user"); v.put("peer_id", c.peerUserId);
                v.put("name", c.peerName); v.put("handle", c.peerHandle); v.put("color", c.peerColorHex); v.put("avatar_url", c.peerAvatarUrl);
                v.put("is_bot", b(c.peerIsBot)); v.put("last_message", c.lastMessage); v.put("last_message_time", c.lastMessageTime);
                v.put("last_outgoing", b(c.lastOutgoing)); v.put("unread_count", c.unreadCount);
                v.put("pinned", b(c.pinned)); v.put("muted", b(c.muted)); v.put("archived", b(archived));
                v.put("updated_at", now()); v.put("sync_status", SyncStatus.SYNCED);

db.update(
        "chats",
        v,
        "chat_key=?",
        new String[]{v.getAsString("chat_key")}
);

db.insertWithOnConflict(
        "chats",
        null,
        v,
        SQLiteDatabase.CONFLICT_IGNORE
);

            }
            if (groups != null) for (com.talksy.messenger.data.model.Group g : groups) {
                ContentValues v = new ContentValues();
                v.put("chat_key", groupKey(g.id)); v.put("peer_type", "group"); v.put("peer_id", g.id);
                v.put("name", g.name); v.put("handle", g.handle); v.put("color", "#06B6D4"); v.put("avatar_url", g.avatarUrl);
                v.put("my_role", g.myRole); v.put("admin_only", b(g.adminOnlyMode));
                v.put("last_message", g.lastMessage); v.put("last_message_time", g.lastMessageTime); v.put("unread_count", g.unreadCount);
                v.put("pinned", b(g.pinned)); v.put("muted", b(g.muted)); v.put("archived", b(archived));
                v.put("updated_at", now()); v.put("sync_status", SyncStatus.SYNCED);

db.update(
        "chats",
        v,
        "chat_key=?",
        new String[]{v.getAsString("chat_key")}
);

db.insertWithOnConflict(
        "chats",
        null,
        v,
        SQLiteDatabase.CONFLICT_IGNORE
);

            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        notifyChanged();
    }

    public void seedLegacyMessages(String chatKey, List<Message> list) {
        if (list == null || list.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Message m : list) {
                if (m.id <= 0) continue;
                ContentValues v = new ContentValues();
                v.put("server_id", m.id); v.put("chat_key", chatKey); v.put("outgoing", b(m.outgoing)); v.put("text", m.text);
                v.put("time_ms", m.time); v.put("enc_text", m.encText); v.put("enc_key_recipient", m.encKeyRecipient);
                v.put("enc_key_sender", m.encKeySender); v.put("iv", m.iv);
                if (m.media != null) { v.put("media_url", m.media.url); v.put("media_type", m.media.type); v.put("media_filename", m.media.filename); v.put("media_size", m.media.size); v.put("media_duration", m.media.durationMs); v.put("media_waveform", m.media.waveform); }
                if (m.replyToId > 0) v.put("reply_to_id", m.replyToId);
                if (m.storyId > 0) v.put("story_id", m.storyId);
                v.put("edited", b(m.edited)); v.put("deleted", b(m.deletedForEveryone)); v.put("reply_markup", m.replyMarkup);
                v.put("version", 1); v.put("updated_at", now()); v.put("sync_status", SyncStatus.SYNCED);
                db.insertWithOnConflict("messages", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        notifyChanged();
    }

    public void seedLegacyGroupMessages(String chatKey, List<com.talksy.messenger.data.model.GroupMessage> list) {
        if (list == null || list.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (com.talksy.messenger.data.model.GroupMessage m : list) {
                if (m.id <= 0) continue;
                ContentValues v = new ContentValues();
                v.put("server_id", m.id); v.put("chat_key", chatKey); v.put("sender_id", m.senderId); v.put("sender_name", m.senderName);
                v.put("outgoing", b(m.outgoing)); v.put("text", m.text); v.put("time_ms", m.time); v.put("enc_text", m.encText); v.put("iv", m.iv);
                if (m.media != null) { v.put("media_url", m.media.url); v.put("media_type", m.media.type); v.put("media_filename", m.media.filename); v.put("media_size", m.media.size); v.put("media_duration", m.media.durationMs); v.put("media_waveform", m.media.waveform); }
                v.put("deleted", b(m.deletedForEveryone)); v.put("version", 1); v.put("updated_at", now()); v.put("sync_status", SyncStatus.SYNCED);
                v.put("views", m.views); v.put("comments_count", m.commentsCount);
                if (db.insertWithOnConflict("messages", null, v, SQLiteDatabase.CONFLICT_IGNORE) == -1) {

                    db.execSQL("UPDATE messages SET views=?, comments_count=? WHERE server_id=? AND chat_key=?", new Object[]{m.views, m.commentsCount, m.id, chatKey});
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        notifyChanged();
    }

    public int pendingCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM outbox", null)) { return c.moveToFirst() ? c.getInt(0) : 0; }
    }

    private static String s(Cursor c, String col) { int i = c.getColumnIndexOrThrow(col); return c.isNull(i) ? null : c.getString(i); }
    private static long l(Cursor c, String col) { int i = c.getColumnIndexOrThrow(col); return c.isNull(i) ? 0 : c.getLong(i); }

    private Message readMessage(Cursor c) {
        Message m = new Message();
        long localId = l(c, "local_id"), serverId = l(c, "server_id");
        m.id = serverId > 0 ? serverId : -localId;
        String key = s(c, "chat_key");
        m.chatId = key.indexOf(':') > 0 ? Long.parseLong(key.substring(key.indexOf(':') + 1)) : 0;
        m.outgoing = l(c, "outgoing") == 1; m.text = s(c, "text"); m.time = l(c, "time_ms");
        m.encText = s(c, "enc_text"); m.encKeyRecipient = s(c, "enc_key_recipient"); m.encKeySender = s(c, "enc_key_sender"); m.iv = s(c, "iv");
        m.deletedForEveryone = l(c, "deleted") == 1; m.edited = l(c, "edited") == 1;
        m.storyId = l(c, "story_id"); m.replyToId = l(c, "reply_to_id"); m.replyMarkup = s(c, "reply_markup");
        if (s(c, "media_url") != null) {
            m.media = new MediaAttachment();
            m.media.url = s(c, "media_url"); m.media.type = s(c, "media_type");
            m.media.filename = s(c, "media_filename"); m.media.size = l(c, "media_size");
            m.media.durationMs = l(c, "media_duration"); m.media.waveform = s(c, "media_waveform");
        }
        if (s(c, "media_url") == null && s(c, "media_local_path") != null) {
            m.uploading = true; m.uploadName = s(c, "media_filename"); m.uploadTotal = l(c, "media_size"); m.uploadDone = 0;
            m.uploadCancel = new java.util.concurrent.atomic.AtomicBoolean(false);
        }
        String rj = s(c, "reactions_json");
        if (rj != null) {
            try {
                JSONArray a = new JSONArray(rj);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i); Reaction r = new Reaction();
                    r.emoji = o.optString("emoji"); r.count = o.optInt("count"); r.mine = o.optBoolean("mine");
                    m.reactions.add(r);
                }
            } catch (JSONException ignored) {}
        }
        m.group = key.startsWith("g:");
        m.localId = localId; m.clientUuid = s(c, "client_uuid"); m.syncStatus = s(c, "sync_status");
        m.version = l(c, "version"); m.conflictText = s(c, "conflict_text");
        return m;
    }

    public void ensureChat(String chatKey, String name, String color, String avatarUrl, boolean isBot) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        boolean group = chatKey.startsWith("g:");
        v.put("chat_key", chatKey); v.put("peer_type", group ? "group" : "user");
        long peerId = 0;
        try {
            String rawId = chatKey;
            if (chatKey.contains(":")) {
                rawId = chatKey.substring(chatKey.indexOf(":") + 1);
            }
            peerId = Long.parseLong(rawId);
        } catch (Exception ignored) {}

        v.put("peer_id", peerId);
        v.put("name", name); v.put("color", color); v.put("avatar_url", avatarUrl); v.put("is_bot", b(isBot));

db.update(
        "chats",
        v,
        "chat_key=?",
        new String[]{v.getAsString("chat_key")}
);

db.insertWithOnConflict(
        "chats",
        null,
        v,
        SQLiteDatabase.CONFLICT_IGNORE
);

    }

    public Message enqueueSend(String chatKey, String plainText, String encText, String encKeyRecipient, String encKeySender,
                               String iv, long replyToId, MediaAttachment media) {
        SQLiteDatabase db = getWritableDatabase();
        long t = now(); String uuid = UUID.randomUUID().toString(); long localId;
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("client_uuid", uuid); v.put("chat_key", chatKey); v.put("outgoing", 1); v.put("text", plainText);
            v.put("time_ms", t); v.put("enc_text", encText); v.put("enc_key_recipient", encKeyRecipient);
            v.put("enc_key_sender", encKeySender); v.put("iv", iv);
            if (replyToId != 0) v.put("reply_to_id", replyToId);
            if (media != null) { v.put("media_url", media.url); v.put("media_type", media.type); v.put("media_filename", media.filename); v.put("media_size", media.size); }
            v.put("updated_at", t); v.put("version", 0); v.put("sync_status", SyncStatus.PENDING);
            localId = db.insertOrThrow("messages", null, v);

            Map<String, Object> p = new HashMap<>();
            Map<String, Object> chat = new HashMap<>();
            chat.put("type", chatKey.startsWith("g:") ? "group" : "user"); chat.put("id", parseChatId(chatKey));
            p.put("chat", chat); p.put("client_uuid", uuid); p.put("created_at", t);
            if (encText != null) { p.put("enc_text", encText); p.put("enc_key_recipient", encKeyRecipient); p.put("enc_key_sender", encKeySender); p.put("iv", iv); }
            else p.put("text", plainText == null ? "" : plainText);
            if (replyToId > 0) p.put("reply_to_id", replyToId);
            else if (replyToId < 0) p.put("reply_to_local_id", -replyToId);
            if (media != null) { p.put("media_url", media.url); p.put("media_type", media.type); p.put("media_filename", media.filename); p.put("media_size", media.size); }
            insertOp(db, Op.MESSAGE_SEND, chatKey, localId, p);

            String sp = com.talksy.messenger.data.SpecialMessage.previewRu(plainText);
            String preview = sp != null ? sp : (plainText != null && !plainText.isEmpty() ? plainText : (media != null ? "Вложение" : ""));
            db.execSQL("UPDATE chats SET last_message=?, last_message_time=?, last_outgoing=1 WHERE chat_key=?", new Object[]{preview, t, chatKey});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        notifyChanged();
        try (Cursor c = db.rawQuery("SELECT * FROM messages WHERE local_id=?", new String[]{String.valueOf(localId)})) {
            c.moveToFirst(); return readMessage(c);
        }
    }

    public Message addPendingOutgoing(String chatKey, String plainText, String encText, String encKeyRecipient, String encKeySender,
                                      String iv, long replyToId, MediaAttachment media) {
        SQLiteDatabase db = getWritableDatabase();
        long t = now(); long localId;
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("client_uuid", UUID.randomUUID().toString()); v.put("chat_key", chatKey); v.put("outgoing", 1); v.put("text", plainText);
            v.put("time_ms", t); v.put("enc_text", encText); v.put("enc_key_recipient", encKeyRecipient);
            v.put("enc_key_sender", encKeySender); v.put("iv", iv);
            if (replyToId > 0) v.put("reply_to_id", replyToId);
            if (media != null) { v.put("media_url", media.url); v.put("media_type", media.type); v.put("media_filename", media.filename); v.put("media_size", media.size); }
            v.put("updated_at", t); v.put("version", 0); v.put("sync_status", SyncStatus.PENDING);
            localId = db.insertOrThrow("messages", null, v);
            String sp = com.talksy.messenger.data.SpecialMessage.previewRu(plainText);
            String preview = sp != null ? sp : (plainText != null && !plainText.isEmpty() ? plainText : (media != null ? "Вложение" : ""));
            db.execSQL("UPDATE chats SET last_message=?, last_message_time=?, last_outgoing=1 WHERE chat_key=?", new Object[]{preview, t, chatKey});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        notifyChanged();
        try (Cursor c = db.rawQuery("SELECT * FROM messages WHERE local_id=?", new String[]{String.valueOf(localId)})) {
            c.moveToFirst(); return readMessage(c);
        }
    }

    public boolean hasChat(String chatKey) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM chats WHERE chat_key=?", new String[]{chatKey})) { return c.moveToFirst(); }
    }

    public long lastServerTimeMs(String chatKey) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT MAX(time_ms) FROM messages WHERE chat_key=? AND server_id IS NOT NULL", new String[]{chatKey})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public List<Message> getUnsentOutgoing() {
        List<Message> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM messages WHERE outgoing=1 AND server_id IS NULL AND sync_status=? ORDER BY local_id ASC",
                new String[]{SyncStatus.PENDING})) {
            while (c.moveToNext()) out.add(readMessage(c));
        }
        return out;
    }

    public void markFailed(long localId) {
        ContentValues v = new ContentValues();
        v.put("sync_status", SyncStatus.FAILED); v.put("updated_at", now());
        getWritableDatabase().update("messages", v, "local_id=?", new String[]{String.valueOf(localId)});
        notifyChanged();
    }

    public void markDelivered(long localId, long serverId, long serverTimeMs) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            boolean exists;
            try (Cursor c = db.rawQuery("SELECT 1 FROM messages WHERE server_id=?", new String[]{String.valueOf(serverId)})) { exists = c.moveToFirst(); }
            if (exists) {
                db.execSQL("DELETE FROM messages WHERE local_id=?", new Object[]{localId});
            } else {
                ContentValues v = new ContentValues();
                v.put("server_id", serverId); v.put("sync_status", SyncStatus.SYNCED); v.put("updated_at", now());
                if (serverTimeMs > 0) v.put("time_ms", serverTimeMs);
                db.update("messages", v, "local_id=?", new String[]{String.valueOf(localId)});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        notifyChanged();
    }

    public void enqueueEdit(long messageId, String newPlain, String encText, String encKeyRecipient, String encKeySender, String iv) {
        SQLiteDatabase db = getWritableDatabase();
        long localId = localIdOf(db, messageId); if (localId == 0) return;
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("text", newPlain); v.put("enc_text", encText); v.put("enc_key_recipient", encKeyRecipient);
            v.put("enc_key_sender", encKeySender); v.put("iv", iv); v.put("edited", 1); v.put("sync_status", SyncStatus.PENDING);
            db.update("messages", v, "local_id=?", new String[]{String.valueOf(localId)});
            long serverId = serverIdOf(db, localId);
            Map<String, Object> p = new HashMap<>();
            if (encText != null) { p.put("enc_text", encText); p.put("enc_key_recipient", encKeyRecipient); p.put("enc_key_sender", encKeySender); p.put("iv", iv); }
            else p.put("text", newPlain);
            if (serverId == 0) {

                patchPendingSend(db, localId, p);
            } else {
                p.put("message_id", serverId);
                p.put("base_version", versionOf(db, localId));
                db.execSQL("DELETE FROM outbox WHERE type=? AND ref_local_id=? AND state='PENDING'", new Object[]{Op.MESSAGE_EDIT, localId});
                insertOp(db, Op.MESSAGE_EDIT, chatKeyOf(db, localId), localId, p);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        notifyChanged();
    }

    public void enqueueDelete(long messageId, boolean forEveryone) {
        SQLiteDatabase db = getWritableDatabase();
        long localId = localIdOf(db, messageId); if (localId == 0) return;
        db.beginTransaction();
        try {
            String chatKey = chatKeyOf(db, localId); long serverId = serverIdOf(db, localId);
            db.execSQL("DELETE FROM outbox WHERE ref_local_id=? AND type IN (?,?)", new Object[]{localId, Op.MESSAGE_EDIT, Op.MESSAGE_SEND});
            if (serverId == 0) {
                try (Cursor fc = db.rawQuery("SELECT media_local_path FROM messages WHERE local_id=?", new String[]{String.valueOf(localId)})) {
                    if (fc.moveToFirst() && fc.getString(0) != null) new java.io.File(fc.getString(0)).delete();
                }
                db.delete("messages", "local_id=?", new String[]{String.valueOf(localId)});
            } else {
                Map<String, Object> p = new HashMap<>();
                p.put("message_id", serverId); p.put("kind", chatKey.startsWith("g:") ? "group" : "direct"); p.put("for_everyone", forEveryone);
                insertOp(db, Op.MESSAGE_DELETE, chatKey, localId, p);
                if (forEveryone) db.execSQL("UPDATE messages SET deleted=1, text='' WHERE local_id=?", new Object[]{localId});
                else db.delete("messages", "local_id=?", new String[]{String.valueOf(localId)});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        notifyChanged();
    }

    public void enqueueChatState(String chatKey, Map<String, Boolean> fields) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Map<String, Object> cur = new HashMap<>(); Map<String, Object> base = new HashMap<>(); Map<String, Object> fl = new HashMap<>();
            long opSeq = 0;
            try (Cursor c = db.rawQuery("SELECT seq, payload FROM outbox WHERE type=? AND chat_key=? AND state='PENDING' ORDER BY seq DESC LIMIT 1",
                    new String[]{Op.CHAT_STATE, chatKey})) {
                if (c.moveToFirst()) {
                    opSeq = c.getLong(0);
                    Map<String, Object> old = toMap(parse(c.getString(1)));
                    base.putAll(asMap(old.get("base"))); fl.putAll(asMap(old.get("fields")));
                }
            }
            try (Cursor c = db.rawQuery("SELECT pinned, archived, muted FROM chats WHERE chat_key=?", new String[]{chatKey})) {
                if (!c.moveToFirst()) return;
                cur.put("pinned", c.getInt(0) == 1); cur.put("archived", c.getInt(1) == 1); cur.put("muted", c.getInt(2) == 1);
            }
            ContentValues v = new ContentValues();
            for (Map.Entry<String, Boolean> e : fields.entrySet()) {
                if (!base.containsKey(e.getKey())) base.put(e.getKey(), cur.get(e.getKey()));
                fl.put(e.getKey(), e.getValue());
                v.put(e.getKey(), b(e.getValue()));
            }
            v.put("sync_status", SyncStatus.PENDING);
            db.update("chats", v, "chat_key=?", new String[]{chatKey});
            Map<String, Object> p = new HashMap<>();
            p.put("peer_type", chatKey.startsWith("g:") ? "group" : "user"); p.put("peer_id", parseChatId(chatKey));
            p.put("fields", fl); p.put("base", base);
            if (opSeq != 0) db.execSQL("UPDATE outbox SET payload=? WHERE seq=?", new Object[]{toJson(p).toString(), opSeq});
            else insertOp(db, Op.CHAT_STATE, chatKey, 0, p);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        notifyChanged();
    }

    public boolean markChatRead(String chatKey) {
        SQLiteDatabase db = getWritableDatabase();
        boolean changed = false;
        db.beginTransaction();
        try {
            long maxMs = 0, sent = 0, unread = 0;
            try (Cursor c = db.rawQuery("SELECT MAX(time_ms) FROM messages WHERE chat_key=? AND outgoing=0 AND server_id IS NOT NULL", new String[]{chatKey})) {
                if (c.moveToFirst()) maxMs = c.getLong(0);
            }
            try (Cursor c = db.rawQuery("SELECT my_read_sent, unread_count FROM chats WHERE chat_key=?", new String[]{chatKey})) {
                if (c.moveToFirst()) { sent = c.getLong(0); unread = c.getLong(1); }
            }
            if (unread > 0) { db.execSQL("UPDATE chats SET unread_count=0 WHERE chat_key=?", new Object[]{chatKey}); changed = true; }
            if (maxMs > sent) {
                db.execSQL("UPDATE chats SET my_read_sent=? WHERE chat_key=?", new Object[]{maxMs, chatKey});
                db.execSQL("DELETE FROM outbox WHERE type=? AND chat_key=? AND state='PENDING'", new Object[]{Op.CHAT_READ, chatKey});
                Map<String, Object> p = new HashMap<>();
                p.put("kind", chatKey.startsWith("g:") ? "group" : "direct"); p.put("peer_id", parseChatId(chatKey));
                p.put("read_until", maxMs / 1000.0);
                insertOp(db, Op.CHAT_READ, chatKey, 0, p);
                changed = true;
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        if (changed) notifyChanged();
        return changed;
    }

    public void enqueueProfile(Map<String, String> fields) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Map<String, Object> base = new HashMap<>(), fl = new HashMap<>(); long opSeq = 0;
            try (Cursor c = db.rawQuery("SELECT seq, payload FROM outbox WHERE type=? AND state='PENDING' ORDER BY seq DESC LIMIT 1", new String[]{Op.PROFILE_UPDATE})) {
                if (c.moveToFirst()) { opSeq = c.getLong(0); Map<String, Object> old = toMap(parse(c.getString(1))); base.putAll(asMap(old.get("base"))); fl.putAll(asMap(old.get("fields"))); }
            }
            ContentValues v = new ContentValues();
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (!base.containsKey(e.getKey())) {
                    try (Cursor c = db.rawQuery("SELECT " + safeCol(e.getKey()) + " FROM users WHERE is_me=1", null)) { base.put(e.getKey(), c.moveToFirst() && c.getString(0) != null ? c.getString(0) : ""); }
                }
                fl.put(e.getKey(), e.getValue()); v.put(safeCol(e.getKey()), e.getValue());
            }
            v.put("sync_status", SyncStatus.PENDING);
            db.update("users", v, "is_me=1", null);
            Map<String, Object> p = new HashMap<>(); p.put("fields", fl); p.put("base", base);
            if (opSeq != 0) db.execSQL("UPDATE outbox SET payload=? WHERE seq=?", new Object[]{toJson(p).toString(), opSeq});
            else insertOp(db, Op.PROFILE_UPDATE, null, 0, p);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        notifyChanged();
    }

    private static String safeCol(String k) {
        switch (k) { case "name": case "handle": case "bio": case "color": return k; default: throw new IllegalArgumentException(k); }
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> asMap(Object o) { return o instanceof Map ? (Map<String, Object>) o : new HashMap<>(); }
    private static JSONObject parse(String s) { try { return new JSONObject(s); } catch (JSONException e) { return new JSONObject(); } }

    private void insertOp(SQLiteDatabase db, String type, String chatKey, long refLocalId, Map<String, Object> payload) {
        ContentValues v = new ContentValues();
        v.put("op_id", UUID.randomUUID().toString()); v.put("type", type); v.put("chat_key", chatKey);
        v.put("ref_local_id", refLocalId); v.put("payload", toJson(payload).toString()); v.put("created_at", now());
        db.insertOrThrow("outbox", null, v);
    }
    private void patchPendingSend(SQLiteDatabase db, long localId, Map<String, Object> patch) {
        try (Cursor c = db.rawQuery("SELECT seq, payload FROM outbox WHERE type=? AND ref_local_id=?", new String[]{Op.MESSAGE_SEND, String.valueOf(localId)})) {
            if (!c.moveToFirst()) return;
            Map<String, Object> p = toMap(parse(c.getString(1)));
            p.remove("text"); p.remove("enc_text"); p.putAll(patch);
            db.execSQL("UPDATE outbox SET payload=? WHERE seq=?", new Object[]{toJson(p).toString(), c.getLong(0)});
        }
    }
    private long localIdOf(SQLiteDatabase db, long messageId) {
        if (messageId < 0) return -messageId;
        try (Cursor c = db.rawQuery("SELECT local_id FROM messages WHERE server_id=?", new String[]{String.valueOf(messageId)})) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }
    private long serverIdOf(SQLiteDatabase db, long localId) {
        try (Cursor c = db.rawQuery("SELECT server_id FROM messages WHERE local_id=?", new String[]{String.valueOf(localId)})) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }
    private long versionOf(SQLiteDatabase db, long localId) {
        try (Cursor c = db.rawQuery("SELECT version FROM messages WHERE local_id=?", new String[]{String.valueOf(localId)})) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }
    private String chatKeyOf(SQLiteDatabase db, long localId) {
        try (Cursor c = db.rawQuery("SELECT chat_key FROM messages WHERE local_id=?", new String[]{String.valueOf(localId)})) { return c.moveToFirst() ? c.getString(0) : null; }
    }

    public Message enqueueSendFile(String chatKey, String localPath, String filename, String mime, long size, long replyToId) {
        SQLiteDatabase db = getWritableDatabase();
        long t = now(); String uuid = UUID.randomUUID().toString(); long localId;
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("client_uuid", uuid); v.put("chat_key", chatKey); v.put("outgoing", 1); v.put("text", "");
            v.put("time_ms", t); v.put("media_filename", filename); v.put("media_size", size); v.put("media_local_path", localPath);
            if (replyToId != 0) v.put("reply_to_id", replyToId);
            v.put("updated_at", t); v.put("version", 0); v.put("sync_status", SyncStatus.PENDING);
            localId = db.insertOrThrow("messages", null, v);
            Map<String, Object> p = new HashMap<>();
            Map<String, Object> chat = new HashMap<>();
            chat.put("type", chatKey.startsWith("g:") ? "group" : "user"); chat.put("id", parseChatId(chatKey));
            p.put("chat", chat); p.put("client_uuid", uuid); p.put("created_at", t); p.put("text", "");
            p.put("local_media_path", localPath); p.put("media_filename", filename); p.put("media_mime", mime); p.put("media_size", size);
            if (replyToId > 0) p.put("reply_to_id", replyToId);
            insertOp(db, Op.MESSAGE_SEND, chatKey, localId, p);
            db.execSQL("UPDATE chats SET last_message=?, last_message_time=?, last_outgoing=1 WHERE chat_key=?", new Object[]{filename, t, chatKey});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        notifyChanged();
        try (Cursor c = db.rawQuery("SELECT * FROM messages WHERE local_id=?", new String[]{String.valueOf(localId)})) { c.moveToFirst(); return readMessage(c); }
    }

    public void savePayload(Op op) {
        getWritableDatabase().execSQL("UPDATE outbox SET payload=? WHERE op_id=?", new Object[]{toJson(op.payload).toString(), op.opId});
    }

    public List<Reaction> enqueueReaction(long messageId, String emoji) {
        SQLiteDatabase db = getWritableDatabase();
        long localId = localIdOf(db, messageId); if (localId == 0) return null;
        if (serverIdOf(db, localId) == 0) return null;
        List<Reaction> list = new ArrayList<>();
        String chatKey = chatKeyOf(db, localId);
        try (Cursor c = db.rawQuery("SELECT reactions_json FROM messages WHERE local_id=?", new String[]{String.valueOf(localId)})) {
            if (c.moveToFirst() && c.getString(0) != null) {
                try {
                    JSONArray a = new JSONArray(c.getString(0));
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject o = a.getJSONObject(i); Reaction r = new Reaction();
                        r.emoji = o.optString("emoji"); r.count = o.optInt("count"); r.mine = o.optBoolean("mine"); list.add(r);
                    }
                } catch (JSONException ignored) {}
            }
        }
        Reaction mine = null; for (Reaction r : list) if (r.mine) mine = r;
        boolean on = !(mine != null && emoji.equals(mine.emoji));
        if (mine != null) { mine.count--; mine.mine = false; if (mine.count <= 0) list.remove(mine); }
        if (on) {
            Reaction target = null; for (Reaction r : list) if (emoji.equals(r.emoji)) target = r;
            if (target == null) { target = new Reaction(); target.emoji = emoji; target.count = 0; list.add(target); }
            target.count++; target.mine = true;
        }
        JSONArray out = new JSONArray();
        try { for (Reaction r : list) out.put(new JSONObject().put("emoji", r.emoji).put("count", r.count).put("mine", r.mine)); } catch (JSONException ignored) {}
        db.beginTransaction();
        try {
            db.execSQL("UPDATE messages SET reactions_json=? WHERE local_id=?", new Object[]{out.toString(), localId});
            Map<String, Object> p = new HashMap<>();
            p.put("kind", chatKey.startsWith("g:") ? "group" : "direct"); p.put("message_id", serverIdOf(db, localId)); p.put("emoji", emoji); p.put("on", on);
            insertOp(db, "reaction.set", chatKey, localId, p);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        notifyChanged();
        return list;
    }

    public List<com.talksy.messenger.data.model.GroupMessage> getGroupMessages(String chatKey) {
        List<com.talksy.messenger.data.model.GroupMessage> out = new ArrayList<>();
        for (Message m : getMessages(chatKey)) {
            com.talksy.messenger.data.model.GroupMessage g = new com.talksy.messenger.data.model.GroupMessage();
            g.id = m.id; g.outgoing = m.outgoing; g.text = m.text; g.time = m.time; g.reactions = m.reactions; g.media = m.media;
            g.encText = m.encText; g.iv = m.iv; g.deletedForEveryone = m.deletedForEveryone;
            g.localId = m.localId; g.syncStatus = m.syncStatus; g.version = m.version; g.uploading = m.uploading;
            out.add(g);
        }

        try (Cursor c = getReadableDatabase().rawQuery("SELECT local_id, sender_id, sender_name, views, comments_count FROM messages WHERE chat_key=? ORDER BY time_ms ASC, local_id ASC", new String[]{chatKey})) {
            int i = 0;
            while (c.moveToNext() && i < out.size()) {
                out.get(i).senderId = c.getLong(1); out.get(i).senderName = c.getString(2);
                out.get(i).views = c.getLong(3); out.get(i).commentsCount = c.getLong(4); i++;
            }
        }
        return out;
    }

    public Map<String, Object> getGroupMeta(long groupId) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT extra_json FROM chats WHERE chat_key=?", new String[]{groupKey(groupId)})) {
            return c.moveToFirst() && c.getString(0) != null ? toMap(parse(c.getString(0))) : null;
        }
    }

    public String kvGet(String k) { return getState(k, null); }
    public void kvPut(String k, String v) { setState(getWritableDatabase(), k, v); }

    public void cachePut(String key, String body) {
        if (body == null || body.length() > 300_000) return;
        try { getWritableDatabase().execSQL("INSERT OR REPLACE INTO http_cache(k, body, ts) VALUES (?,?,?)", new Object[]{key, body, now()}); } catch (Exception ignored) {}
    }
    public JSONObject cacheGet(String key) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT body FROM http_cache WHERE k=?", new String[]{key})) {
            if (c.moveToFirst()) return new JSONObject(c.getString(0));
        } catch (Exception ignored) {}
        return null;
    }

    public List<ChatListItem> searchLocal(String q) {
        List<ChatListItem> out = new ArrayList<>(); String like = "%" + q.toLowerCase().replace("%", "") + "%";
        Set<String> seen = new HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM chats WHERE lower(name) LIKE ? OR lower(handle) LIKE ? ORDER BY last_message_time DESC LIMIT 50", new String[]{like, like})) {
            while (c.moveToNext()) {
                ChatListItem it = new ChatListItem(); boolean g = "group".equals(s(c, "peer_type"));
                it.id = l(c, "peer_id"); it.isGroup = g; it.name = s(c, "name"); it.handle = s(c, "handle");
                it.colorHex = g ? "#06B6D4" : s(c, "color"); it.avatarUrl = s(c, "avatar_url"); it.myRole = g ? s(c, "my_role") : null;
                out.add(it); seen.add(s(c, "chat_key"));
            }
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM users WHERE is_me=0 AND (lower(name) LIKE ? OR lower(handle) LIKE ?) LIMIT 50", new String[]{like, like})) {
            while (c.moveToNext()) {
                if (seen.contains(userKey(l(c, "id")))) continue;
                ChatListItem it = new ChatListItem(); it.id = l(c, "id"); it.name = s(c, "name"); it.handle = s(c, "handle");
                it.colorHex = s(c, "color"); it.avatarUrl = s(c, "avatar_url"); out.add(it);
            }
        }
        return out;
    }

    @Override public void resetSending() {
        getWritableDatabase().execSQL("UPDATE outbox SET state='PENDING' WHERE state='SENDING'");
    }

    @Override public List<Op> claimDueOps(int limit, long nowMs) {
        SQLiteDatabase db = getWritableDatabase();
        List<Op> out = new ArrayList<>();
        Set<String> blocked = new HashSet<>();
        Set<String> sendOnly = new HashSet<>();
        db.beginTransaction();
        try (Cursor c = db.rawQuery("SELECT * FROM outbox ORDER BY seq ASC LIMIT 500", null)) {
            while (c.moveToNext() && out.size() < limit) {
                String chat = s(c, "chat_key"); String ck = chat == null ? "_" + s(c, "type") : chat;
                String type = s(c, "type"); boolean due = l(c, "next_retry_at") <= nowMs && "PENDING".equals(s(c, "state"));
                if (!due || blocked.contains(ck)) { blocked.add(ck); continue; }
                if (!Op.MESSAGE_SEND.equals(type) && sendOnly.contains(ck)) { blocked.add(ck); continue; }
                Op op = new Op();
                op.seq = l(c, "seq"); op.opId = s(c, "op_id"); op.type = type; op.chatKey = chat;
                op.attempts = (int) l(c, "attempts"); op.nextRetryAt = l(c, "next_retry_at");
                op.payload = toMap(parse(s(c, "payload")));
                if (Op.MESSAGE_SEND.equals(type)) { resolveReply(db, op); sendOnly.add(ck); } else blocked.add(ck);
                out.add(op);
            }
        }
        for (Op op : out) db.execSQL("UPDATE outbox SET state='SENDING' WHERE op_id=?", new Object[]{op.opId});
        for (Op op : out) if (Op.MESSAGE_SEND.equals(op.type)) markMessageStatus(db, op, SyncStatus.SENDING);
        db.setTransactionSuccessful();
        db.endTransaction();
        return out;
    }

    private void resolveReply(SQLiteDatabase db, Op op) {
        Object local = op.payload.remove("reply_to_local_id");
        if (local == null) return;
        try (Cursor c = db.rawQuery("SELECT server_id, client_uuid FROM messages WHERE local_id=?", new String[]{String.valueOf(((Number) local).longValue())})) {
            if (!c.moveToFirst()) return;
            if (!c.isNull(0) && c.getLong(0) > 0) op.payload.put("reply_to_id", c.getLong(0));
            else op.payload.put("reply_to_client_uuid", c.getString(1));
        }
    }
    private void markMessageStatus(SQLiteDatabase db, Op op, String status) {
        Object cu = op.payload.get("client_uuid");
        if (cu != null) db.execSQL("UPDATE messages SET sync_status=? WHERE client_uuid=? AND sync_status IN ('PENDING','SENDING')", new Object[]{status, String.valueOf(cu)});
    }

    @Override public void applyResult(Op op, OpResult res) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM outbox WHERE op_id=?", new Object[]{op.opId});
            boolean ok = OpResult.OK.equals(res.status), conflict = OpResult.CONFLICT.equals(res.status);
            switch (op.type) {
                case Op.MESSAGE_SEND: {
                    String cu = String.valueOf(op.payload.get("client_uuid"));
                    if (ok && res.entity != null) {
                        ContentValues v = new ContentValues();
                        v.put("server_id", lng(res.entity, "id")); v.put("time_ms", Math.round(dbl(res.entity, "time") * 1000));
                        v.put("version", lng(res.entity, "version")); v.put("updated_at", Math.round(dbl(res.entity, "updated_at") * 1000));
                        v.put("sync_status", SyncStatus.SYNCED); v.putNull("last_error");
                        if (str(res.entity, "media_url") != null) {
                            v.put("media_url", str(res.entity, "media_url")); v.put("media_type", str(res.entity, "media_type"));
                            v.put("media_filename", str(res.entity, "media_filename")); v.put("media_size", lng(res.entity, "media_size"));
                            v.putNull("media_local_path");
                            Object lp = op.payload.get("local_media_path");
                            if (lp != null) new java.io.File(String.valueOf(lp)).delete();
                        }

                        db.update("messages", v, "client_uuid=?", new String[]{cu});
                    } else {
                        ContentValues v = new ContentValues();
                        v.put("sync_status", SyncStatus.FAILED); v.put("last_error", res.error);
                        db.update("messages", v, "client_uuid=?", new String[]{cu});
                    }
                    break;
                }
                case Op.MESSAGE_EDIT: {
                    long mid = lng(op.payload, "message_id");
                    if (res.entity != null) {
                        String localText = null;
                        try (Cursor c = db.rawQuery("SELECT text FROM messages WHERE server_id=?", new String[]{String.valueOf(mid)})) { if (c.moveToFirst()) localText = c.getString(0); }
                        if (conflict) {
                            String keep = ConflictResolver.conflictTextToKeep(localText, str(res.entity, "text"), res.reason);
                            applyServerMessage(db, res.entity, true);
                            ContentValues v = new ContentValues();
                            v.put("conflict_text", keep); v.put("sync_status", keep != null ? SyncStatus.CONFLICT : SyncStatus.SYNCED);
                            db.update("messages", v, "server_id=?", new String[]{String.valueOf(mid)});
                        } else {
                            ContentValues v = new ContentValues();
                            v.put("version", lng(res.entity, "version")); v.put("sync_status", SyncStatus.SYNCED);
                            db.update("messages", v, "server_id=?", new String[]{String.valueOf(mid)});
                        }
                    }
                    break;
                }
                case Op.CHAT_STATE: {
                    if (res.entity != null) {
                        String key = str(res.entity, "chat_key");
                        Map<String, Object> merged = ConflictResolver.mergeFields(res.entity, pendingStateFields(db, key));
                        ContentValues v = new ContentValues();
                        for (String f : new String[]{"pinned", "archived", "muted"}) if (merged.containsKey(f)) v.put(f, b(bool(merged, f)));
                        v.put("sync_status", hasPendingState(db, key) ? SyncStatus.PENDING : SyncStatus.SYNCED);
                        db.update("chats", v, "chat_key=?", new String[]{key});
                    }
                    break;
                }
                case Op.PROFILE_UPDATE: {
                    if (res.entity != null) { res.entity.put("is_me", true); upsertUser(db, res.entity, false); }
                    break;
                }
                default: break;
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        notifyChanged();
    }

    @Override public void markTransientFailure(List<Op> ops, long nowMs) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Op op : ops) {
                int attempts = op.attempts + 1;
                db.execSQL("UPDATE outbox SET state='PENDING', attempts=?, next_retry_at=? WHERE op_id=?",
                        new Object[]{attempts, nowMs + ConflictResolver.backoffMs(attempts), op.opId});
                if (Op.MESSAGE_SEND.equals(op.type)) markMessageStatus(db, op, SyncStatus.PENDING);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    @Override public long getCursor() { return Long.parseLong(getState("cursor", "-1")); }
    @Override public boolean hasPendingOps() { return pendingCount() > 0; }
    @Override public long nextRetryAt() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT MIN(next_retry_at) FROM outbox WHERE next_retry_at>0", null)) { return c.moveToFirst() ? c.getLong(0) : 0; }
    }

    public static class Incoming { public String chatKey; public String text, encText, encKeyRecipient, iv; public long serverId; }
    public final List<Incoming> lastIncoming = new ArrayList<>();

    @Override public void applyPull(PullResult r) {
        SQLiteDatabase db = getWritableDatabase();
        lastIncoming.clear();
        List<Incoming> incoming = new ArrayList<>();
        db.beginTransaction();
        try {
            if (r.fullResync) {
                db.execSQL("DELETE FROM chats WHERE sync_status='SYNCED'");
                db.execSQL("DELETE FROM messages WHERE sync_status='SYNCED'");
            }
            for (Change ch : r.changes) {
                switch (ch.entity) {
                    case "message":
                        if ("delete".equals(ch.op)) db.execSQL("DELETE FROM messages WHERE server_id=?", new Object[]{lng(ch.data, "id")});
                        else applyServerMessageIncoming(db, ch.data, r.fullResync ? null : incoming);
                        break;
                    case "chat":
                        if ("delete".equals(ch.op)) db.execSQL("DELETE FROM chats WHERE chat_key=?", new Object[]{str(ch.data, "chat_key")});
                        else upsertChat(db, ch.data);
                        break;
                    case "user":
                        upsertUser(db, ch.data, true);
                        break;
                    default: break;
                }
            }
            setState(db, "cursor", String.valueOf(r.cursor));
            setState(db, "last_sync_at", String.valueOf(now()));
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        lastIncoming.addAll(incoming);
        notifyChanged();
    }

    private static final String[] MSG_COLS = {"chat_key", "sender_id", "outgoing", "text", "time_ms", "media_url", "media_type", "media_filename",
            "media_size", "enc_text", "enc_key_recipient", "enc_key_sender", "iv", "reply_to_id", "story_id", "edited", "deleted",
            "reactions_json", "reply_markup", "updated_at", "version", "sync_status"};

    private ContentValues messageValues(Map<String, Object> d) {
        ContentValues v = new ContentValues();
        v.put("server_id", lng(d, "id")); v.put("client_uuid", str(d, "client_uuid"));
        v.put("chat_key", str(d, "chat_key")); v.put("sender_id", lng(d, "sender_id")); v.put("outgoing", b(bool(d, "outgoing")));
        v.put("text", str(d, "text")); v.put("time_ms", Math.round(dbl(d, "time") * 1000));
        v.put("media_url", str(d, "media_url")); v.put("media_type", str(d, "media_type"));
        v.put("media_filename", str(d, "media_filename")); v.put("media_size", lng(d, "media_size"));
        v.put("enc_text", str(d, "enc_text")); v.put("enc_key_recipient", str(d, "enc_key_recipient"));
        v.put("enc_key_sender", str(d, "enc_key_sender")); v.put("iv", str(d, "iv"));
        v.put("reply_to_id", lng(d, "reply_to_id")); v.put("story_id", lng(d, "story_id"));
        v.put("edited", b(bool(d, "edited"))); v.put("deleted", b(bool(d, "deleted_for_everyone")));
        Object rx = d.get("reactions");
        v.put("reactions_json", rx instanceof List ? ((JSONArray) wrap(rx)).toString() : null);
        v.put("reply_markup", str(d, "reply_markup")); v.put("sender_name", str(d, "sender_name"));
        v.put("updated_at", Math.round(dbl(d, "updated_at") * 1000)); v.put("version", lng(d, "version"));
        v.put("sync_status", SyncStatus.SYNCED);
        return v;
    }

    private void applyServerMessage(SQLiteDatabase db, Map<String, Object> d, boolean force) { applyServerMessageInternal(db, d, force, null); }
    private void applyServerMessageIncoming(SQLiteDatabase db, Map<String, Object> d, List<Incoming> incoming) { applyServerMessageInternal(db, d, false, incoming); }

    private void applyServerMessageInternal(SQLiteDatabase db, Map<String, Object> d, boolean force, List<Incoming> incoming) {
        long sid = lng(d, "id"); String cu = str(d, "client_uuid");
        long localId = 0, localVersion = 0; String status = null, localText = null;
        try (Cursor c = db.rawQuery("SELECT local_id, version, sync_status, text FROM messages WHERE server_id=?", new String[]{String.valueOf(sid)})) {
            if (c.moveToFirst()) { localId = c.getLong(0); localVersion = c.getLong(1); status = c.getString(2); localText = c.getString(3); }
        }
        if (localId == 0 && cu != null) {
            try (Cursor c = db.rawQuery("SELECT local_id, version, sync_status, text FROM messages WHERE client_uuid=?", new String[]{cu})) {
                if (c.moveToFirst()) { localId = c.getLong(0); localVersion = c.getLong(1); status = c.getString(2); localText = c.getString(3); }
            }
        }
        ContentValues v = messageValues(d);
        if (localId == 0) {
            db.insertWithOnConflict("messages", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            if (incoming != null && !bool(d, "outgoing") && !bool(d, "deleted_for_everyone")) {
                Incoming in = new Incoming(); in.chatKey = str(d, "chat_key"); in.text = str(d, "text"); in.encText = str(d, "enc_text");
                in.encKeyRecipient = str(d, "enc_key_recipient"); in.iv = str(d, "iv"); in.serverId = sid; incoming.add(in);
            }
            return;
        }
        boolean hasPendingEdit = false;
        try (Cursor c = db.rawQuery("SELECT 1 FROM outbox WHERE type=? AND ref_local_id=?", new String[]{Op.MESSAGE_EDIT, String.valueOf(localId)})) { hasPendingEdit = c.moveToFirst(); }
        ConflictResolver.Incoming act = force ? ConflictResolver.Incoming.APPLY_ALL :
                ConflictResolver.onIncomingMessage(localVersion, status, hasPendingEdit, lng(d, "version"), bool(d, "deleted_for_everyone"));
        switch (act) {
            case SKIP_STALE: return;
            case DELETE_AND_DROP_PENDING:
                db.execSQL("DELETE FROM outbox WHERE ref_local_id=? AND type=?", new Object[]{localId, Op.MESSAGE_EDIT});
                break;
            case APPLY_KEEP_LOCAL_TEXT:
                v.remove("text"); v.remove("enc_text"); v.remove("enc_key_recipient"); v.remove("enc_key_sender"); v.remove("iv"); v.remove("edited");
                v.put("sync_status", SyncStatus.PENDING); v.remove("version");
                break;
            default: break;
        }

        if (v.containsKey("text") && (v.getAsString("text") == null || v.getAsString("text").isEmpty())
                && v.getAsString("enc_text") != null && localText != null && !localText.isEmpty() && !bool(d, "deleted_for_everyone")) v.put("text", localText);
        if (cu == null) v.remove("client_uuid");
        if (v.containsKey("client_uuid") && v.getAsString("client_uuid") == null) v.remove("client_uuid");
        db.update("messages", v, "local_id=?", new String[]{String.valueOf(localId)});
    }

    private Map<String, Object> pendingStateFields(SQLiteDatabase db, String chatKey) {
        Map<String, Object> out = new HashMap<>();
        try (Cursor c = db.rawQuery("SELECT payload FROM outbox WHERE type=? AND chat_key=? ORDER BY seq", new String[]{Op.CHAT_STATE, chatKey})) {
            while (c.moveToNext()) out.putAll(asMap(toMap(parse(c.getString(0))).get("fields")));
        }
        return out;
    }
    private boolean hasPendingState(SQLiteDatabase db, String chatKey) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM outbox WHERE type=? AND chat_key=?", new String[]{Op.CHAT_STATE, chatKey})) { return c.moveToFirst(); }
    }

    private void upsertChat(SQLiteDatabase db, Map<String, Object> d) {
        String key = str(d, "chat_key");
        Map<String, Object> state = new HashMap<>();
        state.put("pinned", bool(d, "pinned")); state.put("archived", bool(d, "archived")); state.put("muted", bool(d, "muted"));
        Map<String, Object> merged = ConflictResolver.mergeFields(state, pendingStateFields(db, key));
        boolean group = "group".equals(str(d, "peer_type"));
        ContentValues v = new ContentValues();
        v.put("chat_key", key); v.put("peer_type", str(d, "peer_type")); v.put("peer_id", lng(d, "peer_id"));
        v.put("name", str(d, "name")); v.put("handle", str(d, "handle")); v.put("avatar_url", str(d, "avatar_url"));
        v.put("color", group ? "#06B6D4" : str(d, "color")); v.put("is_bot", b(bool(d, "is_bot")));
        v.put("my_role", str(d, "my_role")); v.put("admin_only", b(bool(d, "admin_only_mode")));
        v.put("last_message", str(d, "last_message")); v.put("last_message_time", Math.round(dbl(d, "last_message_time") * 1000));
        v.put("last_outgoing", b(bool(d, "last_outgoing"))); v.put("unread_count", lng(d, "unread_count"));
        v.put("pinned", b(bool(merged, "pinned"))); v.put("archived", b(bool(merged, "archived"))); v.put("muted", b(bool(merged, "muted")));
        v.put("peer_last_read", Math.round(dbl(d, "peer_last_read") * 1000));
        v.put("extra_json", toJson(d).toString());
        v.put("version", lng(d, "seq")); v.put("updated_at", now());
        v.put("sync_status", hasPendingState(db, key) ? SyncStatus.PENDING : SyncStatus.SYNCED);
        int n = db.update("chats", v, "chat_key=?", new String[]{key});
        if (n == 0) db.insert("chats", null, v);

        try (Cursor c = db.rawQuery("SELECT text, time_ms FROM messages WHERE chat_key=? AND sync_status IN ('PENDING','SENDING') ORDER BY time_ms DESC LIMIT 1", new String[]{key})) {
            if (c.moveToFirst() && c.getLong(1) > Math.round(dbl(d, "last_message_time") * 1000))
                db.execSQL("UPDATE chats SET last_message=?, last_message_time=?, last_outgoing=1 WHERE chat_key=?", new Object[]{c.getString(0), c.getLong(1), key});
        }
    }

    private void upsertUser(SQLiteDatabase db, Map<String, Object> d, boolean respectPending) {
        long id = lng(d, "id"); boolean me = bool(d, "is_me");
        Map<String, Object> f = new HashMap<>();
        f.put("name", str(d, "name")); f.put("handle", str(d, "handle")); f.put("bio", str(d, "bio")); f.put("color", str(d, "color"));
        boolean pending = false;
        if (me && respectPending) {
            try (Cursor c = db.rawQuery("SELECT payload FROM outbox WHERE type=? ORDER BY seq", new String[]{Op.PROFILE_UPDATE})) {
                while (c.moveToNext()) { pending = true; f = ConflictResolver.mergeFields(f, asMap(toMap(parse(c.getString(0))).get("fields"))); }
            }
        }
        ContentValues v = new ContentValues();
        v.put("id", id); v.put("name", String.valueOf(f.get("name"))); v.put("handle", (String) f.get("handle")); v.put("bio", (String) f.get("bio"));
        v.put("color", (String) f.get("color")); v.put("phone", str(d, "phone")); v.put("email", str(d, "email"));
        v.put("avatar_url", str(d, "avatar_url")); v.put("is_bot", b(bool(d, "is_bot"))); v.put("public_key", str(d, "public_key"));
        if (d.get("last_seen") instanceof Number) v.put("last_seen", Math.round(dbl(d, "last_seen") * 1000));
        v.put("is_me", b(me)); v.put("version", lng(d, "version")); v.put("updated_at", now());
        v.put("sync_status", pending ? SyncStatus.PENDING : SyncStatus.SYNCED);
        int n = db.update("users", v, "id=?", new String[]{String.valueOf(id)});
        if (n == 0) db.insert("users", null, v);

        if (!me) db.execSQL("UPDATE chats SET name=?, avatar_url=?, color=? WHERE chat_key=?", new Object[]{str(d, "name"), str(d, "avatar_url"), str(d, "color"), userKey(id)});
    }

    private long parseChatId(String chatKey) {
        try {
            if (chatKey == null) return 0;
            int idx = chatKey.indexOf(":");
            if (idx >= 0) {
                return Long.parseLong(chatKey.substring(idx + 1));
            }
            return Long.parseLong(chatKey);
        } catch (Exception e) {
            return 0;
        }
    }

}
