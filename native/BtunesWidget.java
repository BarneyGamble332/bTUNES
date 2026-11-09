package de.bernhard.musik;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;
import android.widget.RemoteViews;

/* Startbildschirm-Widget: kleine Schallplatte mit dem Albumcover als Etikett,
   Titel, Interpret und Tasten Zurück / Abspielen-Pause / Weiter */
public class BtunesWidget extends AppWidgetProvider {
    static final String PREFS = "btunes_widget";
    static final String A_TOGGLE = "de.bernhard.musik.TOGGLE";
    static final String A_NEXT = "de.bernhard.musik.NEXT";
    static final String A_PREV = "de.bernhard.musik.PREV";

    private static String coverFor = "";
    private static Bitmap record;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        refreshAsync(ctx);
    }

    /* Aktuellen Stand merken und Widget neu zeichnen (Cover wird im Hintergrund geladen) */
    static void save(Context ctx, String title, String artist, boolean playing, long trackId, String art, String next) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("next", next)
            .putString("title", title).putString("artist", artist).putString("art", art)
            .putBoolean("playing", playing).putLong("track", trackId).apply();
        refreshAsync(ctx);
    }

    static void refreshAsync(Context ctx) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try { refresh(app); } catch (Exception ignore) {}
        }).start();
    }

    private static synchronized void refresh(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, BtunesWidget.class));
        if (ids == null || ids.length == 0) return;
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String title = p.getString("title", "");
        String artist = p.getString("artist", "");
        boolean playing = p.getBoolean("playing", false);
        long track = p.getLong("track", -1L);
        String art = p.getString("art", "");
        String coverKey = track + "|" + art;

        if (record == null || !coverKey.equals(coverFor)) {
            record = drawRecord(ctx, track, art);
            coverFor = coverKey;
        }

        String next = p.getString("next", "");
        RemoteViews big = views(ctx, R.layout.btunes_widget, title, artist, playing);
        big.setTextViewText(R.id.w_next_t, next.isEmpty() ? "" : "Als Nächstes: " + next);
        big.setViewVisibility(R.id.w_next_t, next.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        RemoteViews flat = views(ctx, R.layout.btunes_widget_small, title, artist, playing);
        RemoteViews v;
        if (Build.VERSION.SDK_INT >= 31) {
            // Android wählt je nach Widget-Größe selbst: flach (1 Zeile) oder groß
            java.util.Map<android.util.SizeF, RemoteViews> m = new java.util.HashMap<>();
            m.put(new android.util.SizeF(180f, 40f), flat);
            m.put(new android.util.SizeF(200f, 140f), big);
            v = new RemoteViews(m);
        } else {
            v = big;
        }
        mgr.updateAppWidget(ids, v);
    }

    private static RemoteViews views(Context ctx, int layout, String title, String artist, boolean playing) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), layout);
        v.setTextViewText(R.id.w_title, title.isEmpty() ? "bTUNES" : title);
        v.setTextViewText(R.id.w_artist, artist.isEmpty() ? "Leg eine Platte auf" : artist);
        v.setImageViewBitmap(R.id.w_record, record);
        v.setImageViewResource(R.id.w_play, playing ? R.drawable.ic_w_pause : R.drawable.ic_w_play);
        Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        PendingIntent po = PendingIntent.getActivity(ctx, 10, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.w_record, po);
        v.setOnClickPendingIntent(R.id.w_text, po);
        v.setOnClickPendingIntent(R.id.w_play, action(ctx, A_TOGGLE, 11));
        v.setOnClickPendingIntent(R.id.w_next, action(ctx, A_NEXT, 12));
        v.setOnClickPendingIntent(R.id.w_prev, action(ctx, A_PREV, 13));
        return v;
    }

    private static PendingIntent action(Context ctx, String a, int code) {
        Intent i = new Intent(ctx, BtunesWidget.class).setAction(a);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /* Tastendruck im Widget an den Wiedergabe-Dienst weitergeben */
    @Override
    public void onReceive(Context ctx, Intent in) {
        String a = in.getAction();
        if (A_TOGGLE.equals(a) || A_NEXT.equals(a) || A_PREV.equals(a)) {
            Intent s = new Intent(ctx, PlaybackService.class).setAction(a);
            try {
                ctx.startService(s);
            } catch (Exception e) {
                // Dienst läuft nicht und die App ist im Hintergrund: nur zum Abspielen als Vordergrunddienst starten
                boolean hasQueue = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("queue", null) != null;
                if (A_TOGGLE.equals(a) && hasQueue && Build.VERSION.SDK_INT >= 26) {
                    try { ctx.startForegroundService(s); } catch (Exception ignore) {}
                }
            }
            return;
        }
        super.onReceive(ctx, in);
    }

    /* Schallplatte zeichnen: Rillen, Glanz, rundes Cover als Etikett */
    private static Bitmap drawRecord(Context ctx, long track, String artUrl) {
        int s = 300;
        float c = s / 2f, r = s / 2f - 2;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        pt.setColor(0xFF0D0D0D);
        cv.drawCircle(c, c, r, pt);
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(1.4f);
        int k = 0;
        for (float rr = r - 4; rr > r * 0.66f; rr -= 3.2f, k++) {
            pt.setColor(k % 7 == 3 ? 0xFF2A2A2A : 0xFF1B1B1B);
            cv.drawCircle(c, c, rr, pt);
        }
        pt.setStyle(Paint.Style.FILL);
        // Lichtreflex
        Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setShader(new LinearGradient(0, 0, s, s, new int[]{0x00FFFFFF, 0x22FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x18FFFFFF, 0x00FFFFFF},
            new float[]{0.15f, 0.3f, 0.42f, 0.58f, 0.7f, 0.85f}, Shader.TileMode.CLAMP));
        cv.drawCircle(c, c, r, sh);

        float lr = r * 0.62f;
        Bitmap art = null;
        if (track >= 0 && Build.VERSION.SDK_INT >= 29) {
            try {
                art = ctx.getContentResolver().loadThumbnail(
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track), new Size(300, 300), null);
            } catch (Exception ignore) {}
        }
        if (art == null && artUrl != null && artUrl.startsWith("http")) {
            // Podcast: Bild aus dem Internet
            try {
                java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(artUrl).openConnection();
                con.setConnectTimeout(8000); con.setReadTimeout(8000); con.setInstanceFollowRedirects(true);
                try (java.io.InputStream in = con.getInputStream()) {
                    android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                    o.inSampleSize = 2;
                    art = android.graphics.BitmapFactory.decodeStream(in, null, o);
                }
            } catch (Exception ignore) {}
        }
        Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (art != null) {
            BitmapShader bs = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float sc = (2 * lr) / Math.min(art.getWidth(), art.getHeight());
            Matrix m = new Matrix();
            m.setScale(sc, sc);
            m.postTranslate(c - art.getWidth() * sc / 2f, c - art.getHeight() * sc / 2f);
            bs.setLocalMatrix(m);
            lp.setShader(bs);
        } else {
            lp.setShader(new LinearGradient(0, c - lr, 0, c + lr, 0xFFF2B65D, 0xFFC9762E, Shader.TileMode.CLAMP));
        }
        cv.drawCircle(c, c, lr, lp);
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setShader(new RadialGradient(c, c, lr, new int[]{0x00000000, 0x00000000, 0x44000000}, new float[]{0f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        cv.drawCircle(c, c, lr, rim);
        pt.setColor(0xFFDDDDDD);
        cv.drawCircle(c, c, s * 0.022f, pt);
        pt.setColor(0xFF444444);
        cv.drawCircle(c, c, s * 0.01f, pt);
        return b;
    }
}
