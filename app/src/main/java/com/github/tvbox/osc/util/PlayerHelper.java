package com.github.tvbox.osc.util;

import android.app.Activity;
import android.content.Context;

import com.github.tvbox.osc.player.CorePlayer;
import com.github.tvbox.osc.player.render.SurfaceRenderViewFactory;
import com.github.tvbox.osc.player.thirdparty.Kodi;
import com.github.tvbox.osc.player.thirdparty.MXPlayer;
import com.github.tvbox.osc.player.thirdparty.ReexPlayer;
import com.github.tvbox.osc.player.thirdparty.RemoteTVBox;
import com.github.tvbox.osc.player.thirdparty.VlcPlayer;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;

import xyz.doikki.videoplayer.player.PlayerFactory;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.render.RenderViewFactory;
import xyz.doikki.videoplayer.render.TextureRenderViewFactory;

public class PlayerHelper {
    public static final int RENDER_TEXTURE_VIEW = 0;
    public static final int RENDER_SURFACE_VIEW = 1;

    public static ArrayList<Integer> getRenderTypes() {
        ArrayList<Integer> renderTypes = new ArrayList<>();
        renderTypes.add(RENDER_TEXTURE_VIEW);
        renderTypes.add(RENDER_SURFACE_VIEW);
        return renderTypes;
    }

    public static void updateCfg(VideoView videoView, JSONObject playerCfg) {
        int playerType = Hawk.get(HawkConfig.PLAY_TYPE, 0);
        int renderType = Hawk.get(HawkConfig.PLAY_RENDER, RENDER_TEXTURE_VIEW);
        String ijkCode = Hawk.get(HawkConfig.IJK_CODEC, "软解码");
        int scale = Hawk.get(HawkConfig.PLAY_SCALE, 0);
        try {
            if (playerCfg.has("pl")) playerType = playerCfg.getInt("pl");
            if (playerCfg.has("pr")) renderType = playerCfg.getInt("pr");
            if (playerCfg.has("ijk")) ijkCode = playerCfg.getString("ijk");
            if (playerCfg.has("sc")) scale = playerCfg.getInt("sc");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        final int selectedType = playerType;
        final boolean softwareDecode = ijkCode != null && ijkCode.contains("软");

        PlayerFactory playerFactory = new PlayerFactory<CorePlayer>() {
            @Override
            public CorePlayer createPlayer(Context context) {
                CorePlayer player = new CorePlayer(selectedType);
                player.setSoftwareDecode(softwareDecode);
                return player;
            }
        };

        RenderViewFactory renderViewFactory;
        switch (renderType) {
            case RENDER_SURFACE_VIEW:
                renderViewFactory = SurfaceRenderViewFactory.create();
                break;
            case RENDER_TEXTURE_VIEW:
            default:
                renderViewFactory = TextureRenderViewFactory.create();
                break;
        }

        videoView.setPlayerFactory(playerFactory);
        videoView.setRenderViewFactory(renderViewFactory);
        videoView.setScreenScaleType(scale);
    }

    public static void updateCfg(VideoView videoView) {
        updateCfg(videoView, new JSONObject());
    }

    public static void init() {
    }

    public static String getPlayerName(int playType) {
        HashMap<Integer, String> playersInfo = getPlayersInfo();
        if (playersInfo.containsKey(playType)) {
            return playersInfo.get(playType);
        } else {
            return "Media3";
        }
    }

    private static HashMap<Integer, String> mPlayersInfo = null;
    public static HashMap<Integer, String> getPlayersInfo() {
        if (mPlayersInfo == null) {
            HashMap<Integer, String> playersInfo = new HashMap<>();
            playersInfo.put(0, "Media3");
            playersInfo.put(1, "MPV");
            playersInfo.put(2, "Media3");
            playersInfo.put(10, "MX播放器");
            playersInfo.put(11, "Reex播放器");
            playersInfo.put(12, "Kodi播放器");
            playersInfo.put(13, "附近TVBox");
            playersInfo.put(14, "VLC播放器");
            mPlayersInfo = playersInfo;
        }
        return mPlayersInfo;
    }

    private static HashMap<Integer, Boolean> mPlayersExistInfo = null;
    public static HashMap<Integer, Boolean> getPlayersExistInfo() {
        if (mPlayersExistInfo == null) {
            HashMap<Integer, Boolean> playersExist = new HashMap<>();
            playersExist.put(0, true);
            playersExist.put(1, true);
            playersExist.put(2, false);
            playersExist.put(10, MXPlayer.getPackageInfo() != null);
            playersExist.put(11, ReexPlayer.getPackageInfo() != null);
            playersExist.put(12, Kodi.getPackageInfo() != null);
            playersExist.put(13, RemoteTVBox.getAvalible() != null);
            playersExist.put(14, VlcPlayer.getPackageInfo() != null);
            mPlayersExistInfo = playersExist;
        }
        return mPlayersExistInfo;
    }

    public static Boolean getPlayerExist(int playType) {
        HashMap<Integer, Boolean> playersExistInfo = getPlayersExistInfo();
        if (playersExistInfo.containsKey(playType)) {
            return playersExistInfo.get(playType);
        } else {
            return false;
        }
    }

    public static ArrayList<Integer> getExistPlayerTypes() {
        ArrayList<Integer> existPlayerTypes = new ArrayList<>();
        HashMap<Integer, Boolean> playersExistInfo = getPlayersExistInfo();
        for (int playType : playersExistInfo.keySet()) {
            if (playersExistInfo.get(playType)) {
                existPlayerTypes.add(playType);
            }
        }
        return existPlayerTypes;
    }

    public static ArrayList<Integer> getAllPlayerTypes() {
        ArrayList<Integer> allPlayerTypes = new ArrayList<>();
        allPlayerTypes.add(0);
        allPlayerTypes.add(1);
        allPlayerTypes.add(10);
        allPlayerTypes.add(11);
        allPlayerTypes.add(12);
        allPlayerTypes.add(13);
        allPlayerTypes.add(14);
        return allPlayerTypes;
    }

    public static String getScaleName(int scaleType) {
        switch (scaleType) {
            case 1:
                return "16:9";
            case 2:
                return "4:3";
            case 3:
                return "填充";
            case 4:
                return "原始";
            case 5:
                return "裁剪";
            default:
                return "默认";
        }
    }

    public static String getRenderName(int renderType) {
        switch (renderType) {
            case RENDER_SURFACE_VIEW:
                return "SurfaceView";
            case RENDER_TEXTURE_VIEW:
            default:
                return "TextureView";
        }
    }

    public static ArrayList<Integer> getAllScaleTypes() {
        ArrayList<Integer> allScaleTypes = new ArrayList<>();
        allScaleTypes.add(0);
        allScaleTypes.add(1);
        allScaleTypes.add(2);
        allScaleTypes.add(3);
        allScaleTypes.add(4);
        allScaleTypes.add(5);
        return allScaleTypes;
    }

    public static String getSpeedName(float speed) {
        DecimalFormat df = new DecimalFormat("0.0#");
        return df.format(speed) + "x";
    }

    public static String getDisplaySpeed(long speed) {
        if (speed <= 0) return "";
        if (speed >= 1024 * 1024) {
            return String.format(java.util.Locale.getDefault(), "%.2f MB/s", speed / (1024f * 1024f));
        } else if (speed >= 1024) {
            return String.format(java.util.Locale.getDefault(), "%.2f KB/s", speed / 1024f);
        } else {
            return speed + " B/s";
        }
    }

    public static boolean runExternalPlayer(int playType, Activity activity, String url, String title, String subtitle, HashMap<String, String> headers, long progress) {
        switch (playType) {
            case 10:
                return MXPlayer.run(activity, url, title, subtitle, headers);
            case 11:
                return ReexPlayer.run(activity, url, title, subtitle, headers);
            case 12:
                return Kodi.run(activity, url, title, subtitle, headers);
            case 13:
                return RemoteTVBox.run(activity, url, title, subtitle, headers);
            case 14:
                return VlcPlayer.run(activity, url, title, subtitle, progress);
            default:
                return false;
        }
    }

    public static boolean runThirdPlayer(int playType, Activity activity, String url, String title, JSONObject playHeader, long progress) {
        HashMap<String, String> headers = new HashMap<>();
        if (playHeader != null) {
            java.util.Iterator<String> keys = playHeader.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                headers.put(key, playHeader.optString(key));
            }
        }
        return runExternalPlayer(playType, activity, url, title, null, headers, progress);
    }
}
