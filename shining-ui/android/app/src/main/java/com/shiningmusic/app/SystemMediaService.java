package com.shiningmusic.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Android media session + MediaStyle notification for the existing WebView player. */
public class SystemMediaService extends Service {
    static final String ACTION_UPDATE = "com.shiningmusic.app.media.UPDATE";
    private static final String ACTION_PREVIOUS = "com.shiningmusic.app.media.PREVIOUS";
    private static final String ACTION_TOGGLE = "com.shiningmusic.app.media.TOGGLE";
    private static final String ACTION_NEXT = "com.shiningmusic.app.media.NEXT";
    private static final String CHANNEL_ID = "shining_music_playback";
    private static final int NOTIFICATION_ID = 1201;
    private static volatile boolean running;

    private MediaSession session;
    private NotificationManager notifications;
    private boolean foreground;
    private boolean playing;
    private long positionMs;
    private long durationMs;
    private String title = "Shining Music";
    private String artist = "";
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()) && playing) {
                // A wired headset was unplugged or the Bluetooth audio route was lost.
                SystemMediaPlugin.sendCommand("pause", 0);
            }
        }
    };

    static boolean isRunning() { return running; }

    @Override public void onCreate() {
        super.onCreate();
        notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "音乐播放",
                NotificationManager.IMPORTANCE_LOW);
            notifications.createNotificationChannel(channel);
        }
        session = new MediaSession(this, "Shining Music");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setSessionActivity(PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { SystemMediaPlugin.sendCommand("play", 0); }
            @Override public void onPause() { SystemMediaPlugin.sendCommand("pause", 0); }
            @Override public void onSkipToNext() { SystemMediaPlugin.sendCommand("next", 0); }
            @Override public void onSkipToPrevious() { SystemMediaPlugin.sendCommand("previous", 0); }
            @Override public void onSeekTo(long pos) { SystemMediaPlugin.sendCommand("seek", pos); }
        }, new Handler(Looper.getMainLooper()));
        session.setActive(true);
        registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        running = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_PREVIOUS.equals(action)) {
            SystemMediaPlugin.sendCommand("previous", 0);
            return START_NOT_STICKY;
        }
        if (ACTION_NEXT.equals(action)) {
            SystemMediaPlugin.sendCommand("next", 0);
            return START_NOT_STICKY;
        }
        if (ACTION_TOGGLE.equals(action)) {
            SystemMediaPlugin.sendCommand(playing ? "pause" : "play", 0);
            return START_NOT_STICKY;
        }
        if (!ACTION_UPDATE.equals(action)) return START_NOT_STICKY;

        title = intent.getStringExtra("title");
        if (title == null || title.isEmpty()) title = "Shining Music";
        artist = intent.getStringExtra("artist");
        if (artist == null) artist = "";
        durationMs = Math.max(0, intent.getLongExtra("durationMs", 0));
        positionMs = Math.max(0, intent.getLongExtra("positionMs", 0));
        playing = intent.getBooleanExtra("playing", false);

        session.setMetadata(new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            .build());
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
            PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT |
            PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO;
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(actions)
            .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                positionMs, playing ? 1.0f : 0.0f)
            .build());

        Notification notification = notification();
        if (!foreground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            foreground = true;
        } else {
            notifications.notify(NOTIFICATION_ID, notification);
        }
        return START_NOT_STICKY;
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        return PendingIntent.getService(this, requestCode,
            new Intent(this, SystemMediaService.class).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification notification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOngoing(playing)
            .addAction(android.R.drawable.ic_media_previous, "上一首", actionIntent(ACTION_PREVIOUS, 1))
            .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "暂停" : "播放", actionIntent(ACTION_TOGGLE, 2))
            .addAction(android.R.drawable.ic_media_next, "下一首", actionIntent(ACTION_NEXT, 3))
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
            .build();
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        stopSelf(); // There is no independent native audio engine after WebView is removed.
    }

    @Override public void onDestroy() {
        running = false;
        unregisterReceiver(noisyReceiver);
        if (session != null) {
            session.setActive(false);
            session.release();
        }
        if (foreground) stopForeground(true);
        if (notifications != null) notifications.cancel(NOTIFICATION_ID);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
