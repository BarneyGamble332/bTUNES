package de.bernhard.musik;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/* Liefert die Alben für den Cover-Stapel im Taschen-Player-Widget */
public class TpStackService extends RemoteViewsService {
    static class Album { long id, track; String name, artist; }

    private static List<Album> cache;
    private static long cacheAt;

    /* Alle Alben mit Musik, alphabetisch (wie in der App) */
    static synchronized List<Album> albums(Context ctx, boolean fresh) {
        if (!fresh && cache != null && System.currentTimeMillis() - cacheAt < 10 * 60 * 1000) return cache;
        LinkedHashMap<Long, Album> m = new LinkedHashMap<>();
        boolean q = Build.VERSION.SDK_INT >= 29;
        String[] proj = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST,
            q ? MediaStore.MediaColumns.RELATIVE_PATH : MediaStore.MediaColumns.DATA};
        // nur die in der App ausgewählten Musikordner (leer = alle)
        List<String> sel = new ArrayList<>();
        try {
            org.json.JSONArray f = new org.json.JSONArray(ctx.getSharedPreferences(BtunesWidget.PREFS, Context.MODE_PRIVATE).getString("mfolders", "[]"));
            for (int i = 0; i < f.length(); i++) sel.add(f.getString(i));
        } catch (Exception ignore) {}
        try (Cursor c = ctx.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, null)) {
            while (c != null && c.moveToNext()) {
                long aid = c.getLong(1);
                if (m.containsKey(aid)) continue;
                if (!sel.isEmpty()) {
                    String p = c.getString(4) == null ? "" : c.getString(4);
                    if (!q && p.lastIndexOf('/') > 0) p = p.substring(0, p.lastIndexOf('/') + 1);
                    p = p.replaceFirst("^/storage/(emulated/\\d+|[^/]+)/", "").replaceFirst("^/+", "");
                    if (!p.isEmpty() && !p.endsWith("/")) p += "/";
                    boolean ok = false;
                    for (String f : sel) if (p.startsWith(f)) { ok = true; break; }
                    if (!ok) continue;
                }
                Album a = new Album();
                a.id = aid; a.track = c.getLong(0);
                a.name = c.getString(2) == null ? "" : c.getString(2);
                a.artist = c.getString(3) == null ? "" : c.getString(3);
                m.put(aid, a);
            }
        } catch (Exception ignore) {}
        List<Album> l = new ArrayList<>(m.values());
        Collator co = Collator.getInstance(Locale.GERMAN);
        co.setStrength(Collator.PRIMARY);
        l.sort((x, y) -> co.compare(x.name, y.name));
        cache = l;
        cacheAt = System.currentTimeMillis();
        return l;
    }

    static int indexOf(Context ctx, long album) {
        List<Album> l = albums(ctx, false);
        for (int i = 0; i < l.size(); i++) if (l.get(i).id == album) return i;
        return -1;
    }

    static long albumOf(Context ctx, long track) {
        if (track < 0) return -1;
        try (Cursor c = ctx.getContentResolver().query(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track),
                new String[]{MediaStore.Audio.Media.ALBUM_ID}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getLong(0);
        } catch (Exception ignore) {}
        return -1;
    }

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        final Context ctx = getApplicationContext();
        return new RemoteViewsFactory() {
            List<Album> list = new ArrayList<>();
            public void onCreate() {}
            public void onDataSetChanged() { list = albums(ctx, true); }
            public void onDestroy() {}
            public int getCount() { return list.size(); }
            public RemoteViews getViewAt(int i) {
                RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.tp_stack_item);
                if (i < 0 || i >= list.size()) return v;
                Album a = list.get(i);
                v.setTextViewText(R.id.tp_item_name, a.name + (a.artist.isEmpty() ? "" : "\n\n" + a.artist));
                Bitmap b = null;
                if (Build.VERSION.SDK_INT >= 29) {
                    try { b = ctx.getContentResolver().loadThumbnail(ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, a.id), new Size(260, 260), null); } catch (Exception ignore) {}
                    if (b == null) try { b = ctx.getContentResolver().loadThumbnail(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, a.track), new Size(260, 260), null); } catch (Exception ignore) {}
                }
                if (b != null) { v.setImageViewBitmap(R.id.tp_item_cover, b); v.setViewVisibility(R.id.tp_item_cover, View.VISIBLE); }
                else v.setViewVisibility(R.id.tp_item_cover, View.GONE);
                v.setOnClickFillInIntent(R.id.tp_item, new Intent().putExtra("album", a.id));
                return v;
            }
            public RemoteViews getLoadingView() { return null; }
            public int getViewTypeCount() { return 1; }
            public long getItemId(int i) { return i < list.size() ? list.get(i).id : i; }
            public boolean hasStableIds() { return true; }
        };
    }
}
