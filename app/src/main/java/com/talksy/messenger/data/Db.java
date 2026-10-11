package com.talksy.messenger.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.talksy.messenger.data.model.Chat;
import com.talksy.messenger.data.model.Message;
import com.talksy.messenger.data.model.User;

import java.util.ArrayList;
import java.util.List;

public class Db extends SQLiteOpenHelper {

    private static final String NAME = "talksy.db";
    private static final int VERSION = 1;
    private static Db instance;

    public static synchronized Db get(Context ctx) {
        if (instance == null) instance = new Db(ctx.getApplicationContext());
        return instance;
    }

    private Db(Context ctx) { super(ctx, NAME, null, VERSION); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT, handle TEXT, phone TEXT, email TEXT, bio TEXT, color TEXT)");

        db.execSQL("CREATE TABLE chats (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "peer_user_id INTEGER, peer_name TEXT, peer_handle TEXT, peer_color TEXT," +
                "last_message TEXT, last_message_time INTEGER)");

        db.execSQL("CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "chat_id INTEGER, outgoing INTEGER, text TEXT, time INTEGER)");

        seedDemoUsers(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS users");
        db.execSQL("DROP TABLE IF EXISTS chats");
        db.execSQL("DROP TABLE IF EXISTS messages");
        onCreate(db);
    }

    private void seedDemoUsers(SQLiteDatabase db) {
        String[][] demo = {
                {"Артём Волков", "@artem", "#6366F1"},
                {"Мила Соколова", "@mila", "#8B5CF6"},
                {"Talksy Bot", "@talksybot", "#06B6D4"}
        };
        for (String[] u : demo) {
            ContentValues cv = new ContentValues();
            cv.put("name", u[0]);
            cv.put("handle", u[1]);
            cv.put("color", u[2]);
            cv.put("phone", "");
            cv.put("email", "");
            cv.put("bio", "");
            db.insert("users", null, cv);
        }
    }

    public long saveMe(User u) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", u.name);
        cv.put("handle", u.handle);
        cv.put("phone", u.phone);
        cv.put("email", u.email);
        cv.put("bio", u.bio);
        cv.put("color", u.colorHex);
        if (u.id > 0) {
            db.update("users", cv, "id=?", new String[]{String.valueOf(u.id)});
            return u.id;
        } else {
            return db.insert("users", null, cv);
        }
    }

    public User getUser(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("users", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        User u = null;
        if (c.moveToFirst()) u = userFromCursor(c);
        c.close();
        return u;
    }

    public List<User> searchUsers(String query) {
        SQLiteDatabase db = getReadableDatabase();
        List<User> out = new ArrayList<>();
        String like = "%" + query + "%";
        Cursor c = db.query("users", null, "name LIKE ? OR handle LIKE ?",
                new String[]{like, like}, null, null, "name ASC");
        while (c.moveToNext()) out.add(userFromCursor(c));
        c.close();
        return out;
    }

    private User userFromCursor(Cursor c) {
        User u = new User();
        u.id = c.getLong(c.getColumnIndexOrThrow("id"));
        u.name = c.getString(c.getColumnIndexOrThrow("name"));
        u.handle = c.getString(c.getColumnIndexOrThrow("handle"));
        u.phone = c.getString(c.getColumnIndexOrThrow("phone"));
        u.email = c.getString(c.getColumnIndexOrThrow("email"));
        u.bio = c.getString(c.getColumnIndexOrThrow("bio"));
        u.colorHex = c.getString(c.getColumnIndexOrThrow("color"));
        return u;
    }

    public long ensureChatWith(User peer) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.query("chats", new String[]{"id"}, "peer_user_id=?",
                new String[]{String.valueOf(peer.id)}, null, null, null);
        if (c.moveToFirst()) {
            long id = c.getLong(0);
            c.close();
            return id;
        }
        c.close();
        ContentValues cv = new ContentValues();
        cv.put("peer_user_id", peer.id);
        cv.put("peer_name", peer.name);
        cv.put("peer_handle", peer.handle);
        cv.put("peer_color", peer.colorHex);
        cv.put("last_message", "");
        cv.put("last_message_time", System.currentTimeMillis());
        return db.insert("chats", null, cv);
    }

    public List<Chat> getChats() {
        SQLiteDatabase db = getReadableDatabase();
        List<Chat> out = new ArrayList<>();
        Cursor c = db.query("chats", null, null, null, null, null, "last_message_time DESC");
        while (c.moveToNext()) {
            Chat ch = new Chat();
            ch.id = c.getLong(c.getColumnIndexOrThrow("id"));
            ch.peerUserId = c.getLong(c.getColumnIndexOrThrow("peer_user_id"));
            ch.peerName = c.getString(c.getColumnIndexOrThrow("peer_name"));
            ch.peerHandle = c.getString(c.getColumnIndexOrThrow("peer_handle"));
            ch.peerColorHex = c.getString(c.getColumnIndexOrThrow("peer_color"));
            ch.lastMessage = c.getString(c.getColumnIndexOrThrow("last_message"));
            ch.lastMessageTime = c.getLong(c.getColumnIndexOrThrow("last_message_time"));
            out.add(ch);
        }
        c.close();
        return out;
    }

    private void touchChat(long chatId, String lastMessage, long time) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("last_message", lastMessage);
        cv.put("last_message_time", time);
        db.update("chats", cv, "id=?", new String[]{String.valueOf(chatId)});
    }

    public long addMessage(Message m) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("chat_id", m.chatId);
        cv.put("outgoing", m.outgoing ? 1 : 0);
        cv.put("text", m.text);
        cv.put("time", m.time);
        long id = db.insert("messages", null, cv);
        touchChat(m.chatId, m.text, m.time);
        return id;
    }

    public List<Message> getMessages(long chatId) {
        SQLiteDatabase db = getReadableDatabase();
        List<Message> out = new ArrayList<>();
        Cursor c = db.query("messages", null, "chat_id=?", new String[]{String.valueOf(chatId)},
                null, null, "time ASC");
        while (c.moveToNext()) {
            Message m = new Message();
            m.id = c.getLong(c.getColumnIndexOrThrow("id"));
            m.chatId = c.getLong(c.getColumnIndexOrThrow("chat_id"));
            m.outgoing = c.getInt(c.getColumnIndexOrThrow("outgoing")) == 1;
            m.text = c.getString(c.getColumnIndexOrThrow("text"));
            m.time = c.getLong(c.getColumnIndexOrThrow("time"));
            out.add(m);
        }
        c.close();
        return out;
    }
}
