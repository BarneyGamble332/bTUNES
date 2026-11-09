package de.bernhard.musik;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

@CapacitorPlugin(name = "Music", permissions = {
    @Permission(alias = "audio", strings = { Manifest.permission.READ_MEDIA_AUDIO }),
    @Permission(alias = "storage", strings = { Manifest.permission.READ_EXTERNAL_STORAGE }),
    @Permission(alias = "notify", strings = { Manifest.permission.POST_NOTIFICATIONS })
})
public class MusicPlugin extends Plugin {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private ListenableFuture<MediaController> future;
    private MediaController ctl;
    private final List<Runnable> waiting = new ArrayList<>();

    /* ---------- Zugriff auf die Musik ---------- */

    private boolean has(String p) {
        return ContextCompat.checkSelfPermission(getContext(), p) == PackageManager.PERMISSION_GRANTED;
    }
    private boolean granted() {
        return Build.VERSION.SDK_INT >= 33 ? has(Manifest.permission.READ_MEDIA_AUDIO) : has(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject r = new JSObject();
        r.put("granted", granted());
        call.resolve(r);
    }

    @PluginMethod
    public void request(PluginCall call) {
        if (granted()) { status(call); return; }
        String[] a = Build.VERSION.SDK_INT >= 33 ? new String[]{ "audio", "notify" } : new String[]{ "storage" };
        requestPermissionForAliases(a, call, "permDone");
    }

    @PermissionCallback
    private void permDone(PluginCall call) { status(call); }

    /* Alle Musikstücke auf dem Handy aus der Android-Medienbibliothek */
    @PluginMethod
    public void scan(PluginCall call) {
        pool.execute(() -> {
            try {
                ContentResolver cr = getContext().getContentResolver();
                boolean q = Build.VERSION.SDK_INT >= 29;
                String[] proj = {
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.YEAR,
                    q ? MediaStore.MediaColumns.RELATIVE_PATH : MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DISPLAY_NAME, "album_artist"
                };
                Cursor c;
                try {
                    c = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, MediaStore.Audio.Media.IS_MUSIC + " != 0", null, null);
                } catch (Exception e) {   // ältere Geräte ohne Spalte album_artist
                    String[] p2 = new String[proj.length - 1];
                    System.arraycopy(proj, 0, p2, 0, p2.length);
                    c = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, p2, MediaStore.Audio.Media.IS_MUSIC + " != 0", null, null);
                }
                JSArray out = new JSArray();
                if (c != null) {
                    try {
                        int n = c.getColumnCount();
                        while (c.moveToNext()) {
                            JSONArray t = new JSONArray();
                            t.put(c.getLong(0));
                            t.put(s(c, 1)); t.put(s(c, 2)); t.put(s(c, 3));
                            t.put(c.getLong(4)); t.put(c.getLong(5)); t.put(c.getInt(6)); t.put(c.getInt(7));
                            String path = s(c, 8);
                            if (!q && path.lastIndexOf('/') > 0) path = path.substring(0, path.lastIndexOf('/') + 1);
                            t.put(path); t.put(s(c, 9));
                            t.put(n > 10 ? s(c, 10) : "");
                            out.put(t);
                        }
                    } finally { c.close(); }
                }
                JSObject r = new JSObject();
                r.put("tracks", out);
                call.resolve(r);
            } catch (SecurityException e) {
                call.reject("Kein Zugriff auf Musik");
            } catch (Exception e) {
                call.reject("Musik konnte nicht gelesen werden: " + e.getMessage());
            }
        });
    }

    private static String s(Cursor c, int i) {
        String v = c.isNull(i) ? null : c.getString(i);
        return v == null || "<unknown>".equals(v) ? "" : v;
    }

    /* Albumcover als Bild-Daten (zuerst Android-Vorschau, sonst das in der Datei eingebettete Cover) */
    @PluginMethod
    public void art(PluginCall call) {
        final long album = call.getData().optLong("album", -1L);
        final long track = call.getData().optLong("track", -1L);
        final int size = call.getInt("size", 300);
        pool.execute(() -> {
            Bitmap b = null;
            ContentResolver cr = getContext().getContentResolver();
            if (Build.VERSION.SDK_INT >= 29) {
                try { if (album >= 0) b = cr.loadThumbnail(ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, album), new Size(size, size), null); } catch (Exception ignore) {}
                if (b == null) try { if (track >= 0) b = cr.loadThumbnail(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track), new Size(size, size), null); } catch (Exception ignore) {}
            }
            if (b == null && track >= 0) {
                MediaMetadataRetriever mr = new MediaMetadataRetriever();
                try {
                    mr.setDataSource(getContext(), ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track));
                    byte[] d = mr.getEmbeddedPicture();
                    if (d != null) {
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(d, 0, d.length, o);
                        int sc = 1;
                        while (Math.max(o.outWidth, o.outHeight) / (sc * 2) >= size) sc *= 2;
                        BitmapFactory.Options o2 = new BitmapFactory.Options();
                        o2.inSampleSize = sc;
                        b = BitmapFactory.decodeByteArray(d, 0, d.length, o2);
                    }
                } catch (Exception ignore) {
                } finally { try { mr.release(); } catch (Exception ignore) {} }
            }
            if (b == null) { call.reject("Kein Cover"); return; }
            if (Math.max(b.getWidth(), b.getHeight()) > size * 1.3) {
                float f = size / (float) Math.max(b.getWidth(), b.getHeight());
                b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * f), Math.round(b.getHeight() * f), true);
            }
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 85, os);
            JSObject r = new JSObject();
            r.put("url", "data:image/jpeg;base64," + Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP));
            call.resolve(r);
        });
    }

    /* ---------- Wiedergabe über den Hintergrund-Dienst ---------- */

    private void withCtl(Runnable r) {
        main.post(() -> {
            if (ctl != null) { r.run(); return; }
            waiting.add(r);
            if (future != null) return;
            SessionToken tok = new SessionToken(getContext(), new ComponentName(getContext(), PlaybackService.class));
            future = new MediaController.Builder(getContext(), tok).buildAsync();
            future.addListener(() -> {
                try {
                    ctl = future.get();
                    ctl.addListener(new Player.Listener() {
                        @Override
                        public void onEvents(Player p, Player.Events e) { notifyListeners("state", state()); }
                    });
                    for (Runnable w : waiting) w.run();
                } catch (Exception e) {
                    future = null;
                }
                waiting.clear();
            }, main::post);
        });
    }

    private JSObject state() {
        JSObject r = new JSObject();
        if (ctl == null) { r.put("ready", false); return r; }
        r.put("ready", true);
        r.put("playing", ctl.getPlayWhenReady());
        r.put("buffering", ctl.getPlaybackState() == Player.STATE_BUFFERING);
        r.put("ended", ctl.getPlaybackState() == Player.STATE_ENDED);
        r.put("index", ctl.getCurrentMediaItemIndex());
        r.put("count", ctl.getMediaItemCount());
        MediaItem m = ctl.getCurrentMediaItem();
        r.put("id", m == null ? "" : m.mediaId);
        r.put("pos", Math.max(0, ctl.getCurrentPosition()));
        long d = ctl.getDuration();
        r.put("dur", d < 0 ? 0 : d);
        r.put("shuffle", ctl.getShuffleModeEnabled());
        r.put("repeat", ctl.getRepeatMode());
        r.put("speed", ctl.getPlaybackParameters().speed);
        return r;
    }

    @PluginMethod
    public void getState(PluginCall call) {
        withCtl(() -> {
            JSObject r = state();
            // Warteschlange mitliefern, damit die App nach dem Neustart weiß, was gerade läuft
            if (call.getBoolean("queue", false) && ctl != null) {
                JSArray ids = new JSArray();
                for (int i = 0; i < ctl.getMediaItemCount(); i++) ids.put(ctl.getMediaItemAt(i).mediaId);
                r.put("queue", ids);
            }
            call.resolve(r);
        });
    }

    /* Liste abspielen: tracks = [{id,title,artist,album}], index = Start, shuffle = Zufall */
    @PluginMethod
    public void play(PluginCall call) {
        JSONArray t = call.getData().optJSONArray("tracks");
        int index = call.getInt("index", 0);
        long start = call.getData().optLong("start", 0L);
        Boolean shuffle = call.getBoolean("shuffle", null);
        if (t == null || t.length() == 0) { call.reject("Keine Titel"); return; }
        List<MediaItem> items = new ArrayList<>();
        for (int i = 0; i < t.length(); i++) {
            JSONObject o = t.optJSONObject(i);
            if (o != null) items.add(PlaybackService.itemFrom(o));
        }
        // Für das Widget merken, damit es auch nach Beenden der App weiterspielen kann
        getContext().getSharedPreferences(BtunesWidget.PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putString("queue", t.toString()).putInt("index", index).putLong("pos", start).apply();
        withCtl(() -> {
            if (shuffle != null) ctl.setShuffleModeEnabled(shuffle);
            ctl.setMediaItems(items, Math.max(0, Math.min(index, items.size() - 1)), Math.max(0, start));
            // Musik normal schnell, Podcasts mit dem eingestellten Tempo
            ctl.setPlaybackSpeed((float) call.getData().optDouble("speed", 1.0));
            ctl.prepare();
            ctl.play();
            call.resolve(state());
        });
    }

    @PluginMethod
    public void control(PluginCall call) {
        String a = call.getString("action", "");
        withCtl(() -> {
            switch (a) {
                case "toggle":
                    if (ctl.getPlaybackState() == Player.STATE_ENDED) { ctl.seekTo(0, 0); ctl.play(); }
                    else if (ctl.getPlayWhenReady()) ctl.pause();
                    else { if (ctl.getPlaybackState() == Player.STATE_IDLE) ctl.prepare(); ctl.play(); }
                    break;
                case "pause": ctl.pause(); break;
                case "play": if (ctl.getPlaybackState() == Player.STATE_IDLE) ctl.prepare(); ctl.play(); break;
                case "next": if (ctl.hasNextMediaItem()) ctl.seekToNextMediaItem(); break;
                case "prev":
                    if (ctl.getCurrentPosition() > 3000 || !ctl.hasPreviousMediaItem()) ctl.seekTo(0);
                    else ctl.seekToPreviousMediaItem();
                    break;
                case "seek": ctl.seekTo(call.getData().optLong("ms", 0)); break;
                case "jump": ctl.seekTo(call.getInt("index", 0), 0); ctl.play(); break;
                case "shuffle": ctl.setShuffleModeEnabled(call.getBoolean("on", false)); break;
                case "repeat": ctl.setRepeatMode(call.getInt("mode", 0)); break;
                case "stop": ctl.stop(); ctl.clearMediaItems(); break;
                case "speed": ctl.setPlaybackSpeed((float) call.getData().optDouble("speed", 1.0)); break;
            }
            call.resolve(state());
        });
    }

    /* ---------- Scratchen ---------- */
    private volatile Scratch scratch;
    private final ExecutorService scPool = Executors.newSingleThreadExecutor();

    @PluginMethod
    public void scratchStart(PluginCall call) {
        final long pos = call.getData().optLong("pos", 0L);
        final String uri = call.getString("uri", "");
        final long id = call.getData().optLong("id", -1L);
        scPool.execute(() -> {
            try {
                Scratch old = scratch; scratch = null;
                if (old != null) old.stop();
                Uri u = !uri.isEmpty() ? Uri.parse(uri) : ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                Scratch sc = new Scratch();
                sc.load(getContext(), u, pos, 5000, 5000);
                sc.start(pos);
                scratch = sc;
                JSObject r = new JSObject();
                r.put("start", sc.windowStartMs());
                r.put("end", sc.windowEndMs());
                call.resolve(r);
            } catch (Exception e) {
                call.reject("Scratchen nicht möglich: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void scratchMove(PluginCall call) {
        Scratch sc = scratch;
        if (sc != null) sc.move(call.getData().optLong("pos", 0L));
        call.resolve();
    }

    @PluginMethod
    public void scratchStop(PluginCall call) {
        scPool.execute(() -> {
            Scratch sc = scratch; scratch = null;
            JSObject r = new JSObject();
            if (sc != null) r.put("pos", sc.stop());
            call.resolve(r);
        });
    }

    /* ---------- Podcast-Downloads (App-Ordner, keine Berechtigung nötig) ---------- */
    private final ExecutorService dlPool = Executors.newFixedThreadPool(2);
    private final java.util.Set<String> cancelled = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private java.io.File dlDir() {
        java.io.File d = getContext().getExternalFilesDir("Podcasts");
        if (d == null) d = new java.io.File(getContext().getFilesDir(), "Podcasts");
        d.mkdirs();
        return d;
    }
    private static String safe(String k) { return k.replaceAll("[^A-Za-z0-9._-]", "_"); }

    @PluginMethod
    public void download(PluginCall call) {
        final String key = call.getString("key", "");
        final String url = call.getString("url", "");
        if (key.isEmpty() || url.isEmpty()) { call.reject("Fehlt"); return; }
        cancelled.remove(key);
        dlPool.execute(() -> {
            java.io.File out = new java.io.File(dlDir(), safe(key) + ".audio");
            java.io.File part = new java.io.File(dlDir(), safe(key) + ".part");
            java.net.HttpURLConnection con = null;
            try {
                String u = url;
                // Weiterleitungen selbst verfolgen (auch von http nach https, das macht Android nicht automatisch)
                for (int i = 0; i < 10; i++) {
                    con = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                    con.setInstanceFollowRedirects(false);
                    con.setConnectTimeout(15000);
                    con.setReadTimeout(30000);
                    con.setRequestProperty("User-Agent", "bTUNES/1.0 (Android)");
                    int code = con.getResponseCode();
                    if (code >= 300 && code < 400 && con.getHeaderField("Location") != null) {
                        u = new java.net.URL(new java.net.URL(u), con.getHeaderField("Location")).toString();
                        con.disconnect();
                        continue;
                    }
                    if (code >= 400) throw new Exception("HTTP " + code);
                    break;
                }
                long total = con.getContentLengthLong(), got = 0, lastSent = 0;
                try (java.io.InputStream in = con.getInputStream(); java.io.OutputStream os = new java.io.FileOutputStream(part)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (cancelled.contains(key)) throw new java.util.concurrent.CancellationException();
                        os.write(buf, 0, n);
                        got += n;
                        long now = System.currentTimeMillis();
                        if (now - lastSent > 400) {
                            lastSent = now;
                            JSObject ev = new JSObject();
                            ev.put("key", key); ev.put("got", got); ev.put("total", total);
                            notifyListeners("dl", ev);
                        }
                    }
                }
                if (out.exists()) out.delete();
                if (!part.renameTo(out)) throw new Exception("Speichern fehlgeschlagen");
                JSObject r = new JSObject();
                r.put("path", out.getAbsolutePath());
                r.put("size", out.length());
                call.resolve(r);
            } catch (java.util.concurrent.CancellationException e) {
                part.delete();
                call.reject("abgebrochen");
            } catch (Exception e) {
                part.delete();
                call.reject("Download fehlgeschlagen: " + e.getMessage());
            } finally {
                if (con != null) con.disconnect();
                cancelled.remove(key);
            }
        });
    }

    @PluginMethod
    public void cancelDownload(PluginCall call) {
        cancelled.add(call.getString("key", ""));
        call.resolve();
    }

    @PluginMethod
    public void deleteDownload(PluginCall call) {
        new java.io.File(dlDir(), safe(call.getString("key", "")) + ".audio").delete();
        call.resolve();
    }

    /* Was liegt schon auf dem Handy? (zum Abgleich mit der App) */
    @PluginMethod
    public void listDownloads(PluginCall call) {
        JSArray a = new JSArray();
        java.io.File[] fs = dlDir().listFiles();
        if (fs != null) for (java.io.File f : fs) {
            if (f.getName().endsWith(".part")) { f.delete(); continue; }
            if (!f.getName().endsWith(".audio")) continue;
            JSObject o = new JSObject();
            o.put("name", f.getName().substring(0, f.getName().length() - 6));
            o.put("path", f.getAbsolutePath());
            o.put("size", f.length());
            a.put(o);
        }
        JSObject r = new JSObject();
        r.put("files", a);
        android.os.StatFs st = new android.os.StatFs(dlDir().getAbsolutePath());
        r.put("free", st.getAvailableBytes());
        call.resolve(r);
    }

    @Override
    protected void handleOnDestroy() {
        if (future != null) MediaController.releaseFuture(future);
        future = null; ctl = null;
        pool.shutdown();
        dlPool.shutdown();
        Scratch sc = scratch; scratch = null; if (sc != null) sc.stop();
        scPool.shutdown();
    }
}
