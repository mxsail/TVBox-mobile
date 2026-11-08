package com.github.tvbox.osc.ui.fragment;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;

import com.blankj.utilcode.util.ColorUtils;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.RegexUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.blankj.utilcode.util.SpanUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.Subtitle;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.CacheManager;
import com.github.tvbox.osc.cache.DownloadEpisode;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.tvbox.sync.SyncTrigger;
import com.tvbox.sync.adapter.AppRoomSyncAdapter;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.EXOmPlayer;
import com.github.tvbox.osc.player.IjkMediaPlayer;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.player.TrackInfo;
import com.github.tvbox.osc.player.TrackInfoBean;
import com.github.tvbox.osc.player.controller.VodController;
import com.github.tvbox.osc.server.RemoteServer;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.adapter.ParseAdapter;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.PlayingControlDialog;
import com.github.tvbox.osc.ui.dialog.PlayingControlRightDialog;
import com.github.tvbox.osc.ui.dialog.SearchSubtitleDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.SubtitleDialog;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.VideoParseRuler;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.text.Cue;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.enums.PopupPosition;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.HttpHeaders;
import com.lzy.okgo.model.Response;
import com.obsez.android.lib.filechooser.ChooserDialog;
import com.orhanobut.hawk.Hawk;

import org.apache.commons.lang3.StringUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import me.jessyan.autosize.AutoSize;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;
import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.ProgressManager;
import xyz.doikki.videoplayer.player.VideoView;

public class PlayFragment extends BaseLazyFragment {
    private MyVideoView mVideoView;
    private TextView mPlayLoadTip;
    private ImageView mPlayLoadErr;
    private View mPlayLoading;
    private VodController mController;
    private SourceViewModel sourceViewModel;
    private Handler mHandler;

    private final long videoDuration = -1;
    /**
     * 记录当前播放url
     */
    private String mCurrentUrl;
    private boolean mFullWindows;
    /**
     * 非全屏下的设置弹窗
     */
    private BasePopupView mPlayingControlDialog;
    /**
     * 全屏下的设置弹窗
     */
    private BasePopupView mPlayingControlRightDialog;
    /**
     * 视频播放出错时,自动切换另一个播放器;按"当前集进度键"记录,
     * 换集后重新允许自动切换(旧的全局布尔导致首集切换过后续集永不切换)
     */
    String retriedSwitchPlayerKey = "";
    @Override
    protected int getLayoutResID() {
        return R.layout.activity_play;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SUBTITLE_SIZE_CHANGE) {
            mController.mSubtitleView.setTextSize((int) event.obj);
        } else if (event.type == RefreshEvent.TYPE_BATTERY_CHANGE && mController.mMyBatteryView!=null){
            mController.mMyBatteryView.updateBattery((int) event.obj);
        }
    }

    @Override
    protected void init() {
        initView();
        initViewModel();
        initData();
        registerNetworkCallback();
    }

    public long getSavedProgress(String url) {
        int st = 0;
        try {
            st = mVodPlayerCfg.getInt("st");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        long skip = st * 1000L;
        Object theCache=CacheManager.getCache(MD5.string2MD5(url));
        if (theCache == null) {
            return skip;
        }
        long rec = 0;
        if (theCache instanceof Long) {
            rec = (Long) theCache;
        } else if (theCache instanceof String) {
            try {
                rec = Long.parseLong((String) theCache);
            } catch (NumberFormatException e) {
                System.out.println("String value is not a valid long.");
            }
        } else {
            System.out.println("Value cannot be converted to long.");
        }
        return Math.max(rec, skip);
    }

    private void initView() {
        EventBus.getDefault().register(this);
        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case 100:
                        stopParse();
                        // 嗅探已命中并起播后残留的过期定时器: 真正在播/已就绪时忽略,
                        // 否则会误报"嗅探错误"并把正在播放的视频重新加载一遍
                        int st100 = mVideoView != null ? mVideoView.getCurrentPlayState() : -1;
                        boolean alreadyPlaying = st100 == VideoView.STATE_PLAYING
                                || st100 == VideoView.STATE_PAUSED
                                || st100 == VideoView.STATE_PREPARED
                                || st100 == VideoView.STATE_BUFFERED;
                        if (!alreadyPlaying) {
                            errorWithRetry("嗅探错误", false);
                        }
                        break;
                }
                return false;
            }
        });
        mVideoView = findViewById(R.id.mVideoView);
        // 预览小窗强制 TextureView 渲染: SurfaceView 尺寸变化/全屏切换会销毁重建 surface,
        // 解码器可能失联(MediaCodec -19 dec surface not connected)→ 有声无画持续加载。
        // TextureView 是普通 View, 生命周期平滑; 预览小窗体积下性能无差。
        mVideoView.setRenderViewFactory(xyz.doikki.videoplayer.render.TextureRenderViewFactory.create());
        mPlayLoadTip = findViewById(R.id.play_load_tip);
        mPlayLoading = findViewById(R.id.play_loading);
        mPlayLoadErr = findViewById(R.id.play_load_error);
        mController = new VodController(requireContext());
        mController.showParse(false);
        mController.setCanChangePosition(true);
        mController.setEnableInNormal(true);
        mController.setGestureEnabled(true);
        ProgressManager progressManager = new ProgressManager() {
            @Override
            public void saveProgress(String url, long progress) {
                if (Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) return;
                String key = MD5.string2MD5(url);
                CacheManager.save(key, progress);
                AppRoomSyncAdapter.recordProgressUpsert(key, progress, mVideoView != null ? mVideoView.getDuration() : 0);
            }

            @Override
            public long getSavedProgress(String url) {
                return PlayFragment.this.getSavedProgress(url);
            }
        };
        mVideoView.setProgressManager(progressManager);
        mController.setListener(new VodController.VodControlListener() {
            final DetailActivity activity = (DetailActivity) mActivity;
            @Override
            public void chooseSeries() {
                //activity中已处理
                activity.showAllSeriesDialog();
            }

            @Override
            public void playNext(boolean rmProgress) {
                String preProgressKey = progressKey;
                PlayFragment.this.playNext(rmProgress);
                if (rmProgress && preProgressKey != null) {
                    String pKey = MD5.string2MD5(preProgressKey);
                    CacheManager.delete(pKey, 0);
                    AppRoomSyncAdapter.recordProgressDelete(pKey);
                }
            }

            @Override
            public void playPre() {
                PlayFragment.this.playPrevious();
            }

            @Override
            public void changeParse(ParseBean pb) {
                autoRetryCount = 0;
                doParse(pb);
            }

            @Override
            public void updatePlayerCfg() {
                mVodInfo.playerCfg = mVodPlayerCfg.toString();
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodPlayerCfg));
            }

            @Override
            public void replay(boolean replay) {
                autoRetryCount = 0;
                play(replay);
            }

            @Override
            public void errReplay() {
                errorWithRetry("视频播放出错", false);
            }

            @Override
            public void selectSubtitle() {
                try {
                    selectMySubtitle();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void selectAudioTrack() {
                selectMyAudioTrack();
            }

            @Override
            public void prepared() {
                initSubtitleView();
            }

            @Override
            public void toggleFullScreen() {
                activity.toggleFullPreview();
            }

            @Override
            public void exit() {
                // 走 dispatcher(等价旧 activity.onBackPressed(), 兼容预见性返回链路)
                ((com.github.tvbox.osc.base.BaseActivity) activity).getOnBackPressedDispatcher().onBackPressed();
            }

            @Override
            public void cast() {
                activity.showCastDialog();
            }

            @Override
            public void onHideBottom() {
                if (mFullWindows){
                    ImmersionBar.with(activity)
                            .hideBar(BarHide.FLAG_HIDE_BAR)
                            .init();
                }
            }

            @Override
            public void showSetting() {
                if (mFullWindows){
                    mPlayingControlRightDialog = new XPopup.Builder(activity).hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
                            .isViewMode(true)//改为view模式无法自动响应返回键操作,onBackPress时手动dismiss
                            .popupHeight(ScreenUtils.getScreenHeight())
                            .popupPosition(PopupPosition.Right)
                            .asCustom(new PlayingControlRightDialog(activity,mController,mVideoView));
                    mPlayingControlRightDialog.show();
                }else {
                    mPlayingControlDialog = new XPopup.Builder(activity).hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
                            .isViewMode(true)
                            .asCustom(new PlayingControlDialog(activity,mController,mVideoView));
                    mPlayingControlDialog.show();
                }
            }

            @Override
            public void pip() {
                activity.enterPip();
            }

            @Override
            public void showParseRoot(boolean show, ParseAdapter adapter) {
                DetailActivity activity = (DetailActivity)mActivity;
                activity.showParseRoot(show,adapter);
            }
        });
        mVideoView.setVideoController(mController);
        // 下载门控:首帧真正开始播放时通知详情页,放行暂存的缓存任务(见 DetailActivity.queueDownloadAfterPlay)
        mVideoView.addOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int playState) {
                // 返回退出后 onDestroyView 已置空 mVideoView, ExoPlayer 已排队的事件仍会
                // 经 controller 派发到这里(mActivity 引用还在), 直接 NPE
                if (mVideoView == null || !isAdded()) return;
                if (mActivity instanceof DetailActivity) {
                    if (playState == VideoView.STATE_PLAYING) {
                        // 真正出画面/出声时强制清掉残留的加载提示
                        // (起播链路的 hideTip 若被后续 setTip 覆盖, 声音已响而 UI 停在"加载")
                        hideTip();
                        ((DetailActivity) mActivity).onPlaybackStarted();
                    } else {
                        ((DetailActivity) mActivity).updatePipParams();
                    }
                    // 预览窗固定 16:9(宽=屏宽), 不随视频比例动态调整:
                    // 主流播放应用的标准做法(YT/B站/长视频 App), 非 16:9 内容在容器内
                    // 等比 letterbox; 动态 resize 对 SurfaceView 有撕 surface 风险
                }
            }
        });
    }

    public boolean hideAllDialogSuccess(){
        if (mPlayingControlRightDialog!=null && mPlayingControlRightDialog.isShow()){
            mPlayingControlRightDialog.dismiss();
            return true;
        }
        if (mPlayingControlDialog!=null && mPlayingControlDialog.isShow()){
            mPlayingControlDialog.dismiss();
            return true;
        }
        return false;
    }

    /**
     * activity返回/点击播放器切换全屏操作等
     */
    public void changedLandscape(boolean fullWindows) {
        mFullWindows = fullWindows;
        if (fullWindows){
            int[] size = mVideoView.getVideoSize();
            int width = size[0];
            int height = size[1];
            if (width>height){//根据视频尺寸判断是否横屏,小视频则只在activity改了预览尺寸(全屏预览)
                //横屏(传感器)
                mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }

            ImmersionBar.with(mActivity)
                    .hideBar(BarHide.FLAG_HIDE_BAR)
                    .navigationBarColor(R.color.black)//即使隐藏部分时候还是会显示
                    .fitsSystemWindows(false)
                    .init();
        }else {//非全屏统一设置竖屏,activity处理为小的预览尺寸
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            ImmersionBar.with(mActivity)
                    .hideBar(BarHide.FLAG_SHOW_BAR)
                    .navigationBarColor(android.R.color.transparent)
                    .fitsSystemWindows(true)
                    .init();
        }

        mController.changedLandscape(fullWindows);
    }

    //设置字幕
    void setSubtitle(String path) {
        if (path != null && path.length() > 0) {
            // 设置字幕
            mController.mSubtitleView.setVisibility(View.GONE);
            mController.mSubtitleView.setSubtitlePath(path);
            mController.mSubtitleView.setVisibility(View.VISIBLE);
        }
    }

    void selectMySubtitle() throws Exception {
        SubtitleDialog subtitleDialog = new SubtitleDialog(getActivity());
        subtitleDialog.setSubtitleViewListener(new SubtitleDialog.SubtitleViewListener() {
            @Override
            public void setTextSize(int size) {
                mController.mSubtitleView.setTextSize(size);
            }

            @Override
            public void setSubtitleDelay(int milliseconds) {
                mController.mSubtitleView.setSubtitleDelay(milliseconds);
            }

            @Override
            public void selectInternalSubtitle() {
                selectMyInternalSubtitle();
            }

            @Override
            public void setTextStyle(int style) {
                setSubtitleViewTextStyle(style);
            }

            @Override
            public void subtitleOpen(boolean b) {
                mController.openSubtitle(b);
            }
        });
        subtitleDialog.setSearchSubtitleListener(new SubtitleDialog.SearchSubtitleListener() {
            @Override
            public void openSearchSubtitleDialog() {
                SearchSubtitleDialog searchSubtitleDialog = new SearchSubtitleDialog(getActivity());
                searchSubtitleDialog.setSubtitleLoader(new SearchSubtitleDialog.SubtitleLoader() {
                    @Override
                    public void loadSubtitle(Subtitle subtitle) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String zimuUrl = subtitle.getUrl();
                                LOG.i("Remote Subtitle Url: " + zimuUrl);
                                setSubtitle(zimuUrl);//设置字幕
                                searchSubtitleDialog.dismiss();
                            }
                        });
                    }
                });
                if (mVodInfo.playFlag.contains("Ali") || mVodInfo.playFlag.contains("parse")) {
                    searchSubtitleDialog.setSearchWord(mVodInfo.playNote);
                } else {
                    searchSubtitleDialog.setSearchWord(mVodInfo.name);
                }
                searchSubtitleDialog.show();
            }
        });
        subtitleDialog.setLocalFileChooserListener(new SubtitleDialog.LocalFileChooserListener() {
            @Override
            public void openLocalFileChooserDialog() {
                new ChooserDialog(getActivity(),R.style.FileChooser)
                        .withFilter(false, false, "srt", "ass", "scc", "stl", "ttml")
                        .withStartFile("/storage/emulated/0/Download")
                        .withChosenListener(new ChooserDialog.Result() {
                            @Override
                            public void onChoosePath(String path, File pathFile) {
                                LOG.i("Local Subtitle Path: " + path);
                                setSubtitle(path);//设置字幕
                            }
                        })
                        .build()
                        .show();
            }
        });
        subtitleDialog.show();
    }

    @SuppressLint("UseCompatLoadingForColorStateLists")
    void setSubtitleViewTextStyle(int style) {
        if (style == 0) {
            mController.mSubtitleView.setTextColor(getContext().getResources().getColorStateList(R.color.color_FFFFFF));
        } else if (style == 1) {
            mController.mSubtitleView.setTextColor(getContext().getResources().getColorStateList(R.color.color_FFB6C1));
        }
    }

    void selectMyAudioTrack() {
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();

        TrackInfo trackInfo = null;
        if (mediaPlayer instanceof IjkMediaPlayer) {
            trackInfo = ((IjkMediaPlayer) mediaPlayer).getTrackInfo();
        }
        if (mediaPlayer instanceof EXOmPlayer) {
            trackInfo = ((EXOmPlayer) mediaPlayer).getTrackInfo();
        }

        if (trackInfo == null) {
            Toast.makeText(mContext, "没有音轨", Toast.LENGTH_SHORT).show();
            return;
        }
        List<TrackInfoBean> bean = trackInfo.getAudio();
        if (bean.size() < 1) return;
        SelectDialog<TrackInfoBean> dialog = new SelectDialog<>(getActivity());
        dialog.setTip("切换音轨");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<TrackInfoBean>() {
            @Override
            public void click(TrackInfoBean value, int pos) {
                try {
                    for (TrackInfoBean audio : bean) {
                        audio.selected = audio.trackId == value.trackId;
                    }
                    mediaPlayer.pause();
                    long progress = mediaPlayer.getCurrentPosition();//保存当前进度，ijk 切换轨道 会有快进几秒
                    if (mediaPlayer instanceof IjkMediaPlayer) {
                        ((IjkMediaPlayer) mediaPlayer).setTrack(value.trackId);
                    }
                    if (mediaPlayer instanceof EXOmPlayer) {
                        ((EXOmPlayer) mediaPlayer).selectExoTrack(value);
                    }
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mediaPlayer.seekTo(progress);
                            mediaPlayer.start();
                        }
                    }, 800);
                    dialog.dismiss();
                } catch (Exception e) {
                    LOG.e("切换音轨出错");
                }
            }

            @Override
            public String getDisplay(TrackInfoBean val) {
                String name = val.name.replace("AUDIO,", "");
                name = name.replace("N/A,", "");
                name = name.replace(" ", "");
                return name + (TextUtils.isEmpty(val.language) ? "" : " " + val.language);
            }
        }, new DiffUtil.ItemCallback<TrackInfoBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
                return oldItem.trackId == newItem.trackId;
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
                return oldItem.trackId == newItem.trackId;
            }
        }, bean, trackInfo.getAudioSelected(false));
        dialog.show();
    }

    void selectMyInternalSubtitle() {
        AbstractPlayer mediaPlayer = mVideoView.getMediaPlayer();
        TrackInfo trackInfo = null;
        if (mediaPlayer instanceof EXOmPlayer) {
            trackInfo = ((EXOmPlayer)mediaPlayer).getTrackInfo();
        }
        if (mediaPlayer instanceof IjkMediaPlayer) {
            trackInfo = ((IjkMediaPlayer)mediaPlayer).getTrackInfo();
        }

        if (trackInfo == null) {
            Toast.makeText(mContext, "没有内置字幕", Toast.LENGTH_SHORT).show();
            return;
        }
        List<TrackInfoBean> bean = trackInfo.getSubtitle();
        if (bean.size() < 1) return;
        SelectDialog<TrackInfoBean> dialog = new SelectDialog<>(mActivity);
        dialog.setTip("切换内置字幕");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<TrackInfoBean>() {
            @Override
            public void click(TrackInfoBean value, int pos) {
                mController.mSubtitleView.setVisibility(View.VISIBLE);
                try {
                    for (TrackInfoBean subtitle : bean) {
                        subtitle.selected =subtitle.trackGroupId == value.trackGroupId && subtitle.trackId == value.trackId;
                    }
                    mediaPlayer.pause();
                    long progress = mediaPlayer.getCurrentPosition();//保存当前进度，ijk 切换轨道 会有快进几秒
                    mController.mSubtitleView.destroy();
                    mController.mSubtitleView.clearSubtitleCache();
                    mController.mSubtitleView.isInternal = true;

                    if (mediaPlayer instanceof IjkMediaPlayer) {
                        ((IjkMediaPlayer)mediaPlayer).setTrack(value.trackId);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mediaPlayer.seekTo(progress);
                                mediaPlayer.start();
                            }
                        }, 800);
                    }
                    if (mediaPlayer instanceof EXOmPlayer) {
                        ((EXOmPlayer)mediaPlayer).selectExoTrack(value);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mediaPlayer.seekTo(progress);
                                mediaPlayer.start();
                                mController.startProgress();
                            }
                        }, 800);
                    }
                    dialog.dismiss();
                } catch (Exception e) {
                    LOG.e("切换内置字幕出错");
                }
            }

            @Override
            public String getDisplay(TrackInfoBean val) {
                return val.name + (TextUtils.isEmpty(val.language)? "": " " + val.language);
            }
        }, new DiffUtil.ItemCallback<TrackInfoBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
                return oldItem.trackId == newItem.trackId;
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull TrackInfoBean oldItem, @NonNull @NotNull TrackInfoBean newItem) {
                return oldItem.trackId == newItem.trackId;
            }
        }, bean, trackInfo.getSubtitleSelected(false));
        dialog.show();
    }

    void setTip(String msg, boolean loading, boolean err) {
        if (!isAdded()) return;
        //影魔
        requireActivity().runOnUiThread(() -> {
            mPlayLoadTip.setText(msg);
            mPlayLoadTip.setVisibility(View.VISIBLE);
            mPlayLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
            mPlayLoadErr.setVisibility(err ? View.VISIBLE : View.GONE);
            if (!err || loading) return;

            // 所有错误态统一带"点击重试"入口(旧版只有"视频播放出错"文案有切换入口,
            // 嗅探错误/解析失败等二次失败后错误页干等无任何动作)
            if ("视频播放出错".equals(msg)) {
                if (!progressKey.equals(retriedSwitchPlayerKey)) {
                    ToastUtils.showShort("播放出错,正在尝试切换播放器");
                    retriedSwitchPlayerKey = progressKey == null ? "" : progressKey;
                    mController.mPlayerBtn.performClick();
                    return;
                }
                SpanUtils.with(mPlayLoadTip)
                        .append("视频播放出错，")
                        .append("切换播放器")
                        .setClickSpan(ColorUtils.getColor(R.color.orange), false, view -> {
                            mController.mPlayerBtn.performClick();
                        })
                        .append("，或")
                        .append("点击重试")
                        .setClickSpan(ColorUtils.getColor(R.color.orange), false, view -> retryCurrentPlay())
                        .create();
            } else {
                SpanUtils.with(mPlayLoadTip)
                        .append(msg + "，")
                        .append("点击重试")
                        .setClickSpan(ColorUtils.getColor(R.color.orange), false, view -> retryCurrentPlay())
                        .create();
            }
        });
    }

    /** 错误页重试入口: 重新走一遍当前集的完整播放链路(重解析/重嗅探) */
    private void retryCurrentPlay() {
        if (!isAdded() || mVodInfo == null) return;
        play(false);
    }

    void hideTip() {
        mPlayLoadTip.setVisibility(View.GONE);
        mPlayLoading.setVisibility(View.GONE);
        mPlayLoadErr.setVisibility(View.GONE);
    }

    void errorWithRetry(String err, boolean finish) {
        if (!autoRetry() && isAdded()) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (finish) {
                        // play() 设置的"正在获取播放信息"加载态必须清掉, 否则 Toast 消失后界面残留转圈
                        hideTip();
                        Toast.makeText(mContext, err, Toast.LENGTH_SHORT).show();
                    } else {
                        setTip(err, false, true);
                    }
                }
            });
        }
    }

    private String removeMinorityUrl(String tsUrlPre, String m3u8content) {
        if (!m3u8content.startsWith("#EXTM3U")) return null;
        String linesplit = "\n";
        if (m3u8content.contains("\r\n"))
            linesplit = "\r\n";
        String[] lines = m3u8content.split(linesplit);

        HashMap<String, Integer> preUrlMap = new HashMap<>();
        for (String line : lines) {
            if (line.length() == 0 || line.charAt(0) == '#') {
                continue;
            }
            int ilast = line.lastIndexOf('.');
            if (ilast <= 4) {
                continue;
            }
            String preUrl = line.substring(0, ilast - 4);
            Integer cnt = preUrlMap.get(preUrl);
            if (cnt != null) {
                preUrlMap.put(preUrl, cnt + 1);
            } else {
                preUrlMap.put(preUrl, 1);
            }
        }
        if (preUrlMap.size() <= 1) return null;
        if (preUrlMap.size() > 5) return null;//too many different url, can not identify ads url
        int maxTimes = 0;
        String maxTimesPreUrl = "";
        for (Map.Entry<String, Integer> entry : preUrlMap.entrySet()) {
            if (entry.getValue() > maxTimes) {
                maxTimesPreUrl = entry.getKey();
                maxTimes = entry.getValue();
            }
        }
        if (maxTimes == 0) return null;

        boolean dealedExtXKey = false;
        for (int i = 0; i < lines.length; ++i) {
            if (!dealedExtXKey && lines[i].startsWith("#EXT-X-KEY")) {
                String keyUrl = StringUtils.substringBetween(lines[i], "URI=\"", "\"");
                if (keyUrl != null && !keyUrl.startsWith("http://") && !keyUrl.startsWith("https://")) {
                    String newKeyUrl;
                    if (keyUrl.charAt(0) == '/') {
                        int ifirst = tsUrlPre.indexOf('/', 9);//skip https://, http://
                        newKeyUrl = tsUrlPre.substring(0, ifirst) + keyUrl;
                    } else
                        newKeyUrl = tsUrlPre + keyUrl;
                    lines[i] = lines[i].replace("URI=\"" + keyUrl + "\"", "URI=\"" + newKeyUrl + "\"");
                }
                dealedExtXKey = true;
            }
            if (lines[i].length() == 0 || lines[i].charAt(0) == '#') {
                continue;
            }
            if (lines[i].startsWith(maxTimesPreUrl)) {
                if (!lines[i].startsWith("http://") && !lines[i].startsWith("https://")) {
                    if (lines[i].charAt(0) == '/') {
                        int ifirst = tsUrlPre.indexOf('/', 9);//skip https://, http://
                        lines[i] = tsUrlPre.substring(0, ifirst) + lines[i];
                    } else
                        lines[i] = tsUrlPre + lines[i];
                }
            } else {
                if (i > 0 && lines[i - 1].length() > 0 && lines[i - 1].charAt(0) == '#') {
                    lines[i - 1] = "";
                }
                lines[i] = "";
            }
        }
        return StringUtils.join(lines, linesplit);
    }

    void playUrl(String url, HashMap<String, String> headers) {
        mCurrentUrl = url;
        if (!Hawk.get(HawkConfig.VIDEO_PURIFY, true)) {
            startPlayUrl(url, headers);
            return;
        }
        if (!url.contains("://127.0.0.1/") && !url.contains(".m3u8")) {
            startPlayUrl(url, headers);
            return;
        }
        OkGo.getInstance().cancelTag("m3u8-1");
        OkGo.getInstance().cancelTag("m3u8-2");
        //remove ads in m3u8
        HttpHeaders hheaders = new HttpHeaders();
        if(headers != null){
            for (Map.Entry<String, String> s : headers.entrySet()) {
                hheaders.put(s.getKey(), s.getValue());
            }
        }

        OkGo.<String>get(url)
                .tag("m3u8-1")
                .headers(hheaders)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(com.lzy.okgo.model.Response<String> response) {
                        String content = response.body();
                        if (!content.startsWith("#EXTM3U")) {
                            startPlayUrl(url, headers);
                            return;
                        }

                        String[] lines = null;
                        if (content.contains("\r\n"))
                            lines = content.split("\r\n", 10);
                        else
                            lines = content.split("\n", 10);
                        String forwardurl = "";
                        boolean dealedFirst = false;
                        for (String line : lines) {
                            if (!"".equals(line) && line.charAt(0) != '#') {
                                if (dealedFirst) {
                                    //跳转行后还有内容，说明不需要跳转
                                    forwardurl = "";
                                    break;
                                }
                                if (line.endsWith(".m3u8") || line.contains(".m3u8?")) {
                                    if (line.startsWith("http://") || line.startsWith("https://")) {
                                        forwardurl = line;
                                    } else if (line.charAt(0)=='/' ) {
                                        int ifirst = url.indexOf('/', 9);//skip https://, http://
                                        forwardurl = url.substring(0, ifirst) + line;
                                    } else {
                                        int ilast = url.lastIndexOf('/');
                                        forwardurl = url.substring(0, ilast + 1) + line;
                                    }
                                }
                                dealedFirst = true;
                            }
                        }
                        if ("".equals(forwardurl)) {
                            int ilast = url.lastIndexOf('/');

                            RemoteServer.m3u8Content = removeMinorityUrl(url.substring(0, ilast + 1), content);
                            if (RemoteServer.m3u8Content == null)
                                startPlayUrl(url, headers);
                            else {
                                startPlayUrl("http://127.0.0.1:" + RemoteServer.serverPort + "/m3u8", headers);
                                //Toast.makeText(getContext(), "已移除视频广告", Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        final String finalforwardurl = forwardurl;
                        OkGo.<String>get(forwardurl)
                                .tag("m3u8-2")
                                .headers(hheaders)
                                .execute(new AbsCallback<String>() {
                                    @Override
                                    public void onSuccess(com.lzy.okgo.model.Response<String> response) {
                                        String content = response.body();
                                        int ilast = finalforwardurl.lastIndexOf('/');
                                        RemoteServer.m3u8Content = removeMinorityUrl(finalforwardurl.substring(0, ilast + 1), content);

                                        if (RemoteServer.m3u8Content == null)
                                            startPlayUrl(finalforwardurl, headers);
                                        else {
                                            startPlayUrl("http://127.0.0.1:" + RemoteServer.serverPort + "/m3u8", headers);
                                            //Toast.makeText(getContext(), "已移除视频广告", Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    @Override
                                    public String convertResponse(okhttp3.Response response) throws Throwable {
                                        return response.body().string();
                                    }

                                    @Override
                                    public void onError(com.lzy.okgo.model.Response<String> response) {
                                        super.onError(response);
                                        startPlayUrl(url, headers);
                                    }
                                });
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }

                    @Override
                    public void onError(com.lzy.okgo.model.Response<String> response) {
                        super.onError(response);
                        startPlayUrl(url, headers);
                    }
                });
    }

    void startPlayUrl(String url, HashMap<String, String> headers) {
        LOG.i("playUrl:" + url);
        // 旧实现重试时把 m3u8 地址拼到硬编码第三方去 BOM 服务(home.jundie.top:666):
        // 域名不可达则重试必败, 还把播放地址泄露给第三方, 移除后直接重试原始地址
        String finalUrl = url;
        if (mActivity == null || !isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            stopParse();
            if (mVideoView != null) {
                mVideoView.release();

                if (finalUrl != null) {
                    try {
                        int playerType = mVodPlayerCfg.getInt("pl");
                        if (playerType >= 10) {
                            VodInfo.VodSeries vs = mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex);
                            String playTitle = mVodInfo.name + " " + vs.name;
                            setTip("调用外部播放器" + PlayerHelper.getPlayerName(playerType) + "进行播放", true, false);
                            boolean callResult = false;
                            long progress = getSavedProgress(progressKey);
                            callResult = PlayerHelper.runExternalPlayer(playerType, requireActivity(), finalUrl, playTitle, playSubtitle, headers, progress);
                            setTip("调用外部播放器" + PlayerHelper.getPlayerName(playerType) + (callResult ? "成功" : "失败"), callResult, !callResult);
                            return;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    hideTip();
                    PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg);
                    mVideoView.setProgressKey(progressKey);
                    if (headers != null) {
                        mVideoView.setUrl(finalUrl, headers);
                    } else {
                        mVideoView.setUrl(finalUrl);
                    }
                    mVideoView.start();
                    mController.resetSpeed();
                }
            }
        });
    }

    private void initSubtitleView() {
        // 返回退出时 onDestroyView 已 release 并置空 mVideoView, ExoPlayer 已排队的
        // PREPARED 事件仍会经 controller 派发到这里, 空引用直接 NPE
        if (mVideoView == null || mVideoView.getMediaPlayer() == null || !isAdded()) return;
        TrackInfo trackInfo = null;
        if (mVideoView.getMediaPlayer() instanceof IjkMediaPlayer) {
            trackInfo = ((IjkMediaPlayer) (mVideoView.getMediaPlayer())).getTrackInfo();
            if (trackInfo != null && trackInfo.getSubtitle().size() > 0) {//如有则设置内置字幕
                mController.mSubtitleView.hasInternal = true;
            }
            ((IjkMediaPlayer) (mVideoView.getMediaPlayer())).setOnTimedTextListener(new IMediaPlayer.OnTimedTextListener() {
                @Override
                public void onTimedText(IMediaPlayer mp, IjkTimedText text) {
                    if (mController.mSubtitleView.isInternal) {
                        com.github.tvbox.osc.subtitle.model.Subtitle subtitle = new com.github.tvbox.osc.subtitle.model.Subtitle();
                        subtitle.content = text.getText();
                        mController.mSubtitleView.onSubtitleChanged(subtitle);
                    }
                }
            });
        }

        if (mVideoView.getMediaPlayer() instanceof EXOmPlayer) {
            trackInfo = ((EXOmPlayer) (mVideoView.getMediaPlayer())).getTrackInfo();
            if (trackInfo != null && trackInfo.getSubtitle().size() > 0) {
                mController.mSubtitleView.hasInternal = true;
            }
            ((EXOmPlayer) (mVideoView.getMediaPlayer())).setOnTimedTextListener(new Player.Listener() {
                @Override
                public void onCues(@NonNull List<Cue> cues) {
                    if (cues.size() > 0) {
                        CharSequence ss = cues.get(0).text;
                        if (ss != null && mController.mSubtitleView.isInternal) {
                            com.github.tvbox.osc.subtitle.model.Subtitle subtitle = new com.github.tvbox.osc.subtitle.model.Subtitle();
                            subtitle.content = ss.toString();
                            mController.mSubtitleView.onSubtitleChanged(subtitle);
                        }
                    } else{
                        mController.mSubtitleView.onSubtitleChanged(null);
                    }
                }
            });
        }

        mController.mSubtitleView.bindToMediaPlayer(mVideoView.getMediaPlayer());
        mController.mSubtitleView.setPlaySubtitleCacheKey(subtitleCacheKey);
        String subtitlePathCache = (String) CacheManager.getCache(MD5.string2MD5(subtitleCacheKey));
        if (subtitlePathCache != null && !subtitlePathCache.isEmpty()) {
            mController.mSubtitleView.setSubtitlePath(subtitlePathCache);
        } else {
            if (playSubtitle != null && playSubtitle.length() > 0) {
                mController.mSubtitleView.setSubtitlePath(playSubtitle);
            } else {
                if (mController.mSubtitleView.hasInternal) {//有则使用内置字幕
                    mController.mSubtitleView.isInternal = true;
                    if (trackInfo != null && !trackInfo.getSubtitle().isEmpty()) {
                        List<TrackInfoBean> subtitleTrackList = trackInfo.getSubtitle();
                        int selectedIndex = trackInfo.getSubtitleSelected(true);
                        boolean hasCh =false;
                        for(TrackInfoBean subtitleTrackInfoBean : subtitleTrackList) {
                            String lowerLang = subtitleTrackInfoBean.language.toLowerCase();
                            if (lowerLang.contains("zh") || lowerLang.contains("ch")) {
                                hasCh=true;
                                if (selectedIndex != subtitleTrackInfoBean.trackId) {
                                    if (mVideoView.getMediaPlayer() instanceof IjkMediaPlayer){
                                        ((IjkMediaPlayer)(mVideoView.getMediaPlayer())).setTrack(subtitleTrackInfoBean.trackId);
                                    }else if (mVideoView.getMediaPlayer() instanceof EXOmPlayer){
                                        ((EXOmPlayer)(mVideoView.getMediaPlayer())).selectExoTrack(subtitleTrackInfoBean);
                                    }
                                    break;
                                }
                            }
                        }
                        if(!hasCh){
                            if (mVideoView.getMediaPlayer() instanceof IjkMediaPlayer){
                                ((IjkMediaPlayer)(mVideoView.getMediaPlayer())).setTrack(subtitleTrackList.get(0).trackId);
                            }else if (mVideoView.getMediaPlayer() instanceof EXOmPlayer){
                                ((EXOmPlayer)(mVideoView.getMediaPlayer())).selectExoTrack(subtitleTrackList.get(0));
                            }
                        }
                    }
                }
            }
        }
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.playResult.observeForever(mObserverPlayResult);
    }

    private final Observer<JSONObject> mObserverPlayResult= new Observer<JSONObject>() {
        @Override
        public void onChanged(JSONObject info) {
            if (info != null) {
                try {
                    progressKey = info.optString("proKey", null);
                    boolean parse = info.optString("parse", "1").equals("1");
                    boolean jx = info.optString("jx", "0").equals("1");
                    playSubtitle = info.optString("subt", /*"https://dash.akamaized.net/akamai/test/caption_test/ElephantsDream/ElephantsDream_en.vtt"*/"");
                    subtitleCacheKey = info.optString("subtKey", null);
                    String playUrl = info.optString("playUrl", "");
                    String flag = info.optString("flag");
                    String url = info.getString("url");
                    HashMap<String, String> headers = null;
                    webUserAgent = null;
                    webHeaderMap = null;
                    if (info.has("header")) {
                        try {
                            JSONObject hds = new JSONObject(info.getString("header"));
                            Iterator<String> keys = hds.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                if (headers == null) {
                                    headers = new HashMap<>();
                                }
                                headers.put(key, hds.getString(key));
                                if (key.equalsIgnoreCase("user-agent")) {
                                    webUserAgent = hds.getString(key).trim();
                                }
                            }
                            webHeaderMap = headers;
                        } catch (Throwable th) {

                        }
                    }
                    if (parse || jx) {
                        boolean userJxList = (playUrl.isEmpty() && ApiConfig.get().getVipParseFlags().contains(flag)) || jx;
                        initParse(flag, userJxList, playUrl, url);
                    } else {
                        mController.showParse(false);
                        playUrl(playUrl + url, headers);
                    }
                } catch (Throwable th) {
                    LogUtils.e(th.toString());
                    // 解析异常不能吞掉: 吞了界面永远停在"正在获取播放信息"转圈
                    errorWithRetry("获取播放信息错误", true);
                }
            } else {
                errorWithRetry("获取播放信息错误", true);
            }
        }
    };

    public void setData(Bundle bundle) {
//        mVodInfo = (VodInfo) bundle.getSerializable("VodInfo");
        mVodInfo = App.getInstance().getVodInfo();
        sourceKey = bundle.getString("sourceKey");
        sourceBean = ApiConfig.get().getSource(sourceKey);
        initPlayerCfg();
        play(false);
    }

    private void initData() {
        /*Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {

        }*/
    }

    void initPlayerCfg() {
        try {
            mVodPlayerCfg = new JSONObject(mVodInfo.playerCfg);
        } catch (Throwable th) {
            mVodPlayerCfg = new JSONObject();
        }
        try {
            if (!mVodPlayerCfg.has("pl")) {
                mVodPlayerCfg.put("pl", (sourceBean.getPlayerType() == -1) ? (int) Hawk.get(HawkConfig.PLAY_TYPE, 1) : sourceBean.getPlayerType());
            }
            if (!mVodPlayerCfg.has("pr")) {
                mVodPlayerCfg.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            }
            if (!mVodPlayerCfg.has("ijk")) {
                mVodPlayerCfg.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, ""));
            }
            if (!mVodPlayerCfg.has("sc")) {
                mVodPlayerCfg.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0));
            }
            if (!mVodPlayerCfg.has("sp")) {
                mVodPlayerCfg.put("sp", 1.0f);
            }
            if (!mVodPlayerCfg.has("st")) {
                mVodPlayerCfg.put("st", 0);
            }
            if (!mVodPlayerCfg.has("et")) {
                mVodPlayerCfg.put("et", 0);
            }
        } catch (Throwable th) {

        }
        mController.setPlayerConfig(mVodPlayerCfg);
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null) {
            if (mController.onKeyEvent(event)) {
                return true;
            }
        }
        return false;
    }

    /** 生命周期引起的暂停(切后台/被遮挡且真的在播), 回前台按它决定是否恢复;
     *  用户主动暂停不置位, 回前台保持暂停不被强制续播 */
    private boolean mPausedByLifecycle = false;

    @Override
    public void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && getActivity() != null && getActivity().isInPictureInPictureMode()) {
            return;
        }
        int bgPlayType = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0);
        if (bgPlayType == 1) {
            return;
        }
        if (mVideoView != null) {
            boolean wasPlaying = mVideoView.isPlaying();
            mVideoView.pause();
            mPausedByLifecycle = wasPlaying;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 只恢复"因切后台/被遮挡而暂停"的播放; 用户手动暂停的回来保持暂停
        if (mVideoView != null && mPausedByLifecycle) {
            mPausedByLifecycle = false;
            mVideoView.resume();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) {
            if (mVideoView != null) {
                boolean wasPlaying = mVideoView.isPlaying();
                mVideoView.pause();
                mPausedByLifecycle = wasPlaying;
            }
        } else {
            // 同 onResume: 只恢复因被遮挡而暂停的播放, 用户手动暂停保持暂停
            if (mVideoView != null && mPausedByLifecycle) {
                mPausedByLifecycle = false;
                mVideoView.resume();
            }
        }
        super.onHiddenChanged(hidden);
    }

    /** 网络恢复监听: 错误态/卡加载态时自动重试当前集, 断网期间用户无需手动点重试 */
    private ConnectivityManager.NetworkCallback mNetworkCallback;

    private void registerNetworkCallback() {
        try {
            if (mNetworkCallback != null || getActivity() == null) return;
            ConnectivityManager cm = (ConnectivityManager) requireActivity().getApplicationContext()
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            mNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(PlayFragment.this::onNetworkRestored);
                    }
                }
            };
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), mNetworkCallback);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private void unregisterNetworkCallback() {
        try {
            if (mNetworkCallback != null && getActivity() != null) {
                ConnectivityManager cm = (ConnectivityManager) getActivity().getApplicationContext()
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) cm.unregisterNetworkCallback(mNetworkCallback);
            }
        } catch (Throwable ignored) {
        }
        mNetworkCallback = null;
    }

    /** 网络恢复: 明确错误态或错误 tip 还挂着(卡加载)时重试; 手动暂停态不受打扰 */
    private void onNetworkRestored() {
        if (mVideoView == null || !isAdded() || mVodInfo == null) return;
        int st = mVideoView.getCurrentPlayState();
        boolean stuckOnError = st == VideoView.STATE_ERROR
                || (st == VideoView.STATE_IDLE && mPlayLoadTip.getVisibility() == View.VISIBLE);
        if (stuckOnError) {
            mHandler.post(this::retryCurrentPlay);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        //手动注销
        sourceViewModel.playResult.removeObserver(mObserverPlayResult);

        EventBus.getDefault().unregister(this);
        unregisterNetworkCallback();
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        stopLoadWebView(true);
        stopParse();
        SyncTrigger.get().triggerFlushSync();
    }

    private VodInfo mVodInfo;
    private JSONObject mVodPlayerCfg;
    private String sourceKey;
    private SourceBean sourceBean;

    public void playNext(boolean isProgress) {
        boolean hasNext;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasNext = false;
        } else {
            hasNext = mVodInfo.playIndex + 1 < mVodInfo.seriesMap.get(mVodInfo.playFlag).size();
        }
        if (!hasNext) {
            Toast.makeText(requireContext(), "已经是最后一集了!", Toast.LENGTH_SHORT).show();
            return;
        } else {
            mVodInfo.playIndex++;
        }
        play(false);
        SyncTrigger.get().triggerFlushSync();
    }

    public void playPrevious() {
        boolean hasPre = true;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasPre = false;
        } else {
            hasPre = mVodInfo.playIndex - 1 >= 0;
        }
        if (!hasPre) {
            Toast.makeText(requireContext(), "已经是第一集了!", Toast.LENGTH_SHORT).show();
            return;
        }
        mVodInfo.playIndex--;
        play(false);
    }

    private int autoRetryCount = 0;

    boolean autoRetry() {
        if (loadFoundVideoUrls != null && loadFoundVideoUrls.size() > 0) {
            autoRetryFromLoadFoundVideoUrls();
            return true;
        }
        if (autoRetryCount < 1) {
            autoRetryCount++;
            play(false);
            return true;
        } else {
            autoRetryCount = 0;
            return false;
        }
    }

    void autoRetryFromLoadFoundVideoUrls() {
        String videoUrl = loadFoundVideoUrls.poll();
        HashMap<String, String> header = loadFoundVideoUrlsHeader.get(videoUrl);
        playUrl(videoUrl, header);
    }

    void initParseLoadFound() {
        loadFoundCount.set(0);
        loadFoundVideoUrls = new LinkedList<String>();
        loadFoundVideoUrlsHeader = new HashMap<String, HashMap<String, String>>();
        // 广告判定缓存跨播放/换源从不清理: 陈旧标记失效且 map 持续增长
        loadedUrls.clear();
    }

    public void play(boolean reset) {
        if (mVodInfo == null) return;
        VodInfo.VodSeries vs = mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodInfo.playIndex));
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH_NOTIFY,
                mVodInfo.name + "&&" + vs.name + "&&" + (mVodInfo.pic == null ? "" : mVodInfo.pic)));
        String playTitleInfo = mVodInfo.name + " " + vs.name;
        setTip("正在获取播放信息", true, false);
        mController.setTitle(playTitleInfo);

        stopParse();
        initParseLoadFound();
        if (mVideoView != null) mVideoView.release();
        String subtitleCacheKey = mVodInfo.sourceKey + "-" + mVodInfo.id + "-" + mVodInfo.playFlag + "-" + mVodInfo.playIndex + "-" + vs.name + "-subt";
        String progressKey = mVodInfo.sourceKey + mVodInfo.id + mVodInfo.playFlag + mVodInfo.playIndex + vs.name;
        this.progressKey = progressKey;
        this.subtitleCacheKey = subtitleCacheKey;
        //重新播放清除现有进度
        if (reset) {
            String pKey = MD5.string2MD5(progressKey);
            CacheManager.delete(pKey, 0);
            CacheManager.delete(MD5.string2MD5(subtitleCacheKey), 0);
            AppRoomSyncAdapter.recordProgressDelete(pKey);
        }
        //离线缓存命中:直接播放应用内已缓存的本地文件
        DownloadEpisode cachedEp = findCachedEpisode(vs);
        if (cachedEp != null) {
            hideTip();
            mController.showParse(false);
            // 进度键改用下载任务的规范键(线路内集号+集名), 与 LocalPlayActivity/在线续播互通;
            // 系列索引键受观看历史倒序(playIndex 翻转)影响会读不到旧进度
            this.progressKey = cachedEp.sourceKey + cachedEp.vodId + cachedEp.flag
                    + cachedEp.episodeIndex + cachedEp.episodeName;
            startLocalUrl(cachedEp.localFilePath);
            return;
        }
        if (isUnsupportedMediaUrl(vs.url)) {//磁力/迅雷/ed2k/ftp 直链(迅雷 SDK 已移除)
            hideTip();
            Toast.makeText(requireContext(), "该链接需要磁力/迅雷播放,当前版本不支持", Toast.LENGTH_SHORT).show();
            return;
        }
        sourceViewModel.getPlay(sourceKey, mVodInfo.playFlag, progressKey, vs.url, subtitleCacheKey);
    }

    /**
     * 磁力/迅雷/ed2k/ftp 等无法直接播放的链接(迅雷 SDK 已移除)
     */
    private boolean isUnsupportedMediaUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.startsWith("magnet:") || u.startsWith("thunder") || u.startsWith("ed2k:")
                || u.startsWith("ftp:") || u.endsWith(".torrent") || u.startsWith("tvbox-xg:")
                || u.startsWith("tvbox-torrent:") || u.startsWith("tvbox-oth:");
    }

    /**
     * 查找当前集的应用内缓存(已完成),命中返回下载任务(带规范进度键字段),文件不存在返回 null
     */
    private DownloadEpisode findCachedEpisode(VodInfo.VodSeries vs) {
        try {
            DownloadEpisode ep = RoomDataManger.getCompletedEpisode(mVodInfo.sourceKey, mVodInfo.id, mVodInfo.playFlag, mVodInfo.playIndex);
            if (ep == null && vs.url != null) {
                ep = RoomDataManger.getCompletedEpisodeByRawUrl(vs.url);
            }
            if (ep == null || ep.localFilePath == null) return null;
            File file = new File(ep.localFilePath);
            return file.exists() && file.length() > 0 ? ep : null;
        } catch (Throwable th) {
            return null;
        }
    }

    /**
     * 播放本地缓存文件(file://),进度仍记录在原progressKey下
     */
    private void startLocalUrl(String path) {
        if (mActivity == null || !isAdded()) return;
        final String url = "file://" + path;
        mCurrentUrl = url;
        requireActivity().runOnUiThread(() -> {
            stopParse();
            if (mVideoView != null) {
                hideTip();
                PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg);
                mVideoView.setProgressKey(progressKey);
                mVideoView.setUrl(url);
                mVideoView.start();
                mController.resetSpeed();
            }
        });
    }

    private String playSubtitle;
    private String subtitleCacheKey;
    private String progressKey;
    private String parseFlag;
    private String webUrl;
    private String webUserAgent;
    private Map<String, String> webHeaderMap;

    private void initParse(String flag, boolean useParse, String playUrl, final String url) {
        parseFlag = flag;
        webUrl = url;
        ParseBean parseBean = null;
        mController.showParse(useParse);
        if (useParse) {
            parseBean = ApiConfig.get().getDefaultParse();
        } else {
            if (playUrl.startsWith("json:")) {
                parseBean = new ParseBean();
                parseBean.setType(1);
                parseBean.setUrl(playUrl.substring(5));
            } else if (playUrl.startsWith("parse:")) {
                String parseRedirect = playUrl.substring(6);
                for (ParseBean pb : ApiConfig.get().getParseBeanList()) {
                    if (pb.getName().equals(parseRedirect)) {
                        parseBean = pb;
                        break;
                    }
                }
            }
            if (parseBean == null) {
                parseBean = new ParseBean();
                parseBean.setType(0);
                parseBean.setUrl(playUrl);
            }
        }
        doParse(parseBean);
    }

    JSONObject jsonParse(String input, String json) throws JSONException {
        JSONObject jsonPlayData = new JSONObject(json);
        //小窗版解析方法改到这了  之前那个位置data解析无效
        String url;
        if (jsonPlayData.has("data")) {
            url = jsonPlayData.getJSONObject("data").getString("url");
        } else {
            url = jsonPlayData.getString("url");
        }
        if (url.startsWith("//")) {
            url = "http:" + url;
        }
        if (!url.startsWith("http")) {
            return null;
        }
        JSONObject headers = new JSONObject();
        String ua = jsonPlayData.optString("user-agent", "");
        if (ua.trim().length() > 0) {
            headers.put("User-Agent", " " + ua);
        }
        String referer = jsonPlayData.optString("referer", "");
        if (referer.trim().length() > 0) {
            headers.put("Referer", " " + referer);
        }
        JSONObject taskResult = new JSONObject();
        taskResult.put("header", headers);
        taskResult.put("url", url);
        return taskResult;
    }

    void stopParse() {
        mHandler.removeMessages(100);
        stopLoadWebView(false);
        OkGo.getInstance().cancelTag("json_jx");
        if (parseThreadPool != null) {
            try {
                parseThreadPool.shutdown();
                parseThreadPool = null;
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    ExecutorService parseThreadPool;

    private void doParse(ParseBean pb) {
        stopParse();
        initParseLoadFound();
        if (pb.getType() == 0) {
            setTip("正在嗅探播放地址", true, false);
            mHandler.removeMessages(100);
            mHandler.sendEmptyMessageDelayed(100, 20 * 1000);
            if (pb.getExt() != null) {
                // 解析ext
                try {
                    HashMap<String, String> reqHeaders = new HashMap<>();
                    JSONObject jsonObject = new JSONObject(pb.getExt());
                    if (jsonObject.has("header")) {
                        JSONObject headerJson = jsonObject.optJSONObject("header");
                        Iterator<String> keys = headerJson.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            if (key.equalsIgnoreCase("user-agent")) {
                                webUserAgent = headerJson.getString(key).trim();
                            } else {
                                reqHeaders.put(key, headerJson.optString(key, ""));
                            }
                        }
                        if (reqHeaders.size() > 0) webHeaderMap = reqHeaders;
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            loadWebView(pb.getUrl() + webUrl);

        } else if (pb.getType() == 1) { // json 解析
            setTip("正在解析播放地址", true, false);
            // 解析ext
            HttpHeaders reqHeaders = new HttpHeaders();
            try {
                JSONObject jsonObject = new JSONObject(pb.getExt());
                if (jsonObject.has("header")) {
                    JSONObject headerJson = jsonObject.optJSONObject("header");
                    Iterator<String> keys = headerJson.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        reqHeaders.put(key, headerJson.optString(key, ""));
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            OkGo.<String>get(pb.getUrl() + encodeUrl(webUrl))
                    .tag("json_jx")
                    .headers(reqHeaders)
                    .execute(new AbsCallback<String>() {
                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            if (response.body() != null) {
                                return response.body().string();
                            } else {
                                throw new IllegalStateException("网络请求错误");
                            }
                        }

                        @Override
                        public void onSuccess(Response<String> response) {
                            String json = response.body();
                            try {
                                JSONObject rs = jsonParse(webUrl, json);
                                HashMap<String, String> headers = null;
                                if (rs.has("header")) {
                                    try {
                                        JSONObject hds = rs.getJSONObject("header");
                                        Iterator<String> keys = hds.keys();
                                        while (keys.hasNext()) {
                                            String key = keys.next();
                                            if (headers == null) {
                                                headers = new HashMap<>();
                                            }
                                            headers.put(key, hds.getString(key));
                                        }
                                    } catch (Throwable th) {

                                    }
                                }
                                playUrl(rs.getString("url"), headers);
                            } catch (Throwable e) {
                                e.printStackTrace();
                                errorWithRetry("解析错误", false);
//                                setTip("解析错误", false, true);
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            super.onError(response);
                            errorWithRetry("解析错误", false);
//                            setTip("解析错误", false, true);
                        }
                    });
        } else if (pb.getType() == 2) { // json 扩展
            setTip("正在解析播放地址", true, false);
            parseThreadPool = Executors.newSingleThreadExecutor();
            LinkedHashMap<String, String> jxs = new LinkedHashMap<>();
            for (ParseBean p : ApiConfig.get().getParseBeanList()) {
                if (p.getType() == 1) {
                    jxs.put(p.getName(), p.mixUrl());
                }
            }
            parseThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    JSONObject rs = ApiConfig.get().jsonExt(pb.getUrl(), jxs, webUrl);
                    if (rs == null || !rs.has("url") || rs.optString("url").isEmpty()) {
//                        errorWithRetry("解析错误", false);
                        setTip("解析错误", false, true);
                    } else {
                        HashMap<String, String> headers = null;
                        if (rs.has("header")) {
                            try {
                                JSONObject hds = rs.getJSONObject("header");
                                Iterator<String> keys = hds.keys();
                                while (keys.hasNext()) {
                                    String key = keys.next();
                                    if (headers == null) {
                                        headers = new HashMap<>();
                                    }
                                    headers.put(key, hds.getString(key));
                                }
                            } catch (Throwable th) {

                            }
                        }
                        if (rs.has("jxFrom")) {
                            ToastUtils.showShort("解析来自:" + rs.optString("jxFrom"));
                        }
                        boolean parseWV = rs.optInt("parse", 0) == 1;
                        if (parseWV) {
                            String wvUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                            loadUrl(wvUrl);
                        } else {
                            playUrl(rs.optString("url", ""), headers);
                        }
                    }
                }
            });
        } else if (pb.getType() == 3) { // json 聚合
            setTip("正在解析播放地址", true, false);
            parseThreadPool = Executors.newSingleThreadExecutor();
            LinkedHashMap<String, HashMap<String, String>> jxs = new LinkedHashMap<>();
            String extendName = "";
            for (ParseBean p : ApiConfig.get().getParseBeanList()) {
                HashMap data = new HashMap<String, String>();
                data.put("url", p.getUrl());
                if (p.getUrl().equals(pb.getUrl())) {
                    extendName = p.getName();
                }
                data.put("type", p.getType() + "");
                data.put("ext", p.getExt());
                jxs.put(p.getName(), data);
            }
            String finalExtendName = extendName;
            parseThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    JSONObject rs = ApiConfig.get().jsonExtMix(parseFlag + "111", pb.getUrl(), finalExtendName, jxs, webUrl);
                    if (rs == null || !rs.has("url") || rs.optString("url").isEmpty()) {
//                        errorWithRetry("解析错误", false);
                        setTip("解析错误", false, true);
                    } else {
                        if (rs.has("parse") && rs.optInt("parse", 0) == 1) {
                            if (rs.has("ua")) {
                                webUserAgent = rs.optString("ua").trim();
                            }
                            if (!isAdded()) return;
                            requireActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // 本次播放已有嗅探命中并起播: 迟到的解析结果直接丢弃,
                                    // 不再重启嗅探/重载播放器打断正在播放的声音
                                    if (loadFoundCount.get() > 0) return;
                                    String mixParseUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                                    stopParse();
                                    setTip("正在嗅探播放地址", true, false);
                                    mHandler.removeMessages(100);
                                    mHandler.sendEmptyMessageDelayed(100, 20 * 1000);
                                    loadWebView(mixParseUrl);
                                }
                            });
                        } else {
                            HashMap<String, String> headers = null;
                            if (rs.has("header")) {
                                try {
                                    JSONObject hds = rs.getJSONObject("header");
                                    Iterator<String> keys = hds.keys();
                                    while (keys.hasNext()) {
                                        String key = keys.next();
                                        if (headers == null) {
                                            headers = new HashMap<>();
                                        }
                                        headers.put(key, hds.getString(key));
                                    }
                                } catch (Throwable th) {
                                    th.printStackTrace();
                                }
                            }
                            if (rs.has("jxFrom")) {
                                ToastUtils.showShort("解析来自:" + rs.optString("jxFrom"));
                            }
                            playUrl(rs.optString("url", ""), headers);
                        }
                    }
                }
            });
        }
    }

    private String encodeUrl(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (Exception e) {
            return url;
        }
    }

    private WebView mSysWebView;
    private final Map<String, Boolean> loadedUrls = new HashMap<>();
    private LinkedList<String> loadFoundVideoUrls = new LinkedList<>();
    private HashMap<String, HashMap<String, String>> loadFoundVideoUrlsHeader = new HashMap<>();
    private final AtomicInteger loadFoundCount = new AtomicInteger(0);

    void loadWebView(String url) {
        if (mSysWebView == null) {
            mSysWebView = new MyWebView(mContext);
            configWebViewSys(mSysWebView);
            loadUrl(url);
        } else {
            loadUrl(url);
        }
    }

    void loadUrl(String url) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (mSysWebView != null) {
                mSysWebView.stopLoading();
                if (webUserAgent != null) {
                    mSysWebView.getSettings().setUserAgentString(webUserAgent);
                }
                //mSysWebView.clearCache(true);
                if (webHeaderMap != null) {
                    mSysWebView.loadUrl(url, webHeaderMap);
                } else {
                    mSysWebView.loadUrl(url);
                }
            }
        });
    }

    void stopLoadWebView(boolean destroy) {
        if (mActivity == null || !isAdded()) return;
        requireActivity().runOnUiThread(() -> {

            if (mSysWebView != null) {
                mSysWebView.stopLoading();
                mSysWebView.loadUrl("about:blank");
                if (destroy) {
//                        mSysWebView.clearCache(true);
                    mSysWebView.removeAllViews();
                    mSysWebView.destroy();
                    mSysWebView = null;
                }
            }
        });
    }

    public String getFinalUrl(){
        return TextUtils.isEmpty(mCurrentUrl) || !RegexUtils.isURL(mCurrentUrl) ?"":mCurrentUrl;
    }

    boolean checkVideoFormat(String url) {
        try {
            if (TextUtils.isEmpty(url)) return false;
            // 排除纯网页，但若带 url=http 参数的真实视频流（如反向代理/中转m3u8）应予以放行
            if (url.contains(".html") || (url.contains("url=http") && !url.contains(".m3u8") && !url.contains(".mp4"))) {
                return false;
            }
            if (sourceBean != null && sourceBean.getType() == 3) {
                Spider sp = ApiConfig.get().getCSP(sourceBean);
                if (sp != null && sp.manualVideoCheck()) {
                    return sp.isVideoFormat(url);
                }
            }
            return VideoParseRuler.checkIsVideoForParse(webUrl, url);
        } catch (Exception e) {
            return false;
        }
    }

    private static final String SNIFFER_MUTE_AND_HOOK_JS =
            "(function() {" +
            "  try {" +
            "    var report = function(u) {" +
            "      if (u && typeof u === 'string' && u.indexOf('http') === 0 && u.indexOf('blob:') !== 0) {" +
            "        if (window.TvBoxBridge && window.TvBoxBridge.onVideoDetected) {" +
            "          window.TvBoxBridge.onVideoDetected(u);" +
            "        }" +
            "      }" +
            "    };" +
            "    var muteMedia = function(el) {" +
            "      if (el) {" +
            "        el.muted = true;" +
            "        el.volume = 0;" +
            "        report(el.src);" +
            "        report(el.currentSrc);" +
            "      }" +
            "    };" +
            "    if (!window._tvbox_hooked) {" +
            "      window._tvbox_hooked = true;" +
            "      var origPlay = HTMLMediaElement.prototype.play;" +
            "      HTMLMediaElement.prototype.play = function() {" +
            "        this.muted = true;" +
            "        this.volume = 0;" +
            "        report(this.src);" +
            "        report(this.currentSrc);" +
            "        return origPlay.apply(this, arguments);" +
            "      };" +
            "      var origSrcDesc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');" +
            "      if (origSrcDesc && origSrcDesc.set) {" +
            "        var origSet = origSrcDesc.set;" +
            "        origSrcDesc.set = function(val) {" +
            "          report(val);" +
            "          return origSet.apply(this, arguments);" +
            "        };" +
            "        Object.defineProperty(HTMLMediaElement.prototype, 'src', origSrcDesc);" +
            "      }" +
            "    }" +
            "    document.querySelectorAll('video, audio').forEach(muteMedia);" +
            "    if (window.hls && window.hls.url) report(window.hls.url);" +
            "    if (window.dp && window.dp.video && window.dp.video.src) report(window.dp.video.src);" +
            "    if (window.player && window.player.url) report(window.player.url);" +
            "    if (!window._tvbox_obs && (document.documentElement || document.body)) {" +
            "      window._tvbox_obs = new MutationObserver(function() {" +
            "        document.querySelectorAll('video, audio').forEach(muteMedia);" +
            "      });" +
            "      window._tvbox_obs.observe(document.documentElement || document.body, { childList: true, subtree: true });" +
            "    }" +
            "  } catch(e) {}" +
            "})();";

    private class SniffBridge {
        @JavascriptInterface
        public void onVideoDetected(String detectedUrl) {
            if (TextUtils.isEmpty(detectedUrl) || mActivity == null || !isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (loadFoundCount.get() == 0 && checkVideoFormat(detectedUrl)) {
                    LOG.i("SniffBridge detected video: " + detectedUrl);
                    loadFoundVideoUrls.add(detectedUrl);
                    loadFoundVideoUrlsHeader.put(detectedUrl, new HashMap<>());
                    if (loadFoundCount.incrementAndGet() == 1) {
                        mHandler.removeMessages(100);
                        String cookie = CookieManager.getInstance().getCookie(detectedUrl);
                        HashMap<String, String> headers = new HashMap<>();
                        if (!TextUtils.isEmpty(cookie)) {
                            headers.put("Cookie", " " + cookie);
                        }
                        playUrl(detectedUrl, headers);
                        stopLoadWebView(true);
                    }
                }
            });
        }
    }

    class MyWebView extends WebView {
        public MyWebView(@NonNull Context context) {
            super(context);
        }

        @Override
        public void setOverScrollMode(int mode) {
            super.setOverScrollMode(mode);
            if (mContext instanceof Activity)
                AutoSize.autoConvertDensityOfCustomAdapt((Activity) mContext, PlayFragment.this);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return false;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configWebViewSys(WebView webView) {
        if (webView == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = Hawk.get(HawkConfig.DEBUG_OPEN, false)
                ? new ViewGroup.LayoutParams(800, 400) :
                new ViewGroup.LayoutParams(1, 1);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.clearFocus();
        webView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        requireActivity().addContentView(webView, layoutParams);
        /* 添加webView配置 */
        final WebSettings settings = webView.getSettings();
        settings.setNeedInitialFocus(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            settings.setBlockNetworkImage(false);
        } else {
            settings.setBlockNetworkImage(true);
        }
        settings.setUseWideViewPort(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
//        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        /* 添加webView配置 */
        //设置编码
        settings.setDefaultTextEncodingName("utf-8");
        settings.setUserAgentString(webView.getSettings().getUserAgentString());
//         settings.setUserAgentString(ANDROID_UA);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return false;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                return true;
            }
        });
        SysWebClient mSysWebClient = new SysWebClient();
        webView.setWebViewClient(mSysWebClient);
        webView.addJavascriptInterface(new SniffBridge(), "TvBoxBridge");
        webView.setBackgroundColor(Color.BLACK);
    }

    private class SysWebClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            view.evaluateJavascript(SNIFFER_MUTE_AND_HOOK_JS, null);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            view.evaluateJavascript(SNIFFER_MUTE_AND_HOOK_JS, null);
            String click = sourceBean.getClickSelector();
            LOG.i("onPageFinished url:" + url);

            if (!click.isEmpty()) {
                String selector;
                if (click.contains(";")) {
                    if (!url.contains(click.split(";")[0])) return;
                    selector = click.split(";")[1];
                } else {
                    selector = click.trim();
                }
                String js = "$(\"" + selector + "\").click();";
                LOG.i("javascript:" + js);
                mSysWebView.loadUrl("javascript:" + js);
            }
        }

        WebResourceResponse checkIsVideo(String url, HashMap<String, String> headers) {
            if (url.endsWith("/favicon.ico")) {
                if (url.startsWith("http://127.0.0.1")) {
                    return new WebResourceResponse("image/x-icon", "UTF-8", null);
                }
                return null;
            }

            boolean isFilter = VideoParseRuler.isFilter(webUrl, url);
            if (isFilter) {
                LOG.i("shouldInterceptLoadRequest filter:" + url);
                return null;
            }

            boolean ad;
            if (!loadedUrls.containsKey(url)) {
                ad = AdBlocker.isAd(url);
                loadedUrls.put(url, ad);
            } else {
                ad = Boolean.TRUE.equals(loadedUrls.get(url));
            }

            if (!ad) {
                if (checkVideoFormat(url)) {
                    loadFoundVideoUrls.add(url);
                    loadFoundVideoUrlsHeader.put(url, headers);
                    LOG.i("loadFoundVideoUrl:" + url);
                    if (loadFoundCount.incrementAndGet() == 1) {
                        url = loadFoundVideoUrls.poll();
                        mHandler.removeMessages(100);
                        String cookie = CookieManager.getInstance().getCookie(url);
                        if (!TextUtils.isEmpty(cookie))
                            headers.put("Cookie", " " + cookie);//携带cookie
                        playUrl(url, headers);
                        // 嗅探已完成: 销毁 WebView, 防止其后续导航重新装填嗅探超时定时器
                        // (旧实现只 stop 不 destroy, WebView 后续导航会再次 sendEmptyMessageDelayed(100))
                        stopLoadWebView(true);
                    }
                }
            }

            return ad || loadFoundCount.get() > 0 ?
                    AdBlocker.createEmptyResource() :
                    null;
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
//            WebResourceResponse response = checkIsVideo(url, new HashMap<>());
            return null;
        }

        @Nullable
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            LOG.i("shouldInterceptRequest url:" + url);
            HashMap<String, String> webHeaders = new HashMap<>();
            Map<String, String> hds = request.getRequestHeaders();
            if (hds != null && hds.keySet().size() > 0) {
                for (String k : hds.keySet()) {
                    if (k.equalsIgnoreCase("user-agent")
                            || k.equalsIgnoreCase("referer")
                            || k.equalsIgnoreCase("origin")) {
                        webHeaders.put(k, " " + hds.get(k));
                    }
                }
            }
            return checkIsVideo(url, webHeaders);
        }

        @Override
        public void onLoadResource(WebView webView, String url) {
            super.onLoadResource(webView, url);
            webView.evaluateJavascript(SNIFFER_MUTE_AND_HOOK_JS, null);
        }
    }

    /** 下载门控:播放器处于空闲/出错态(没有在加载播放)时,选集确认后需主动起播 */
    public boolean isPlayerIdleOrError() {
        if (mVideoView == null) return true;
        int state = mVideoView.getCurrentPlayState();
        return state == VideoView.STATE_IDLE || state == VideoView.STATE_ERROR;
    }

    public MyVideoView getPlayer() {
        return mVideoView;
    }
    public VodController getController() {
        return mController;
    }

}