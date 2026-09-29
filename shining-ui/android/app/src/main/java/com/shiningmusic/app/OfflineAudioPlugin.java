package com.shiningmusic.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Complete, persistent, per-account song bundles. Only explicit playback starts enqueue downloads. */
@CapacitorPlugin(name = "OfflineAudio")
public class OfflineAudioPlugin extends Plugin {
    private final ExecutorService downloads = Executors.newSingleThreadExecutor();
    private final ExecutorService readers = Executors.newSingleThreadExecutor();
    private final Set<String> active = Collections.synchronizedSet(new HashSet<>());
    private volatile boolean offlineSelected = false;
    private volatile int sourceEpoch = 0;
    private CacheDatabase database;

    private static final class CacheDatabase extends SQLiteOpenHelper {
        CacheDatabase(android.content.Context context) {
            super(context, "shining-offline-cache.db", null, 1);
        }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE cached_song (account_id TEXT NOT NULL, song_id TEXT NOT NULL, " +
                "status TEXT NOT NULL, manifest TEXT, error TEXT, PRIMARY KEY (account_id, song_id))");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Database version 1 has no previous schema.
        }
    }

    @Override public void load() {
        database = new CacheDatabase(getContext());
        downloads.execute(this::recoverCompletedBundles);
    }

    private static String id(PluginCall call, String field) throws IOException {
        String value = call.getString(field);
        if (value == null || !value.matches("[0-9]{1,20}")) throw new IOException("INVALID_" + field.toUpperCase(Locale.ROOT));
        return value;
    }

    private File songDir(String accountId, String songId) {
        return new File(new File(new File(getContext().getFilesDir(), "offline"), accountId), "audio/" + songId);
    }

    private File tempDir(String accountId, String songId) {
        return new File(new File(new File(getContext().getFilesDir(), "offline"), accountId), "tmp/" + songId);
    }

    private synchronized JSONObject record(String accountId, String songId) throws JSONException {
        try (Cursor cursor = database.getReadableDatabase().query("cached_song",
            new String[] {"status", "manifest", "error"}, "account_id=? AND song_id=?",
            new String[] {accountId, songId}, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            JSONObject result = new JSONObject();
            result.put("status", cursor.getString(0));
            if (!cursor.isNull(1)) result.put("manifest", new JSONObject(cursor.getString(1)));
            if (!cursor.isNull(2)) result.put("error", cursor.getString(2));
            return result;
        }
    }

    private synchronized void save(String accountId, String songId, String status, JSONObject manifest, String error) {
        ContentValues values = new ContentValues();
        values.put("account_id", accountId);
        values.put("song_id", songId);
        values.put("status", status);
        if (manifest != null) values.put("manifest", manifest.toString());
        if (error != null) values.put("error", error);
        database.getWritableDatabase().insertWithOnConflict("cached_song", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static void writeJson(File file, JSONObject json) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.getFD().sync();
        }
    }

    private static JSONObject readJson(File file) throws IOException, JSONException {
        if (file.length() > 2 * 1024 * 1024 || file.length() < 2) throw new IOException("INVALID_MANIFEST_SIZE");
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int pos = 0;
            while (pos < bytes.length) {
                int count = in.read(bytes, pos, bytes.length - pos);
                if (count < 0) throw new IOException("INCOMPLETE_MANIFEST");
                pos += count;
            }
        }
        return new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) text.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return text.toString();
    }

    private static String checksum(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }

    private static boolean complete(File directory, JSONObject manifest) throws Exception {
        String audioName = manifest.getString("audioFileName");
        if (!audioName.matches("audio\\.(mp3|flac|ogg|wav|m4a)")) return false;
        String mime = manifest.getString("mimeType");
        if (!(audioName.equals("audio.mp3") && mime.equals("audio/mpeg") ||
              audioName.equals("audio.flac") && mime.equals("audio/flac") ||
              audioName.equals("audio.ogg") && mime.equals("audio/ogg") ||
              audioName.equals("audio.wav") && mime.equals("audio/wav") ||
              audioName.equals("audio.m4a") && mime.equals("audio/mp4"))) return false;
        File audio = new File(directory, audioName);
        if (!audio.isFile() || audio.length() < 128 || audio.length() != manifest.getLong("sizeBytes")) return false;
        if (!checksum(audio).equals(manifest.getString("sha256"))) return false;
        String cover = manifest.getString("coverState");
        if (!"SOURCE_ABSENT".equals(cover) && !"READY".equals(cover)) return false;
        if ("READY".equals(cover)) {
            String coverName = manifest.getString("coverFileName");
            if (!coverName.matches("cover\\.(png|jpg|gif|webp)")) return false;
            File coverFile = new File(directory, coverName);
            if (!coverFile.isFile() || coverFile.length() < 1) return false;
            if (!checksum(coverFile).equals(manifest.getString("coverSha256"))) return false;
        }
        if (!manifest.has("metadata") || !manifest.has("lyrics")) return false;
        JSONObject metadata = manifest.getJSONObject("metadata");
        if (!manifest.getString("accountId").matches("[0-9]{1,20}") ||
            !manifest.getString("songId").matches("[0-9]{1,20}") ||
            !manifest.getString("songId").equals(String.valueOf(metadata.get("id"))) ||
            metadata.getString("title").trim().isEmpty() ||
            !metadata.has("artistId") || metadata.getString("artistName").trim().isEmpty() ||
            !Double.isFinite(metadata.getDouble("duration")) ||
            metadata.getDouble("duration") <= 0) return false;
        JSONArray lyrics = manifest.getJSONArray("lyrics");
        String lyricsState = manifest.getString("lyricsState");
        if (!((lyrics.length() == 0 && "SOURCE_ABSENT".equals(lyricsState)) ||
            (lyrics.length() > 0 && "READY".equals(lyricsState)))) return false;
        for (int i = 0; i < lyrics.length(); i++) {
            JSONObject entry = lyrics.getJSONObject(i);
            if (!entry.has("id") || !entry.has("content") || entry.isNull("content") ||
                entry.getString("languageMsg").trim().isEmpty()) return false;
        }
        return true;
    }

    private void recoverCompletedBundles() {
        File root = new File(getContext().getFilesDir(), "offline");
        File[] accounts = root.listFiles();
        if (accounts == null) return;
        for (File account : accounts) {
            if (!account.isDirectory() || !account.getName().matches("[0-9]{1,20}")) continue;
            File[] songs = new File(account, "audio").listFiles();
            if (songs != null) for (File song : songs) {
                if (!song.isDirectory() || !song.getName().matches("[0-9]{1,20}")) continue;
                try {
                    File manifestFile = new File(song, "manifest.json");
                    if (!manifestFile.isFile()) continue;
                    JSONObject manifest = readJson(manifestFile);
                    if (account.getName().equals(manifest.optString("accountId")) &&
                        song.getName().equals(manifest.optString("songId")) && complete(song, manifest)) {
                        save(account.getName(), song.getName(), "READY", manifest, null);
                    }
                } catch (Exception exception) {
                    Log.w("OfflineAudio", "Cannot recover complete bundle", exception);
                }
            }
            File[] pending = new File(account, "tmp").listFiles();
            if (pending == null) continue;
            for (File song : pending) {
                if (!song.isDirectory() || !song.getName().matches("[0-9]{1,20}")) continue;
                try {
                    File manifestFile = new File(song, "manifest.json");
                    if (!manifestFile.isFile()) continue;
                    JSONObject manifest = readJson(manifestFile);
                    if (!account.getName().equals(manifest.optString("accountId")) ||
                        !song.getName().equals(manifest.optString("songId")) || !complete(song, manifest)) continue;
                    File target = songDir(account.getName(), song.getName());
                    if (target.exists()) continue; // Preserve both complete bundles for manual recovery.
                    File parent = target.getParentFile();
                    if ((parent.exists() || parent.mkdirs()) && song.renameTo(target)) {
                        save(account.getName(), song.getName(), "READY", manifest, null);
                    }
                } catch (Exception error) {
                    Log.w("OfflineAudio", "Cannot recover pending complete bundle", error);
                }
            }
        }
    }

    private static URL validUrl(String requested, String origin) throws Exception {
        URL url = new URL(requested);
        URL server = new URL(origin);
        int port = url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
        int serverPort = server.getPort() == -1 ? server.getDefaultPort() : server.getPort();
        if ((!"http".equals(url.getProtocol()) && !"https".equals(url.getProtocol())) ||
            !url.getProtocol().equals(server.getProtocol()) || !url.getHost().equalsIgnoreCase(server.getHost()) ||
            port != serverPort || url.getUserInfo() != null || url.getRef() != null) throw new IOException("INVALID_SOURCE");
        return url;
    }

    private long download(String requested, String origin, File target, boolean audio, int epoch) throws Exception {
        if (offlineSelected || sourceEpoch != epoch) throw new IOException("OFFLINE_MODE");
        URL url = validUrl(requested, origin);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept-Encoding", "identity");
        try {
            if (offlineSelected || sourceEpoch != epoch) throw new IOException("OFFLINE_MODE");
            if (connection.getResponseCode() != 200) throw new IOException("HTTP_" + connection.getResponseCode());
            String type = connection.getContentType();
            if (type != null && (type.toLowerCase(Locale.ROOT).contains("text/html") ||
                type.toLowerCase(Locale.ROOT).contains("application/json"))) throw new IOException("INVALID_MEDIA_TYPE");
            long expected = connection.getContentLengthLong();
            long total = 0;
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (offlineSelected || sourceEpoch != epoch) throw new IOException("OFFLINE_MODE");
                    if (Thread.currentThread().isInterrupted()) throw new IOException("INTERRUPTED");
                    output.write(buffer, 0, count);
                    total += count;
                }
                output.getFD().sync();
            }
            if (total < (audio ? 128 : 1) || (expected >= 0 && total != expected)) throw new IOException("INCOMPLETE_FILE");
            return total;
        } finally {
            connection.disconnect();
        }
    }

    private static String detectAudio(File file) throws Exception {
        byte[] first = new byte[12];
        try (FileInputStream in = new FileInputStream(file)) {
            if (in.read(first) < 12) throw new IOException("INVALID_AUDIO");
        }
        if ((first[0] & 0xff) == 0x49 && first[1] == 0x44 && first[2] == 0x33 ||
            (first[0] & 0xff) == 0xff && ((first[1] & 0xe0) == 0xe0)) return "audio/mpeg";
        if (first[0] == 'f' && first[1] == 'L' && first[2] == 'a' && first[3] == 'C') return "audio/flac";
        if (first[0] == 'O' && first[1] == 'g' && first[2] == 'g' && first[3] == 'S') return "audio/ogg";
        if (first[0] == 'R' && first[1] == 'I' && first[2] == 'F' && first[3] == 'F') return "audio/wav";
        if (first[4] == 'f' && first[5] == 't' && first[6] == 'y' && first[7] == 'p') return "audio/mp4";
        throw new IOException("UNSUPPORTED_AUDIO_FORMAT");
    }

    private static String detectCover(File file) throws Exception {
        byte[] first = new byte[12];
        try (FileInputStream in = new FileInputStream(file)) {
            if (in.read(first) < 12) throw new IOException("INVALID_COVER");
        }
        if ((first[0] & 0xff) == 0x89 && first[1] == 'P' && first[2] == 'N' && first[3] == 'G') return "png";
        if ((first[0] & 0xff) == 0xff && (first[1] & 0xff) == 0xd8 && (first[2] & 0xff) == 0xff) return "jpg";
        if (first[0] == 'G' && first[1] == 'I' && first[2] == 'F') return "gif";
        if (first[0] == 'R' && first[1] == 'I' && first[2] == 'F' && first[3] == 'F' &&
            first[8] == 'W' && first[9] == 'E' && first[10] == 'B' && first[11] == 'P') return "webp";
        throw new IOException("UNSUPPORTED_COVER_FORMAT");
    }

    private void publish(String accountId, String songId, String status, String error) {
        JSObject event = new JSObject();
        event.put("accountId", accountId);
        event.put("songId", songId);
        event.put("status", status);
        if (error != null) event.put("error", error);
        notifyListeners("cacheStatus", event);
    }

    @PluginMethod public void enqueueDownload(PluginCall call) {
        readers.execute(() -> startDownload(call));
    }

    @PluginMethod public void setOfflineMode(PluginCall call) {
        boolean next = Boolean.TRUE.equals(call.getBoolean("offline"));
        if (next != offlineSelected) {
            offlineSelected = next;
            sourceEpoch++;
        }
        call.resolve();
    }

    private void startDownload(PluginCall call) {
        try {
            if (offlineSelected) throw new IOException("OFFLINE_MODE");
            final int downloadEpoch = sourceEpoch;
            String accountId = id(call, "accountId");
            String songId = id(call, "songId");
            String audioUrl = call.getString("audioUrl");
            String coverUrl = call.getString("coverUrl");
            String origin = call.getString("serverOrigin");
            JSObject metadata = call.getObject("metadata");
            JSArray lyrics = call.getArray("lyrics");
            if (metadata == null || !songId.equals(String.valueOf(metadata.get("id"))) ||
                metadata.getString("title").trim().isEmpty() || !metadata.has("artistId") ||
                metadata.getString("artistName").trim().isEmpty() ||
                !Double.isFinite(metadata.getDouble("duration")) || metadata.getDouble("duration") <= 0 ||
                lyrics == null) throw new IOException("INCOMPLETE_METADATA");
            validUrl(audioUrl, origin);
            if (coverUrl != null && !coverUrl.isEmpty()) validUrl(coverUrl, origin);
            String key = accountId + ":" + songId;
            JSONObject existing = record(accountId, songId);
            if (existing != null && "READY".equals(existing.optString("status")) &&
                accountId.equals(existing.getJSONObject("manifest").optString("accountId")) &&
                songId.equals(existing.getJSONObject("manifest").optString("songId")) &&
                complete(songDir(accountId, songId), existing.getJSONObject("manifest"))) {
                JSObject result = new JSObject(); result.put("status", "READY"); call.resolve(result); return;
            }
            if (!active.add(key)) {
                JSObject result = new JSObject(); result.put("status", "DOWNLOADING"); call.resolve(result); return;
            }
            JSONObject snapshot = new JSONObject(metadata.toString());
            JSONArray lyricSnapshot = new JSONArray(lyrics.toString());
            save(accountId, songId, "QUEUED", null, null);
            downloads.execute(() -> {
                try {
                    File dir = songDir(accountId, songId);
                    File tmp = tempDir(accountId, songId);
                    if (dir.isDirectory() && new File(dir, "manifest.json").isFile()) {
                        JSONObject previous = readJson(new File(dir, "manifest.json"));
                        if (complete(dir, previous)) {
                            save(accountId, songId, "READY", previous, null);
                            publish(accountId, songId, "READY", null);
                            return;
                        }
                    }
                    File pendingManifest = new File(tmp, "manifest.json");
                    if (pendingManifest.isFile()) {
                        try {
                            JSONObject previous = readJson(pendingManifest);
                            if (complete(tmp, previous)) {
                                if (dir.exists()) throw new IOException("EXISTING_BUNDLE_NEEDS_RECOVERY");
                                File parent = dir.getParentFile();
                                if (!(parent.exists() || parent.mkdirs()) || !tmp.renameTo(dir)) {
                                    throw new IOException("RECOVER_BUNDLE_FAILED");
                                }
                                save(accountId, songId, "READY", previous, null);
                                publish(accountId, songId, "READY", null);
                                return;
                            }
                        } catch (IOException | JSONException malformedManifest) {
                            Log.w("OfflineAudio", "Incomplete pending manifest; retrying download", malformedManifest);
                        }
                    }
                    if (!tmp.exists() && !tmp.mkdirs()) throw new IOException("CREATE_TEMP_FAILED");
                    save(accountId, songId, "DOWNLOADING", null, null);
                    publish(accountId, songId, "DOWNLOADING", null);
                    File audioFile = new File(tmp, "audio.bin");
                    long bytes = download(audioUrl, origin, audioFile, true, downloadEpoch);
                    String mime = detectAudio(audioFile);
                    String extension = "audio/mpeg".equals(mime) ? "mp3" :
                        "audio/flac".equals(mime) ? "flac" : "audio/ogg".equals(mime) ? "ogg" :
                        "audio/wav".equals(mime) ? "wav" : "m4a";
                    File finalAudio = new File(tmp, "audio." + extension);
                    if (!audioFile.renameTo(finalAudio)) throw new IOException("RENAME_AUDIO_FAILED");
                    JSONObject manifest = new JSONObject();
                    manifest.put("accountId", accountId);
                    manifest.put("songId", songId);
                    manifest.put("metadata", snapshot);
                    manifest.put("lyrics", lyricSnapshot);
                    manifest.put("lyricsState", lyricSnapshot.length() == 0 ? "SOURCE_ABSENT" : "READY");
                    manifest.put("sizeBytes", bytes);
                    manifest.put("audioFileName", finalAudio.getName());
                    manifest.put("sha256", checksum(finalAudio));
                    manifest.put("mimeType", mime);
                    if (coverUrl != null && !coverUrl.isEmpty()) {
                        File temporaryCover = new File(tmp, "cover.bin");
                        download(coverUrl, origin, temporaryCover, false, downloadEpoch);
                        File finalCover = new File(tmp, "cover." + detectCover(temporaryCover));
                        if (!temporaryCover.renameTo(finalCover)) throw new IOException("RENAME_COVER_FAILED");
                        manifest.put("coverState", "READY");
                        manifest.put("coverFileName", finalCover.getName());
                        manifest.put("coverSha256", checksum(finalCover));
                    } else {
                        manifest.put("coverState", "SOURCE_ABSENT");
                    }
                    save(accountId, songId, "VERIFYING", null, null);
                    publish(accountId, songId, "VERIFYING", null);
                    writeJson(new File(tmp, "manifest.json"), manifest);
                    if (!complete(tmp, manifest)) throw new IOException("INCOMPLETE_BUNDLE");
                    File parent = dir.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) throw new IOException("CREATE_STORE_FAILED");
                    if (dir.exists()) throw new IOException("EXISTING_BUNDLE_NEEDS_RECOVERY");
                    if (!tmp.renameTo(dir)) throw new IOException("COMMIT_BUNDLE_FAILED");
                    save(accountId, songId, "READY", manifest, null);
                    publish(accountId, songId, "READY", null);
                } catch (Exception error) {
                    Log.w("OfflineAudio", "Cache task failed: " + songId + " (" + error.getClass().getSimpleName() + ")");
                    save(accountId, songId, "FAILED", null, error.getMessage());
                    publish(accountId, songId, "FAILED", error.getMessage());
                } finally {
                    active.remove(key);
                }
            });
            JSObject result = new JSObject(); result.put("status", "QUEUED"); call.resolve(result);
        } catch (Exception error) {
            call.reject("无法开始歌曲缓存：" + error.getMessage());
        }
    }

    @PluginMethod public void getCachedSong(PluginCall call) {
        readers.execute(() -> readCachedSong(call));
    }

    @PluginMethod public void listCachedSongs(PluginCall call) {
        readers.execute(() -> {
            try {
                String accountId = id(call, "accountId");
                JSArray songs = new JSArray();
                synchronized (this) {
                    try (Cursor cursor = database.getReadableDatabase().query("cached_song",
                        new String[] {"song_id", "manifest"}, "account_id=? AND status=?",
                        new String[] {accountId, "READY"}, null, null, "song_id ASC")) {
                        while (cursor.moveToNext()) {
                            if (cursor.isNull(1)) continue;
                            try {
                                String songId = cursor.getString(0);
                                JSONObject manifest = new JSONObject(cursor.getString(1));
                                if (!accountId.equals(manifest.optString("accountId")) ||
                                    !songId.equals(manifest.optString("songId")) ||
                                    !complete(songDir(accountId, songId), manifest)) continue;
                                JSONObject song = new JSONObject(manifest.getJSONObject("metadata").toString());
                                song.put("id", songId);
                                song.put("sizeBytes", manifest.optLong("sizeBytes"));
                                songs.put(song);
                            } catch (Exception invalid) {
                                Log.w("OfflineAudio", "Ignoring invalid cached song", invalid);
                            }
                        }
                    }
                }
                JSObject result = new JSObject();
                result.put("songs", songs);
                call.resolve(result);
            } catch (Exception error) {
                call.reject("列出本地歌曲失败：" + error.getMessage());
            }
        });
    }

    private void readCachedSong(PluginCall call) {
        try {
            String accountId = id(call, "accountId");
            String songId = id(call, "songId");
            JSONObject row = record(accountId, songId);
            JSObject result = new JSObject();
            if (row == null) { result.put("status", "NOT_CACHED"); call.resolve(result); return; }
            String status = row.getString("status");
            if (("QUEUED".equals(status) || "DOWNLOADING".equals(status) ||
                "VERIFYING".equals(status)) && !active.contains(accountId + ":" + songId)) {
                status = "FAILED";
                save(accountId, songId, status, null, "DOWNLOAD_INTERRUPTED");
                result.put("error", "DOWNLOAD_INTERRUPTED");
            }
            if ("READY".equals(status)) {
                JSONObject manifest = row.getJSONObject("manifest");
                File dir = songDir(accountId, songId);
                if (!accountId.equals(manifest.optString("accountId")) ||
                    !songId.equals(manifest.optString("songId")) || !complete(dir, manifest)) {
                    save(accountId, songId, "MISSING", manifest, "CACHE_DAMAGED");
                    status = "MISSING";
                } else {
                    result.put("metadata", manifest.getJSONObject("metadata"));
                    result.put("lyrics", manifest.getJSONArray("lyrics"));
                    result.put("lyricsState", manifest.getString("lyricsState"));
                    result.put("coverState", manifest.getString("coverState"));
                    result.put("mimeType", manifest.getString("mimeType"));
                    result.put("sizeBytes", manifest.getLong("sizeBytes"));
                    result.put("audioFileUri", Uri.fromFile(new File(dir, manifest.getString("audioFileName"))).toString());
                    if ("READY".equals(manifest.getString("coverState"))) {
                        result.put("coverFileUri", Uri.fromFile(new File(dir, manifest.getString("coverFileName"))).toString());
                    }
                }
            }
            result.put("status", status);
            if (row.has("error")) result.put("error", row.getString("error"));
            call.resolve(result);
        } catch (Exception error) {
            call.reject("读取本地歌曲失败：" + error.getMessage());
        }
    }
}
