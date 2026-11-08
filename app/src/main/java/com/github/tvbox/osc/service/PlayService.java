package com.github.tvbox.osc.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.constant.IntentKey;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HeavyTaskUtil;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.ui.activity.DetailActivity;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import xyz.doikki.videoplayer.player.VideoView;

/**
 * 后台播放前台服务:挂 MediaSession + MediaStyle 通知,
 * 系统媒体卡片(通知栏/快捷设置)展示当前视频标题、集数与播放进度(可拖动)。
 * 控制按钮仍走 VOD_CONTROL 广播,由 DetailActivity/LiveFragment 的接收器驱动播放器,
 * MediaSession.Callback 只是同一套广播的又一入口(蓝牙耳机/媒体按钮走这里)。
 */
public class PlayService extends Service {

    static String videoInfo = "TVBox&&第一集";
    private static VideoView videoView;

    public static void start(VideoView controller, String currentVideoInfo) {
        videoInfo = currentVideoInfo;
        // 点播/直播互斥: 服务切绑到新播放器时停掉旧播放器的声音,
        // 保证任一时刻只有一个播放进程出声
        if (videoView != null && videoView != controller) {
            try {
                videoView.pause();
            } catch (Throwable ignored) {
            }
        }
        PlayService.videoView = controller;
        ContextCompat.startForegroundService(App.getInstance(), new Intent(App.getInstance(), PlayService.class));
    }

    public static void stop() {
        App.getInstance().stopService(new Intent(App.getInstance(), PlayService.class));
    }

    private static final String CHANNEL_ID = "MyChannelId";
    private static final int NOTIFICATION_ID = 1;
    /** 进度心跳:PlaybackState 带速度,系统自动外推位置,1s 刷一次足够 */
    private static final long PROGRESS_INTERVAL_MS = 1000;

    private MediaSessionCompat mediaSession;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private boolean lastNotifyPlaying = false;
    /** 播放内容海报(通知栏大图/媒体卡封面), 异步加载 */
    private android.graphics.Bitmap albumArt;
    private String loadedArtKey = "";

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            // 播放器已随页面销毁(关闭动作会先停服务,这里是兜底),别让通知变成僵尸
            if (videoView == null) {
                stopSelf();
                return;
            }
            updateMediaSession();
            boolean playing = videoView.isPlaying();
            if (playing != lastNotifyPlaying) {
                // 13- 的 MediaStyle 动作图标来自通知本体,播放态变化时重发
                lastNotifyPlaying = playing;
                notifyNotification();
            }
            progressHandler.postDelayed(this, PROGRESS_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "视频后台播放", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }
        mediaSession = new MediaSessionCompat(this, "TVBoxPlayback");
        mediaSession.setActive(true);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                if (videoView != null && !videoView.isPlaying()) sendControl(IntentKey.BROADCAST_ACTION_PLAYPAUSE);
            }

            @Override
            public void onPause() {
                if (videoView != null && videoView.isPlaying()) sendControl(IntentKey.BROADCAST_ACTION_PLAYPAUSE);
            }

            @Override
            public void onSkipToPrevious() {
                sendControl(IntentKey.BROADCAST_ACTION_PREV);
            }

            @Override
            public void onSkipToNext() {
                sendControl(IntentKey.BROADCAST_ACTION_NEXT);
            }

            @Override
            public void onSeekTo(long pos) {
                Intent intent = new Intent(IntentKey.BROADCAST_ACTION)
                        .putExtra("action", IntentKey.BROADCAST_ACTION_SEEK)
                        .putExtra(IntentKey.BROADCAST_ACTION_POSITION, pos)
                        .setPackage(getPackageName());
                sendBroadcast(intent);
            }

            @Override
            public void onStop() {
                sendControl(IntentKey.BROADCAST_ACTION_CLOSE);
                stopSelf();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }
        if (videoView != null) {
            // 服务只是媒体通知载体,起播/续播由页面驱动,这里不再盲目 start():
            // 已 release 的播放器(STATE_IDLE)被 start 会走 startPlay 用旧 mUrl
            // 重建播放器——返回退出后打开直播时,之前的点播就这样在后台复活。
            // 仅恢复暂停态(若存在该场景),空闲态绝不触碰
            if (videoView.getCurrentPlayState() == VideoView.STATE_PAUSED) {
                videoView.start();
            }
            lastNotifyPlaying = videoView.isPlaying();
            updateMediaSession();
            progressHandler.removeCallbacks(progressUpdater);
            progressHandler.postDelayed(progressUpdater, PROGRESS_INTERVAL_MS);
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_REFRESH_NOTIFY) {
            if (event.obj != null) {
                videoInfo = event.obj.toString();
            }
            notifyNotification();
        }
    }

    /** 标题/集数/海报,格式 "剧名&&集名&&海报URL"(海报段可选, 兼容旧两段格式) */
    private String[] videoTitleInfo() {
        String[] parts = (videoInfo == null ? "" : videoInfo).split("&&", 3);
        String title = parts.length > 0 ? parts[0] : "";
        String episode = parts.length > 1 ? parts[1] : "";
        String pic = parts.length > 2 ? parts[2] : "";
        return new String[]{title, episode, pic};
    }

    /** 海报异步加载→通知大图+媒体卡封面; 换集/换剧(URL变化)才重新拉取 */
    private void loadAlbumArt(String pic) {
        if (pic == null || pic.isEmpty()) {
            if (albumArt != null) {
                albumArt = null;
                updateMediaSession();
                notifyNotification();
            }
            return;
        }
        if (pic.equals(loadedArtKey) && albumArt != null) return;
        loadedArtKey = pic;
        final String url = pic;
        HeavyTaskUtil.getBigTaskExecutorService().execute(() -> {
            android.graphics.Bitmap bmp = null;
            try {
                bmp = com.squareup.picasso.Picasso.get().load(DefaultConfig.checkReplaceProxy(url)).get();
            } catch (Throwable th) {
                th.printStackTrace();
            }
            final android.graphics.Bitmap result = bmp;
            progressHandler.post(() -> {
                if (url.equals(loadedArtKey)) {
                    albumArt = result;
                    updateMediaSession();
                    notifyNotification();
                }
            });
        });
    }

    private void updateMediaSession() {
        if (mediaSession == null) return;
        String[] info = videoTitleInfo();
        boolean playing = videoView != null && videoView.isPlaying();
        long position = videoView != null ? Math.max(0, videoView.getCurrentPosition()) : 0;
        long duration = videoView != null ? Math.max(0, videoView.getDuration()) : 0;

        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE
                        | PlaybackStateCompat.ACTION_SEEK_TO
                        | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        | PlaybackStateCompat.ACTION_STOP)
                .setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                        position, playing ? 1.0f : 0f)
                .setBufferedPosition(position)
                .build());
        MediaMetadataCompat.Builder metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, info[0])
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "正在播放: " + info[1])
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        if (albumArt != null) {
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
        }
        mediaSession.setMetadata(metadata.build());
    }

    private Notification buildNotification() {
        String[] info = videoTitleInfo();
        loadAlbumArt(info[2]);
        updateMediaSession();
        boolean playing = videoView != null && videoView.isPlaying();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(info[0])
                .setContentText("正在播放: " + info[1])
                .setLargeIcon(albumArt)
                .setContentIntent(getPendingIntentActivity())
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .addAction(R.drawable.ic_notify_pre, "上一集", getPendingIntent(IntentKey.BROADCAST_ACTION_PREV))
                .addAction(playing ? R.drawable.ic_notify_pause : R.drawable.ic_notify_play,
                        playing ? "暂停" : "播放", getPendingIntent(IntentKey.BROADCAST_ACTION_PLAYPAUSE))
                .addAction(R.drawable.ic_notify_next, "下一集", getPendingIntent(IntentKey.BROADCAST_ACTION_NEXT))
                .addAction(R.drawable.ic_notify_close, "关闭", getPendingIntent(IntentKey.BROADCAST_ACTION_CLOSE))
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession == null ? null : mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        return builder.build();
    }

    private void notifyNotification() {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification());
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private void sendControl(int actionCode) {
        sendBroadcast(new Intent(IntentKey.BROADCAST_ACTION)
                .putExtra("action", actionCode)
                .setPackage(getPackageName()));
    }

    private PendingIntent getPendingIntentActivity() {
        Intent intent = new Intent(this, DetailActivity.class);
        return PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static PendingIntent getPendingIntent(int actionCode) {
        return PendingIntent.getBroadcast(App.getInstance(), actionCode, new Intent(IntentKey.BROADCAST_ACTION).putExtra("action", actionCode).setPackage(App.getInstance().getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onDestroy() {
        progressHandler.removeCallbacksAndMessages(null);
        if (mediaSession != null) {
            try {
                mediaSession.setActive(false);
                mediaSession.release();
            } catch (Throwable ignored) {
            }
            mediaSession = null;
        }
        EventBus.getDefault().unregister(this);
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
