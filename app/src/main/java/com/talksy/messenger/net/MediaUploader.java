package com.talksy.messenger.net;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MediaUploader {

    public static final long MAX_FILE_BYTES = 100L * 1024 * 1024;

    public static class PreparedFile {
        public String base64;
        public String filename;
        public String mimeType;
        public long sizeBytes;
    }

    public static class TooLargeException extends IOException {
        public TooLargeException(String msg) { super(msg); }
    }

    public static PreparedFile prepare(Context ctx, Uri uri) throws IOException {
        String filename = queryDisplayName(ctx, uri);
        String mimeType = ctx.getContentResolver().getType(uri);
        if (mimeType == null) mimeType = guessMimeFromName(filename);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IOException(ctx.getString(com.talksy.messenger.R.string.file_open_failed));
            byte[] buf = new byte[16 * 1024];
            long total = 0;
            int n;
            while ((n = is.read(buf)) != -1) {
                total += n;
                if (total > MAX_FILE_BYTES) {
                    throw new TooLargeException(ctx.getString(com.talksy.messenger.R.string.file_too_big_prefix) + (MAX_FILE_BYTES / (1024 * 1024)) + ctx.getString(com.talksy.messenger.R.string.file_too_big_suffix));
                }
                bos.write(buf, 0, n);
            }
        }

        PreparedFile result = new PreparedFile();
        result.base64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        result.filename = filename;
        result.mimeType = mimeType == null ? "application/octet-stream" : mimeType;
        result.sizeBytes = bos.size();
        return result;
    }

    public static class FileInfo {
        public String name;
        public String mime;
        public long size;
    }

    public static FileInfo describe(Context ctx, Uri uri) {
        FileInfo fi = new FileInfo();
        fi.name = queryDisplayName(ctx, uri);
        String mime = ctx.getContentResolver().getType(uri);
        if (mime == null) mime = guessMimeFromName(fi.name);
        fi.mime = mime == null ? "application/octet-stream" : mime;
        fi.size = -1;
        try (Cursor c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) fi.size = c.getLong(0);
        } catch (Exception ignored) { }
        if (fi.size < 0) {
            try (android.content.res.AssetFileDescriptor afd = ctx.getContentResolver().openAssetFileDescriptor(uri, "r")) {
                if (afd != null) fi.size = afd.getLength();
            } catch (Exception ignored) { }
        }
        return fi;
    }

    private static String queryDisplayName(Context ctx, Uri uri) {
        String name = "file";
        try (Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String n = cursor.getString(idx);
                    if (n != null && !n.isEmpty()) name = n;
                }
            }
        } catch (Exception ignored) {}
        return name;
    }

    private static String guessMimeFromName(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return null;
        String ext = filename.substring(dot + 1).toLowerCase();
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
    }
}
