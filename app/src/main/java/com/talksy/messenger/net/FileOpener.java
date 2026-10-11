package com.talksy.messenger.net;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

public class FileOpener {

    public static void downloadAndOpen(Context ctx, String fullUrl, String filename) {
        try {
            String name = safeName(filename);
            String ext = android.webkit.MimeTypeMap.getFileExtensionFromUrl(name);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);

            DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fullUrl));
            request.setTitle(name);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            if (mime != null) request.setMimeType(mime);

            dm.enqueue(request);
            Toast.makeText(ctx, ctx.getString(com.talksy.messenger.R.string.downloading_file) + name + "»…", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(ctx, ctx.getString(com.talksy.messenger.R.string.file_download_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private static String safeName(String name) {
        if (name == null || name.isEmpty()) return "file";
        return name.replaceAll("[^a-zA-Zа-яА-Я0-9._-]", "_");
    }
}
