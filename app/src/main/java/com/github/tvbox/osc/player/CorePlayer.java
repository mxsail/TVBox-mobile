package com.github.tvbox.osc.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.SystemClock;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;

import com.github.tvbox.osc.player.bean.Sub;
import com.github.tvbox.osc.player.effect.PlayerEffect;
import com.github.tvbox.osc.player.engine.PlayerEngine;
import com.github.tvbox.osc.player.engine.PlayerEngineFactory;
import com.github.tvbox.osc.player.media.PlaySpec;
import com.github.tvbox.osc.player.setting.PlayerSetting;
import com.github.tvbox.osc.player.setting.SubtitleSetting;
import com.github.tvbox.osc.player.util.PlayerApp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import xyz.doikki.videoplayer.player.AbstractPlayer;

/**
 * 适配器：将 Media3 / MPV 统一的 PlayerEngine 适配到 DKPlayer (VideoView / Controller) 生命周期。
 */
public final class CorePlayer extends AbstractPlayer {

    private final int engineType;
    private PlayerEngine playerEngine;
    private Surface surface;
    private String path;
    private Map<String, String> headers = new HashMap<>();
    private boolean prepared;
    private boolean softwareDecode;
    private File descriptorFile;

    private Consumer<CueGroup> cuesListener;
    private Consumer<CueGroup> renderedCuesListener;
    private Runnable subtitleStyleChanged;
    private float subtitleTextSize;
    private int subtitleTextColor = android.graphics.Color.WHITE;
    private String pendingSubtitleId, pendingSubtitleName;
    private boolean subtitleVisible = true;
    private BooleanSupplier renderSubtitleEnabled = () -> true;

    private long lastRx = -1, lastRxTime;

    public CorePlayer(int legacyType) {
        this.engineType = (legacyType == 1) ? PlayerSetting.ENGINE_MPV : PlayerSetting.ENGINE_EXO;
    }

    public void setSoftwareDecode(boolean value) {
        this.softwareDecode = value;
        if (playerEngine != null) {
            playerEngine.setDecode(softwareDecode ? PlayerEngine.SOFT : PlayerEngine.HARD);
        }
    }

    @Override
    public void initPlayer() {
        PlayerApp.init(com.github.tvbox.osc.base.App.getInstance());
        PlayerSetting.putEngine(engineType);

        int decodeMode = softwareDecode ? PlayerEngine.SOFT : PlayerEngine.HARD;
        PlayerEngine.Type type = (engineType == PlayerSetting.ENGINE_MPV) ? PlayerEngine.Type.MPV : PlayerEngine.Type.EXO;

        playerEngine = PlayerEngineFactory.create(decodeMode, type, listener);
        Player player = playerEngine.getPlayer();
        player.setAudioAttributes(AudioAttributes.DEFAULT, false);
        if (surface != null) {
            player.setVideoSurface(surface);
        }
    }

    public Player player() {
        return playerEngine != null ? playerEngine.getPlayer() : null;
    }

    public PlayerEngine engine() {
        return playerEngine;
    }

    public PlayerEffect effect() {
        return playerEngine != null ? playerEngine.getEffect() : PlayerEffect.NONE;
    }

    public void setCueListener(Consumer<CueGroup> listener) {
        this.cuesListener = listener;
    }

    public void setRenderedCueListener(Consumer<CueGroup> listener) {
        this.renderedCuesListener = listener;
    }

    public void setSubtitleStyleListener(Runnable listener) {
        this.subtitleStyleChanged = listener;
    }

    public float getSubtitleTextSize() {
        return subtitleTextSize;
    }

    public int getSubtitleTextColor() {
        return subtitleTextColor;
    }

    public void setSubtitleStyle(float pixels, int color) {
        this.subtitleTextSize = pixels;
        this.subtitleTextColor = color;
        if (subtitleStyleChanged != null) subtitleStyleChanged.run();
        if (engineType == PlayerSetting.ENGINE_MPV && playerEngine != null) {
            SubtitleSetting.putStyleMode(SubtitleSetting.STYLE_CUSTOM);
            SubtitleSetting.putTextColor(color);
            SubtitleSetting.putScale(pixels / (22 * PlayerApp.get().getResources().getDisplayMetrics().scaledDensity));
            playerEngine.applySubtitleStyle();
        }
    }

    public void setExternalSubtitle(String value) {
        if (value == null || value.isEmpty() || playerEngine == null) return;
        String url = Uri.parse(value).getScheme() == null ? Uri.fromFile(new File(value)).toString() : value;
        boolean playing = isPlaying();
        float speed = getSpeed();
        Sub sub = Sub.from(url);
        pendingSubtitleId = "external:" + UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
        pendingSubtitleName = sub.getName();

        PlaySpec spec = PlaySpec.from("", path, headers, MediaMetadata.EMPTY);
        spec.setSub(sub);
        playerEngine.start(spec, getCurrentPosition());
        setSubtitleEnabled(true);
        if (player() != null) {
            player().setPlaybackSpeed(speed);
            if (!playing) player().pause();
        }
    }

    public void setSubtitleEnabled(boolean enabled) {
        this.subtitleVisible = enabled;
        if (engineType == PlayerSetting.ENGINE_MPV && player() != null) {
            player().setTrackSelectionParameters(
                    player().getTrackSelectionParameters().buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
                            .build()
            );
            if (playerEngine != null) playerEngine.applySubtitleStyle();
        }
        refreshRenderedSubtitles();
    }

    public void setRenderedSubtitleEnabled(BooleanSupplier value) {
        this.renderSubtitleEnabled = value;
        refreshRenderedSubtitles();
    }

    private void refreshRenderedSubtitles() {
        if (renderedCuesListener != null && player() != null) {
            renderedCuesListener.accept(player().getCurrentCues());
        }
    }

    public boolean useRenderedSubtitles() {
        return engineType == PlayerSetting.ENGINE_EXO && subtitleVisible && renderSubtitleEnabled.getAsBoolean();
    }

    public TrackInfo getTrackInfo() {
        TrackInfo info = new TrackInfo();
        if (player() == null) return info;
        int groupIndex = 0;
        for (Tracks.Group group : player().getCurrentTracks().getGroups()) {
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                Format format = group.getTrackFormat(i);
                TrackInfoBean item = new TrackInfoBean();
                item.name = format.label == null ? "轨道 " + (i + 1) : format.label;
                item.language = format.language;
                item.trackId = i;
                item.trackGroupId = groupIndex;
                item.selected = group.isTrackSelected(i);
                if (group.getType() == C.TRACK_TYPE_AUDIO) {
                    info.addAudio(item);
                } else if (group.getType() == C.TRACK_TYPE_TEXT) {
                    info.addSubtitle(item);
                }
            }
            groupIndex++;
        }
        return info;
    }

    public void selectTrack(TrackInfoBean item) {
        if (player() == null) return;
        List<Tracks.Group> groups = player().getCurrentTracks().getGroups();
        if (item.trackGroupId >= groups.size()) return;
        Tracks.Group group = groups.get(item.trackGroupId);
        if (group.getType() == C.TRACK_TYPE_TEXT) {
            pendingSubtitleId = null;
            pendingSubtitleName = null;
        }
        player().setTrackSelectionParameters(
                player().getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(group.getType(), false)
                        .setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(), item.trackId))
                        .build()
        );
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        this.path = path;
        this.headers = headers == null ? new HashMap<>() : new HashMap<>(headers);
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        try {
            descriptorFile = File.createTempFile("local-media-", ".bin", PlayerApp.get().getCacheDir());
            try (InputStream input = fd.createInputStream(); OutputStream output = new FileOutputStream(descriptorFile)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            setDataSource(Uri.fromFile(descriptorFile).toString(), null);
        } catch (Exception failure) {
            throw new IllegalArgumentException("无法读取本地媒体", failure);
        }
    }

    @Override
    public void prepareAsync() {
        prepared = false;
        if (playerEngine == null) {
            initPlayer();
        }
        PlaySpec spec = PlaySpec.from("", path, headers, MediaMetadata.EMPTY);
        playerEngine.start(spec, 0);
    }

    @Override
    public void start() {
        if (player() != null) player().play();
    }

    @Override
    public void pause() {
        if (player() != null) player().pause();
    }

    @Override
    public void stop() {
        if (playerEngine != null) playerEngine.stop();
    }

    @Override
    public void reset() {
        stop();
        prepared = false;
    }

    @Override
    public boolean isPlaying() {
        return player() != null && player().getPlayWhenReady();
    }

    @Override
    public void seekTo(long position) {
        if (player() != null) player().seekTo(Math.max(0, position));
    }

    @Override
    public void release() {
        if (playerEngine != null) {
            playerEngine.stop();
            playerEngine.release();
            playerEngine = null;
        }
        if (descriptorFile != null) {
            descriptorFile.delete();
            descriptorFile = null;
        }
        cuesListener = null;
        renderedCuesListener = null;
        subtitleStyleChanged = null;
        renderSubtitleEnabled = () -> true;
        surface = null;
    }

    @Override
    public long getCurrentPosition() {
        return player() == null ? 0 : player().getCurrentPosition();
    }

    @Override
    public long getDuration() {
        return player() == null || player().getDuration() == C.TIME_UNSET ? 0 : player().getDuration();
    }

    @Override
    public int getBufferedPercentage() {
        return player() == null ? 0 : player().getBufferedPercentage();
    }

    @Override
    public void setSurface(Surface value) {
        this.surface = value;
        if (player() != null) player().setVideoSurface(value);
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        setSurface(holder == null ? null : holder.getSurface());
    }

    @Override
    public void setVolume(float left, float right) {
        if (player() != null) player().setVolume((left + right) / 2);
    }

    @Override
    public void setLooping(boolean looping) {
        if (player() != null) player().setRepeatMode(looping ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    @Override
    public void setOptions() {
    }

    @Override
    public void setSpeed(float speed) {
        if (player() != null) player().setPlaybackSpeed(speed);
    }

    @Override
    public float getSpeed() {
        return player() == null ? 1 : player().getPlaybackParameters().speed;
    }

    @Override
    public long getTcpSpeed() {
        long now = SystemClock.elapsedRealtime();
        long rx = android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid());
        long speed = lastRx < 0 || rx < 0 || now <= lastRxTime ? 0 : Math.max(0, rx - lastRx) * 1000 / (now - lastRxTime);
        lastRx = rx;
        lastRxTime = now;
        return speed;
    }

    private void onTracksChanged() {
        if (pendingSubtitleId == null || player() == null) return;
        for (Tracks.Group group : player().getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);
                if (!pendingSubtitleId.equals(format.id) && !pendingSubtitleName.equals(format.label)) continue;
                pendingSubtitleId = null;
                pendingSubtitleName = null;
                player().setTrackSelectionParameters(
                        player().getTrackSelectionParameters().buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(), i))
                                .build()
                );
                return;
            }
        }
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            CorePlayer.this.onTracksChanged();
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            if (mPlayerEventListener == null) return;
            if (state == Player.STATE_READY) {
                if (!prepared) {
                    prepared = true;
                    mPlayerEventListener.onPrepared();
                }
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, 0);
            } else if (state == Player.STATE_BUFFERING && prepared) {
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
            } else if (state == Player.STATE_ENDED) {
                mPlayerEventListener.onCompletion();
            }
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onError();
            }
        }

        @Override
        public void onRenderedFirstFrame() {
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
            }
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize size) {
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onVideoSizeChanged(size.width, size.height);
            }
        }

        @Override
        public void onCues(@NonNull CueGroup group) {
            if (renderedCuesListener != null) renderedCuesListener.accept(group);
            if (cuesListener != null) cuesListener.accept(group);
        }
    };
}
