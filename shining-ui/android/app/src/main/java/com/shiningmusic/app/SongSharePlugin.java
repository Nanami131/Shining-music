package com.shiningmusic.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

@CapacitorPlugin(name = "SongShare")
public class SongSharePlugin extends Plugin {
    @PluginMethod
    public void copyText(PluginCall call) {
        String value = call.getString("text");
        if (value == null || value.isEmpty()) {
            call.reject("复制内容为空");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            call.reject("无法访问系统剪贴板");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Shining Music 歌曲链接", value));
        call.resolve();
    }

    private byte[] decodeImage(PluginCall call) {
        String base64 = call.getString("base64");
        if (base64 == null || base64.isEmpty()) {
            call.reject("图片数据为空");
            return null;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            if (bytes.length < 8 || bytes[0] != (byte) 0x89 || bytes[1] != 0x50 ||
                    bytes[2] != 0x4e || bytes[3] != 0x47) {
                call.reject("图片格式无效");
                return null;
            }
            return bytes;
        } catch (IllegalArgumentException error) {
            call.reject("图片数据无效", error);
            return null;
        }
    }

    private String safeName(PluginCall call) {
        String name = call.getString("fileName", "Shining-Music.png");
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-").trim();
        return (name.isEmpty() ? "Shining-Music" : name) + (name.endsWith(".png") ? "" : ".png");
    }

    @PluginMethod
    public void saveImage(PluginCall call) {
        byte[] bytes = decodeImage(call);
        if (bytes == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            call.reject("保存到相册需要 Android 10 或更高版本");
            return;
        }

        ContentResolver resolver = getContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, safeName(call));
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Shining Music");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            call.reject("相册文件创建失败");
            return;
        }

        try {
            try (OutputStream stream = resolver.openOutputStream(uri)) {
                if (stream == null) throw new IOException("无法写入图片");
                stream.write(bytes);
            }
            ContentValues completed = new ContentValues();
            completed.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, completed, null, null);
            JSObject result = new JSObject();
            result.put("uri", uri.toString());
            call.resolve(result);
        } catch (Exception error) {
            resolver.delete(uri, null, null);
            call.reject("图片保存失败", error);
        }
    }

    @PluginMethod
    public void shareImage(PluginCall call) {
        byte[] bytes = decodeImage(call);
        if (bytes == null) return;

        try {
            File directory = new File(getContext().getCacheDir(), "song-shares");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("无法创建分享缓存目录");
            }
            File file = new File(directory, "song-share.png");
            try (FileOutputStream stream = new FileOutputStream(file)) {
                stream.write(bytes);
            }
            Uri uri = FileProvider.getUriForFile(getContext(),
                    getContext().getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_TEXT, call.getString("text", ""));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(call, Intent.createChooser(intent, "分享歌曲"), "shareResult");
        } catch (Exception error) {
            call.reject("打开分享失败", error);
        }
    }

    @ActivityCallback
    private void shareResult(PluginCall call, androidx.activity.result.ActivityResult result) {
        call.resolve();
    }
}
