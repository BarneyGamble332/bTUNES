package de.bernhard.musik;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;

/* Termin-Erinnerungen: zeigt zur eingestellten Zeit eine Benachrichtigung,
   auch wenn die App geschlossen ist. Nach einem Neustart des Handys werden die Wecker neu gestellt. */
public class ReminderReceiver extends BroadcastReceiver {
    static final String PREFS = "btunes_reminders";
    static final String CHANNEL = "termine";

    @Override
    public void onReceive(Context ctx, Intent in) {
        String a = in.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || "android.intent.action.MY_PACKAGE_REPLACED".equals(a)) {
            try { schedule(ctx, new JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "[]"))); } catch (Exception ignore) {}
            return;
        }
        show(ctx, in.getIntExtra("id", 1), in.getStringExtra("title"), in.getStringExtra("text"));
    }

    static void show(Context ctx, int id, String title, String text) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Termine", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Erinnerungen an Termine aus dem Orga-Kalender");
            nm.createNotificationChannel(ch);
        }
        Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        PendingIntent pi = PendingIntent.getActivity(ctx, 20, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif_cal)
            .setContentTitle(title == null ? "Termin" : title)
            .setContentText(text == null ? "" : text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi);
        try { nm.notify(id, b.build()); } catch (SecurityException ignore) {}
    }

    /* Alle Erinnerungen neu stellen (alte werden vorher gelöscht) */
    static void schedule(Context ctx, JSONArray list) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONArray old = new JSONArray(p.getString("ids", "[]"));
            for (int i = 0; i < old.length(); i++) am.cancel(pending(ctx, old.getInt(i), null, null));
        } catch (Exception ignore) {}
        JSONArray ids = new JSONArray();
        long now = System.currentTimeMillis();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            long at = o.optLong("at");
            if (at <= now) continue;
            int id = o.optInt("id");
            PendingIntent pi = pending(ctx, id, o.optString("title"), o.optString("text"));
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            try {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
            ids.put(id);
        }
        p.edit().putString("list", list.toString()).putString("ids", ids.toString()).apply();
    }

    private static PendingIntent pending(Context ctx, int id, String title, String text) {
        Intent i = new Intent(ctx, ReminderReceiver.class).setAction("de.bernhard.musik.REMIND")
            .putExtra("id", id).putExtra("title", title).putExtra("text", text);
        return PendingIntent.getBroadcast(ctx, id, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
