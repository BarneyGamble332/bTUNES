package de.bernhard.musik;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

/* Zweites Startbildschirm-Widget im Look des Taschen-Players:
   orangefarbenes Gehäuse, Bernstein-Display mit Cover und Titel, Drehrad zum Antippen.
   In groß zusätzlich ein Cover-Stapel zum Durchwischen – Antippen spielt das Album. */
public class TaschenWidget extends AppWidgetProvider {
    private static String coverFor = "";
    private static Bitmap cover, wheel;
    private static long shownAlbum = Long.MIN_VALUE;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        shownAlbum = Long.MIN_VALUE;
        refreshAsync(ctx);
    }

    static void refreshAsync(Context ctx) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try { refresh(app); } catch (Exception ignore) {}
        }).start();
    }

    /* Album-Liste im Stapel neu laden (z. B. nach dem Durchsuchen der Musik) */
    static void albumsChanged(Context ctx) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, TaschenWidget.class));
            if (ids != null && ids.length > 0) mgr.notifyAppWidgetViewDataChanged(ids, R.id.tp_stack);
        } catch (Exception ignore) {}
    }

    private static synchronized void refresh(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, TaschenWidget.class));
        if (ids == null || ids.length == 0) return;
        SharedPreferences p = ctx.getSharedPreferences(BtunesWidget.PREFS, Context.MODE_PRIVATE);
        String title = p.getString("title", "");
        String artist = p.getString("artist", "");
        String next = p.getString("next", "");
        boolean playing = p.getBoolean("playing", false);
        long track = p.getLong("track", -1L);
        String art = p.getString("art", "");
        String key = track + "|" + art;
        if (!key.equals(coverFor)) {
            Bitmap b = BtunesWidget.loadCover(ctx, track, art);
            if (b != null && Math.max(b.getWidth(), b.getHeight()) > 320) {
                float sc = 320f / Math.max(b.getWidth(), b.getHeight());
                b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * sc), Math.round(b.getHeight() * sc), true);
            }
            cover = b;
            coverFor = key;
        }
        if (wheel == null) wheel = drawWheel(300);

        for (int id : ids) {
            RemoteViews flat = small(ctx, title, artist, playing);
            RemoteViews mid = medium(ctx, R.layout.tp_widget, title, artist, next, playing);
            RemoteViews big = medium(ctx, R.layout.tp_widget_large, title, artist, next, playing);
            stack(ctx, big, id, track);
            RemoteViews v;
            if (Build.VERSION.SDK_INT >= 31) {
                java.util.Map<android.util.SizeF, RemoteViews> m = new java.util.HashMap<>();
                m.put(new android.util.SizeF(180f, 40f), flat);
                m.put(new android.util.SizeF(220f, 100f), mid);
                m.put(new android.util.SizeF(250f, 200f), big);
                v = new RemoteViews(m);
            } else {
                v = mid;
            }
            mgr.updateAppWidget(id, v);
        }
    }

    private static void common(Context ctx, RemoteViews v, String title, String artist) {
        v.setTextViewText(R.id.tp_title, title.isEmpty() ? "bTUNES" : title);
        v.setTextViewText(R.id.tp_artist, artist.isEmpty() ? "Leg eine Platte auf" : artist);
        if (cover != null) v.setImageViewBitmap(R.id.tp_cover, cover);
        else v.setImageViewResource(R.id.tp_cover, R.drawable.tp_cov);
        v.setOnClickPendingIntent(R.id.tp_prev, action(ctx, BtunesWidget.A_PREV, 13));
        v.setOnClickPendingIntent(R.id.tp_play, action(ctx, BtunesWidget.A_TOGGLE, 11));
        v.setOnClickPendingIntent(R.id.tp_next, action(ctx, BtunesWidget.A_NEXT, 12));
        v.setOnClickPendingIntent(R.id.tp_scr, open(ctx));
    }

    private static RemoteViews small(Context ctx, String title, String artist, boolean playing) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.tp_widget_small);
        common(ctx, v, title, artist);
        v.setImageViewResource(R.id.tp_play, playing ? R.drawable.tp_ic_pause : R.drawable.tp_ic_play);
        return v;
    }

    private static RemoteViews medium(Context ctx, int layout, String title, String artist, String next, boolean playing) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), layout);
        common(ctx, v, title, artist);
        v.setTextViewText(R.id.tp_state, title.isEmpty() ? "" : playing ? "▶" : "❚❚");
        v.setTextViewText(R.id.tp_head, playing ? "Läuft gerade" : title.isEmpty() ? "bTUNES" : "Pause");
        v.setTextViewText(R.id.tp_nexttxt, next.isEmpty() ? "" : "Als Nächstes: " + next);
        v.setViewVisibility(R.id.tp_nexttxt, next.isEmpty() ? View.GONE : View.VISIBLE);
        v.setImageViewBitmap(R.id.tp_wheel, wheel);
        v.setOnClickPendingIntent(R.id.tp_menu, open(ctx));
        v.setOnClickPendingIntent(R.id.tp_ok, open(ctx));
        return v;
    }

    /* Cover-Stapel: Alben vom Handy, beim Albumwechsel springt er zum laufenden Album */
    private static void stack(Context ctx, RemoteViews v, int widgetId, long track) {
        Intent svc = new Intent(ctx, TpStackService.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.tp_stack, svc);
        v.setEmptyView(R.id.tp_stack, R.id.tp_stack_empty);
        Intent tpl = new Intent(ctx, BtunesWidget.class).setAction(BtunesWidget.A_ALBUM);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        v.setPendingIntentTemplate(R.id.tp_stack, PendingIntent.getBroadcast(ctx, 20, tpl, fl));
        long album = TpStackService.albumOf(ctx, track);
        if (album != shownAlbum && album >= 0) {
            int idx = TpStackService.indexOf(ctx, album);
            if (idx >= 0) v.setDisplayedChild(R.id.tp_stack, idx);
        }
        shownAlbum = album;
    }

    private static PendingIntent open(Context ctx) {
        Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        return PendingIntent.getActivity(ctx, 10, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent action(Context ctx, String a, int code) {
        Intent i = new Intent(ctx, BtunesWidget.class).setAction(a);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /* Drehrad zeichnen: geriffelter Ring mit Beschriftung, Mitte OK */
    private static Bitmap drawWheel(int s) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float c = s / 2f, r = s / 2f - 6;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Schatten
        p.setColor(0x66000000);
        cv.drawCircle(c, c + 4, r, p);
        p.setColor(0xFF161616);
        cv.drawCircle(c, c, r, p);
        p.setColor(0xFF2B2B2B);
        cv.drawCircle(c, c, r - 5, p);
        // Riffelung
        p.setStrokeWidth(2.2f);
        for (int i = 0; i < 90; i++) {
            double a = i * Math.PI * 2 / 90;
            p.setColor(i % 2 == 0 ? 0xFF3C3C3C : 0xFF232323);
            float co = (float) Math.cos(a), si = (float) Math.sin(a);
            cv.drawLine(c + co * r * 0.42f, c + si * r * 0.42f, c + co * (r - 6), c + si * (r - 6), p);
        }
        Paint gl = new Paint(Paint.ANTI_ALIAS_FLAG);
        gl.setShader(new LinearGradient(0, 0, 0, s, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawCircle(c, c, r - 5, gl);
        // Mitte
        p.setColor(0xAA000000);
        cv.drawCircle(c, c + 2, r * 0.36f, p);
        Paint mid = new Paint(Paint.ANTI_ALIAS_FLAG);
        mid.setShader(new RadialGradient(c - r * 0.12f, c - r * 0.14f, r * 0.42f, 0xFF6C6C6C, 0xFF262626, Shader.TileMode.CLAMP));
        cv.drawCircle(c, c, r * 0.34f, mid);
        // Beschriftung
        int lab = 0xFFC9C9C9;
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(lab);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(s * 0.075f);
        cv.drawText("OK", c, c + s * 0.027f, t);
        t.setTextSize(s * 0.062f);
        cv.drawText("MENÜ", c, c - r * 0.68f + s * 0.022f, t);
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setColor(lab);
        float u = s * 0.032f, d = r * 0.7f;
        // zurück (links)
        tri(cv, f, c - d + u * 0.2f, c, -u);
        tri(cv, f, c - d + u * 1.3f, c, -u);
        // weiter (rechts)
        tri(cv, f, c + d - u * 0.2f, c, u);
        tri(cv, f, c + d - u * 1.3f, c, u);
        // Abspielen/Pause (unten)
        float y = c + d;
        tri(cv, f, c - u * 0.6f, y, u * 1.2f);
        cv.drawRect(c + u * 0.9f, y - u, c + u * 1.35f, y + u, f);
        cv.drawRect(c + u * 1.65f, y - u, c + u * 2.1f, y + u, f);
        return b;
    }

    /* Dreieck mit Spitze bei x (w < 0: zeigt nach links) */
    private static void tri(Canvas cv, Paint f, float x, float y, float w) {
        Path pa = new Path();
        pa.moveTo(x, y);
        pa.lineTo(x - w, y - Math.abs(w));
        pa.lineTo(x - w, y + Math.abs(w));
        pa.close();
        cv.drawPath(pa, f);
    }
}
