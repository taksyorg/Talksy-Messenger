package com.talksy.messenger.sync;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

public final class OutboxFiles {
    private OutboxFiles() {}

    public static String copy(Context ctx, Uri uri, String name) {
        try {
            File dir = new File(ctx.getFilesDir(), "outbox_media");
            if (!dir.exists() && !dir.mkdirs()) return null;
            String safe = (name == null ? "file" : name).replaceAll("[^\\p{L}\\p{N}._-]", "_");
            File out = new File(dir, UUID.randomUUID().toString().substring(0, 8) + "_" + safe);
            try (InputStream in = ctx.getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(out)) {
                if (in == null) return null;
                byte[] buf = new byte[64 * 1024]; int n;
                while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
            }
            return out.getAbsolutePath();
        } catch (Exception e) { return null; }
    }
}
