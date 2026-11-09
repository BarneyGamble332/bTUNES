package de.bernhard.musik;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
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
        if (session != null) {
            session.getPlayer().release();
            session.release();
            session = null;
        }
        super.onDestroy();
    }
}
