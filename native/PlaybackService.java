package de.bernhard.musik;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.ContentUris;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.media3.common.MediaMetadata;
import org.json.JSONArray;
import org.json.JSONObject;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;

/* Spielt die Musik im Hintergrund. Android zeigt dazu automatisch eine Benachrichtigung
   mit Cover und Tasten, auch im Sperrbildschirm und für Bluetooth-Kopfhörer/Auto. */
public class PlaybackService extends MediaSessionService {
    private MediaSession session;

    @Override
    public void onCreate() {
        super.onCreate();
        ExoPlayer player = new ExoPlayer.Builder(this)
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true) // pausiert bei Anrufen/Navi
            .setHandleAudioBecomingNoisy(true)                            // Kopfhörer raus -> Pause
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build();
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        session = new MediaSession.Builder(this, player)
            .setSessionActivity(pi)
            .setCallback(new MediaSession.Callback() {
                // Die App schickt nur die Adresse der Datei mit, hier wird daraus ein abspielbarer Titel
                @Override
                public ListenableFuture<List<MediaItem>> onAddMediaItems(MediaSession s, MediaSession.ControllerInfo c, List<MediaItem> items) {
                    List<MediaItem> out = new ArrayList<>();
                    for (MediaItem m : items) {
                        Uri u = m.requestMetadata.mediaUri;
                        out.add(u == null ? m : m.buildUpon().setUri(u).build());
                    }
                    return Futures.immediateFuture(out);
                }
            })
            .build();
        // Widget auf dem Startbildschirm aktuell halten
        player.addListener(new Player.Listener() {
            @Override
            public void onEvents(Player p, Player.Events e) { updateWidget(); }
        });
    }

    private String lastWidget = "";

    private void updateWidget() {
        Player p = session == null ? null : session.getPlayer();
        if (p == null) return;
        MediaItem m = p.getCurrentMediaItem();
        String title = m == null || m.mediaMetadata.title == null ? "" : m.mediaMetadata.title.toString();
        String artist = m == null || m.mediaMetadata.artist == null ? "" : m.mediaMetadata.artist.toString();
        long id = -1;
        try { if (m != null) id = Long.parseLong(m.mediaId); } catch (Exception ignore) {}
        boolean playing = p.getPlayWhenReady() && p.getPlaybackState() != Player.STATE_ENDED;
        String key = title + "|" + artist + "|" + playing + "|" + id;
        if (key.equals(lastWidget)) return;
        lastWidget = key;
        if (m != null) prefs().edit().putInt("index", p.getCurrentMediaItemIndex()).apply();
        BtunesWidget.save(this, title, artist, playing, id);
    }

    private SharedPreferences prefs() { return getSharedPreferences(BtunesWidget.PREFS, MODE_PRIVATE); }

    /* Tasten aus dem Widget */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (a != null && a.startsWith("de.bernhard.musik.") && session != null) {
            Player p = session.getPlayer();
            if (p.getMediaItemCount() == 0) restoreQueue(p);
            if (p.getMediaItemCount() > 0) {
                if (BtunesWidget.A_TOGGLE.equals(a)) {
                    if (p.getPlayWhenReady() && p.getPlaybackState() != Player.STATE_ENDED) p.pause();
                    else {
                        if (p.getPlaybackState() == Player.STATE_ENDED) p.seekTo(0, 0);
                        if (p.getPlaybackState() == Player.STATE_IDLE) p.prepare();
                        p.play();
                    }
                } else if (BtunesWidget.A_NEXT.equals(a)) {
                    if (p.hasNextMediaItem()) p.seekToNextMediaItem();
                } else if (BtunesWidget.A_PREV.equals(a)) {
                    if (p.getCurrentPosition() > 3000 || !p.hasPreviousMediaItem()) p.seekTo(0);
                    else p.seekToPreviousMediaItem();
                }
            }
            return START_NOT_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /* Zuletzt aufgelegte Titel wiederherstellen (wenn die App vorher beendet war) */
    private void restoreQueue(Player p) {
        try {
            JSONArray t = new JSONArray(prefs().getString("queue", "[]"));
            List<MediaItem> items = new ArrayList<>();
            for (int i = 0; i < t.length(); i++) {
                JSONObject o = t.getJSONObject(i);
                long id = o.optLong("id");
                Uri u = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                items.add(new MediaItem.Builder().setMediaId(String.valueOf(id)).setUri(u)
                    .setMediaMetadata(new MediaMetadata.Builder().setTitle(o.optString("title"))
                        .setArtist(o.optString("artist")).setAlbumTitle(o.optString("album")).build()).build());
            }
            if (items.isEmpty()) return;
            int idx = Math.max(0, Math.min(prefs().getInt("index", 0), items.size() - 1));
            p.setMediaItems(items, idx, 0);
            p.prepare();
        } catch (Exception ignore) {}
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // App weggewischt: weiterspielen, wenn Musik läuft, sonst beenden
        Player p = session == null ? null : session.getPlayer();
        if (p == null || !p.getPlayWhenReady() || p.getMediaItemCount() == 0) stopSelf();
    }

    @Override
    public void onDestroy() {
        BtunesWidget.save(this, prefs().getString("title", ""), prefs().getString("artist", ""), false, prefs().getLong("track", -1L));
        if (session != null) {
            session.getPlayer().release();
            session.release();
            session = null;
        }
        super.onDestroy();
    }
}
