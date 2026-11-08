package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.PictureInPictureUiState;
import androidx.annotation.NonNull;
import com.github.tvbox.osc.player.controller.VodController;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Rational;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.ConvertUtils;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.NotificationUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.blankj.utilcode.util.ServiceUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseVbActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.CastVideo;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.DownloadEpisode;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.VodSnapshotStore;
import com.github.tvbox.osc.constant.IntentKey;
import com.github.tvbox.osc.databinding.ActivityDetailBinding;
import androidx.core.content.ContextCompat;
import com.github.tvbox.osc.download.DownloadTaskManager;
import com.github.tvbox.osc.event.DownloadEvent;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.receiver.BatteryReceiver;
import com.github.tvbox.osc.service.PlayService;
import com.github.tvbox.osc.ui.compose.DetailComposeBridge;
import com.github.tvbox.osc.ui.adapter.ParseAdapter;
import com.github.tvbox.osc.ui.adapter.SeriesAdapter;
import com.github.tvbox.osc.ui.adapter.SeriesFlagAdapter;
import com.github.tvbox.osc.ui.dialog.AllVodSeriesBottomDialog;
import com.github.tvbox.osc.ui.dialog.AllVodSeriesRightDialog;
import com.github.tvbox.osc.ui.dialog.CacheSelectDialog;
import com.github.tvbox.osc.ui.dialog.CastListDialog;
import com.github.tvbox.osc.ui.dialog.QuickSearchDialog;
import com.github.tvbox.osc.ui.dialog.VideoDetailDialog;
import com.github.tvbox.osc.ui.fragment.PlayFragment;
import com.github.tvbox.osc.ui.widget.LinearSpacingItemDecoration;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.ScreenShotListenManager;
import com.github.tvbox.osc.util.SearchHelper;
import com.github.tvbox.osc.util.SubtitleHelper;
import com.github.tvbox.osc.util.Utils;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.enums.PopupPosition;
import com.lxj.xpopup.interfaces.OnSelectListener;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */

public class DetailActivity extends BaseVbActivity<ActivityDetailBinding> {
    private PlayFragment playFragment = null;
    private SourceViewModel sourceViewModel;
    private Movie.Video mVideo;
    private VodInfo vodInfo;
    public SeriesFlagAdapter seriesFlagAdapter;
    public SeriesAdapter seriesAdapter;
    public String vodId;
    public String sourceKey;
    private View seriesFlagFocus = null;
    private boolean isReverse;
    private String preFlag = "";
    private HashMap<String, String> mCheckSources = null;
    BatteryReceiver mBatteryReceiver = new BatteryReceiver();
    //改为view模式无法自动响应返回键操作,onBackPress时手动dismiss
    private BasePopupView mAllSeriesRightDialog;
    private BasePopupView mAllSeriesBottomDialog;
    private DetailComposeBridge composeBridge;
    private BroadcastReceiver mRemoteActionReceiver;

    /**
     * 截屏监听
     */
    ScreenShotListenManager screenShotListenManager;

    /** fitsSystemWindows 已让内容避开手势条,不再叠加 BaseActivity 的默认底部避让 */
    @Override
    protected boolean autoNavigationBarInset() {
        return false;
    }

    @Override
    protected void init() {
        // 进入播放详情页即停掉遗留的后台播放服务: 上会话"后台播放开启"时划掉任务
        // 不会杀前台服务, 服务携带的旧视频会继续出声, 而新页面播放器还在加载,
        // 观感就是"加载中但后台已有声音"; 用户进入新播放页, 旧的后台播放理应让位
        PlayService.stop();
        initView();
        initViewModel();
        initData();
        registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        applySystemBarStyle();
        toggleScreenShotListen(true);
    }

    /** 系统栏样式(透明状态栏/暗色字体/透明导航栏+避让), init 与退出画中画共用 */
    private void applySystemBarStyle() {
        ImmersionBar.with(this)
                .transparentStatusBar()
                .statusBarDarkFont(!com.github.tvbox.osc.util.Utils.isDarkTheme())
                .navigationBarColor(android.R.color.transparent)
                .fitsSystemWindows(true)
                .init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playFragment != null && playFragment.getController() != null
                && !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode())) {
            // 确实不在画中画才清标记, 避免与进入回调顺序耦合导致 InPipMode 被误翻
            playFragment.getController().setInPipMode(false);
        }
        updatePipParams();
    }

    private void initView() {
        mBinding.previewPlayerPlace.setVisibility(showPreview ? View.VISIBLE : View.GONE);
        windowsPreview = mBinding.previewPlayer.getLayoutParams();
        windowsFull = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        // 预览窗与占位坑位初始即按标准 16:9(屏宽x9/16)布局: 起播后仅按实际比例微调,
        // 不再从固定 250dp 跳变, 播放器下方的详情内容从首帧起就贴住视频底部
        int initPreviewH = (int) ((long) ScreenUtils.getScreenWidth() * 9 / 16);
        windowsPreview.height = initPreviewH;
        mBinding.previewPlayer.setLayoutParams(windowsPreview);
        syncPreviewPlaceHeight();
        mBinding.previewPlayer.post(this::updatePipParams);

        seriesAdapter = new SeriesAdapter(false);
        seriesFlagAdapter = new SeriesFlagAdapter();
        isReverse = false;
        preFlag = "";
        if (showPreview) {
            playFragment = new PlayFragment();
            getSupportFragmentManager().beginTransaction().add(R.id.previewPlayer, playFragment).commit();
            getSupportFragmentManager().beginTransaction().show(playFragment).commitAllowingStateLoss();
        }

        composeBridge = new DetailComposeBridge(mBinding.composeDetail, new DetailComposeBridge.Callbacks() {
            @Override
            public void onDetailClick() {
                if (vodInfo != null) {
                    new XPopup.Builder(DetailActivity.this).hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
                            .isViewMode(true)
                            .asCustom(new VideoDetailDialog(DetailActivity.this, vodInfo))
                            .show();
                }
            }

            @Override
            public void onChangeSourceClick() {
                startQuickSearch();
                QuickSearchDialog quickSearchDialog = new QuickSearchDialog(DetailActivity.this);
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, quickSearchData));
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, quickSearchWord));
                quickSearchDialog.show();
                if (pauseRunnable != null && pauseRunnable.size() > 0) {
                    searchExecutorService = Executors.newFixedThreadPool(5);
                    for (Runnable runnable : pauseRunnable) {
                        searchExecutorService.execute(runnable);
                    }
                    pauseRunnable.clear();
                    pauseRunnable = null;
                }
                quickSearchDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        try {
                            if (searchExecutorService != null) {
                                pauseRunnable = searchExecutorService.shutdownNow();
                                searchExecutorService = null;
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }
                });
            }

            @Override
            public void onDownloadClick() {
                showCacheSelectDialog();
            }

            @Override
            public void onCollectClick() {
                if (vodInfo == null) return;
                boolean isVodCollect = RoomDataManger.isVodCollect(sourceKey, vodId);
                if (isVodCollect) {
                    RoomDataManger.deleteVodCollect(sourceKey, vodInfo);
                    Toast.makeText(DetailActivity.this, "已移除收藏夹", Toast.LENGTH_SHORT).show();
                    composeBridge.updateCollectState(false);
                } else {
                    RoomDataManger.insertVodCollect(sourceKey, vodInfo);
                    Toast.makeText(DetailActivity.this, "已加入收藏夹", Toast.LENGTH_SHORT).show();
                    composeBridge.updateCollectState(true);
                }
            }

            @Override
            public void onPrivateBrowsingClick() {
                ToastUtils.showShort("当前为无痕浏览");
            }

            @Override
            public void onLineSelected(int position) {
                chooseFlag(position);
            }

            @Override
            public void onEpisodeClick(int position) {
                chooseSeries(position, false);
            }

            @Override
            public void onToggleSort() {
                sortSeries();
            }
        });

        seriesFlagAdapter.setOnItemClickListener((adapter, view, position) -> {
            chooseFlag(position);
        });

        seriesAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                chooseSeries(position, false);
            }
        });

        setLoadSir(mBinding.llLayout);
    }

    // 后台播放/画中画只由 onUserLeaveHint(应用切后台)触发:
    // onPause 在打开其他页面与返回退出时同样回调, 在这里起服务会导致
    // 返回退出时 startForegroundService 已排队、onDestroy 的 stop 拦不住 → 后台继续播

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (playFragment == null || playFragment.getPlayer() == null) {
            return;
        }
        int bgType = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0);
        boolean isPlaying = playFragment.getPlayer().isPlaying();
        if (bgType == 2 && isPlaying) {
            prepareForPip();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInPictureInPictureMode()) {
                enterPip();
            }
        } else if (bgType == 1 && isPlaying) {
            playServerSwitch(true);
        }
    }

    /**
     * 排序(倒序)
     */
    public void sortSeries() {
        if (vodInfo != null && vodInfo.seriesMap.size() > 0) {
            vodInfo.reverseSort = !vodInfo.reverseSort;
            isReverse = !isReverse;
            vodInfo.reverse();
            vodInfo.playIndex = (vodInfo.seriesMap.get(vodInfo.playFlag).size() - 1) - vodInfo.playIndex;
//                    insertVod(sourceKey, vodInfo);

            seriesAdapter.notifyDataSetChanged();
            refreshCachedBadge();
        }
    }

    public void showCastDialog() {

        VodInfo.VodSeries vodSeries = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex);
        new XPopup.Builder(this).hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
                .maxWidth(ConvertUtils.dp2px(360))
                .asCustom(new CastListDialog(this, new CastVideo(vodSeries.name
                        , TextUtils.isEmpty(playFragment.getFinalUrl()) ? vodSeries.url : playFragment.getFinalUrl())))
                .show();
    }

    public void showAllSeriesDialog() {
        if (fullWindows) {
            mAllSeriesRightDialog = new XPopup.Builder(this).hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
                    .isViewMode(true)//隐藏导航栏(手势条)在dialog模式下会闪一下,改为view模式,但需处理onBackPress的隐藏,下方同理
                    .popupHeight(ScreenUtils.getScreenHeight())
                    .popupPosition(PopupPosition.Right)
                    .enableDrag(false)//禁用拖拽,内部有横向rv
                    .asCustom(new AllVodSeriesRightDialog(this));
            mAllSeriesRightDialog.show();
        } else {
            if (composeBridge != null) {
                composeBridge.showAllEpisodesDialog();
            }
        }
    }

    private void chooseFlag(int position) {
        //新选中的flag
        String newFlag = seriesFlagAdapter.getData().get(position).name;
        if (vodInfo != null && !vodInfo.playFlag.equals(newFlag)) {
            for (int i = 0; i < vodInfo.seriesFlags.size(); i++) {//遍历flag集合
                VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(i);
                if (flag.name.equals(vodInfo.playFlag)) {//取消当前播放的选中状态
                    flag.selected = false;
                    seriesFlagAdapter.notifyItemChanged(i);
                    break;
                }
            }
            //新选中的flag
            VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(position);
            flag.selected = true;
            //清除上一个线路集数的选中状态
            List<VodInfo.VodSeries> currentSeriesList = vodInfo.seriesMap.get(vodInfo.playFlag);
            if (currentSeriesList.size() > vodInfo.playIndex) {//有效集数
                currentSeriesList.get(vodInfo.playIndex).selected = false;
            }
            vodInfo.playFlag = newFlag;
            seriesFlagAdapter.notifyItemChanged(position);
            refreshList();
            if (composeBridge != null) {
                composeBridge.updateLines(vodInfo.seriesFlags, position);
            }
            chooseSeries(vodInfo.playIndex, true);
        }
    }

    private void chooseSeries(int position, boolean reloadWithChangeLine) {
        if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
            boolean reload = false;
            for (int j = 0; j < vodInfo.seriesMap.get(vodInfo.playFlag).size(); j++) {
                seriesAdapter.getData().get(j).selected = false;
                seriesAdapter.notifyItemChanged(j);
            }
            //解决倒叙不刷新
            if (vodInfo.playIndex != position) {
                seriesAdapter.getData().get(position).selected = true;
                seriesAdapter.notifyItemChanged(position);
                vodInfo.playIndex = position;

                reload = true;
            }
            //解决当前集不刷新的BUG
            if (!preFlag.isEmpty() && !vodInfo.playFlag.equals(preFlag)) {
                reload = true;
            }

            seriesAdapter.getData().get(vodInfo.playIndex).selected = true;
            seriesAdapter.notifyItemChanged(vodInfo.playIndex);
            refreshCachedBadge();

            //选集全屏 想选集不全屏的注释下面一行
            if (!showPreview || reload || reloadWithChangeLine) {
                jumpToPlay();
            }
        }
    }

    private void initCheckedSourcesForSearch() {
        mCheckSources = SearchHelper.getSourcesForSearch();
    }

    private List<Runnable> pauseRunnable = null;

    private void jumpToPlay() {
        if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
            preFlag = vodInfo.playFlag;
            //更新播放地址
            Bundle bundle = new Bundle();
            //保存历史
            insertVod(sourceKey, vodInfo);
            bundle.putString("sourceKey", sourceKey);
//            bundle.putSerializable("VodInfo", vodInfo);
            App.getInstance().setVodInfo(vodInfo);
            if (previewVodInfo == null) {
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(bos);
                    oos.writeObject(vodInfo);
                    oos.flush();
                    oos.close();
                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()));
                    previewVodInfo = (VodInfo) ois.readObject();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (previewVodInfo != null) {
                previewVodInfo.playerCfg = vodInfo.playerCfg;
                previewVodInfo.playFlag = vodInfo.playFlag;
                previewVodInfo.playIndex = vodInfo.playIndex;
                previewVodInfo.seriesMap = vodInfo.seriesMap;
//                    bundle.putSerializable("VodInfo", previewVodInfo);
                App.getInstance().setVodInfo(previewVodInfo);
            }
            playFragment.setData(bundle);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    void refreshList() {
        int seriesSize = vodInfo.seriesMap.get(vodInfo.playFlag).size();
        if (seriesSize > 0 && seriesSize <= vodInfo.playIndex) {//当前集数大于新选线路的总集数,设置为最后一集
            vodInfo.playIndex = seriesSize - 1;
        }

        if (vodInfo.seriesMap.get(vodInfo.playFlag) != null) {
            boolean canSelect = true;
            for (int j = 0; j < vodInfo.seriesMap.get(vodInfo.playFlag).size(); j++) {
                if (vodInfo.seriesMap.get(vodInfo.playFlag).get(j).selected) {
                    canSelect = false;
                    break;
                }
            }
            if (canSelect)
                vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).selected = true;
        }
        seriesAdapter.setNewData(vodInfo.seriesMap.get(vodInfo.playFlag));
        refreshCachedBadge();
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.detailResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                    mVideo = absXml.movie.videoList.get(0);
                    vodInfo = new VodInfo();
                    vodInfo.setVideo(mVideo);
                    vodInfo.sourceKey = mVideo.sourceKey;
                    // 详情/缓存/播放时机落地快照,离线秒开与下载页直达都依赖它
                    VodSnapshotStore.save(vodInfo);
                    applySeriesData();
                } else {
                    // 快照已渲染(离线/源暂时不可用): 保留当前界面,不回落空态
                    if (!hydratedFromSnapshot) {
                        showEmpty();
                        mBinding.previewPlayer.setVisibility(View.GONE);
                    }
                }
            }
        });
    }

    /**
     * 详情渲染公共后段(详情接口成功 / 快照兜底共用):
     * 头部信息 + 历史进度合并 + 线路/选集渲染 + (可选)预览起播。
     */
    private void applySeriesData() {
        if (vodInfo == null || vodInfo.seriesMap == null) return;
        showSuccess();
        renderHeader();
        if (vodInfo.seriesMap.size() > 0) {//线路
            if (composeBridge != null) {
                composeBridge.setEmptyPlaylist(false);
            }

            VodInfo vodInfoRecord = RoomDataManger.getVodInfo(sourceKey, vodId);
            // 读取历史记录
            if (vodInfoRecord != null) {
                vodInfo.playIndex = Math.max(vodInfoRecord.playIndex, 0);
                vodInfo.playFlag = vodInfoRecord.playFlag;
                vodInfo.playerCfg = vodInfoRecord.playerCfg;
                vodInfo.reverseSort = vodInfoRecord.reverseSort;
            } else {
                vodInfo.playIndex = 0;
                vodInfo.playFlag = null;
                vodInfo.playerCfg = "";
                vodInfo.reverseSort = false;
            }

            if (vodInfo.reverseSort) {
                vodInfo.reverse();
            }

            if (vodInfo.playFlag == null || !vodInfo.seriesMap.containsKey(vodInfo.playFlag))
                vodInfo.playFlag = (String) vodInfo.seriesMap.keySet().toArray()[0];

            updateFlagSelection();

            refreshList();
            if (autoPlayFromCache) {
                // 下载页直达: 直接跳指定集,不再先起播历史集(避免二次起播)
                handleAutoPlayFromCache();
            } else if (showPreview && !autoJumpDone) {
                // 详情异步刷新到达时快照路径已起播过, 不得重新 jumpToPlay——
                // 否则播放器 release 重载, 可见地"刷新"一次且打断续播
                jumpWhenPlayerReady();
            }
            // startQuickSearch();
        } else {//空布局
            if (composeBridge != null) {
                composeBridge.setEmptyPlaylist(true);
            }
        }
    }

    /** 线路栏选中态 + 数据重绑 */
    private void updateFlagSelection() {
        if (vodInfo.seriesFlags == null) return;
        int flagScrollTo = 0;
        for (int j = 0; j < vodInfo.seriesFlags.size(); j++) {
            VodInfo.VodSeriesFlag flag = vodInfo.seriesFlags.get(j);
            if (flag.name.equals(vodInfo.playFlag)) {
                flagScrollTo = j;
                flag.selected = true;
            } else {
                flag.selected = false;
            }
        }
        //设置线路数据
        seriesFlagAdapter.setNewData(vodInfo.seriesFlags);
        if (composeBridge != null) {
            composeBridge.updateLines(vodInfo.seriesFlags, flagScrollTo);
        }
    }

    /** 头部海报/名称/元信息(详情接口与快照共用,字段统一走 vodInfo) */
    private void renderHeader() {
        SourceBean sourceBean = ApiConfig.get().getSource(vodInfo.sourceKey);
        String srcName = sourceBean == null ? null : sourceBean.getName();
        List<String> metaList = new ArrayList<>();
        if (vodInfo.year > 0) {
            metaList.add("年份: " + vodInfo.year);
        }
        if (!TextUtils.isEmpty(vodInfo.area)) {
            metaList.add("地区: " + vodInfo.area.trim());
        }
        if (!TextUtils.isEmpty(vodInfo.type)) {
            metaList.add("类型: " + vodInfo.type.trim());
        }
        if (!TextUtils.isEmpty(vodInfo.lang)) {
            metaList.add("语言: " + vodInfo.lang.trim());
        }
        String subInfo = TextUtils.join("   ", metaList);
        boolean isPrivate = Hawk.get(HawkConfig.PRIVATE_BROWSING, false);

        if (composeBridge != null) {
            composeBridge.updateVideoInfo(
                    TextUtils.isEmpty(vodInfo.name) ? "暂无信息" : vodInfo.name,
                    TextUtils.isEmpty(srcName) ? "未知" : srcName,
                    subInfo,
                    isPrivate
            );
            boolean isVodCollect = RoomDataManger.isVodCollect(sourceKey, vodId);
            composeBridge.updateCollectState(isVodCollect);
        }
    }

    /** 下载页直达: 按 rawUrl 在线路上定位指定集并起播(线路不同则切换线路) */
    private void handleAutoPlayFromCache() {
        autoPlayFromCache = false;
        if (vodInfo == null || TextUtils.isEmpty(autoPlayUrl)) return;
        String flag = vodInfo.playFlag;
        int index = indexOfSeriesUrl(vodInfo.seriesMap.get(flag), autoPlayUrl);
        if (index < 0) {
            for (java.util.Map.Entry<String, List<VodInfo.VodSeries>> entry : vodInfo.seriesMap.entrySet()) {
                int i = indexOfSeriesUrl(entry.getValue(), autoPlayUrl);
                if (i >= 0) {
                    flag = entry.getKey();
                    index = i;
                    break;
                }
            }
        }
        autoPlayUrl = null;
        if (index < 0) return;
        vodInfo.playFlag = flag;
        vodInfo.playIndex = index;
        updateFlagSelection();
        refreshList();
        jumpWhenPlayerReady();
    }

    /**
     * 预览起播需 PlayFragment 视图就绪: 快照路径在 init() 内同步执行,
     * fragment 事务可能尚未跑完,post 一帧兜底(网络路径本来就是异步,不受影响)。
     */
    /**
     * 预览窗固定 16:9(宽=屏宽, 高=宽x9/16, initView 设置), 与主流播放应用一致:
     * 非 16:9 内容由 doikki 等比 letterbox 在容器内, 不做动态高度调整
     * (动态 resize 对 SurfaceView 有撕 surface 风险, 且比例跳变体验差)。
     * windowsPreview 与容器 LayoutParams 是同一对象, 画中画退出恢复沿用同一高度。
     */

    /** 占位坑位与预览窗同高: 坑位不跟着缩, 播放器下方的详情内容(标题/简介/选集)不会贴上来 */
    private void syncPreviewPlaceHeight() {
        if (windowsPreview == null) return;
        ViewGroup.LayoutParams lp = mBinding.previewPlayerPlace.getLayoutParams();
        if (lp != null && lp.height != windowsPreview.height) {
            lp.height = windowsPreview.height;
            mBinding.previewPlayerPlace.setLayoutParams(lp);
        }
    }

    private void jumpWhenPlayerReady() {
        // autoJumpDone: 已起播过; jumpPending: post 在途——防快照/网络两条路径各入队一个跳转
        if (autoJumpDone || jumpPending) return;
        if (playFragment != null && playFragment.getView() != null) {
            doPreviewJump();
        } else if (mBinding != null) {
            jumpPending = true;
            mBinding.getRoot().post(() -> {
                jumpPending = false;
                doPreviewJump();
            });
        }
    }

    private void doPreviewJump() {
        if (isFinishing() || isDestroyed()) return;
        autoJumpDone = true;
        jumpToPlay();
        mBinding.previewPlayer.setVisibility(View.VISIBLE);
        toggleSubtitleTextSize();
    }

    private int indexOfSeriesUrl(List<VodInfo.VodSeries> list, String url) {
        if (list == null || url == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            VodInfo.VodSeries series = list.get(i);
            if (series != null && url.equals(series.url)) return i;
        }
        return -1;
    }

    private String getHtml(String label, String content) {
        if (content == null) {
            content = "";
        }
        return label + "<font color=\"#FFFFFF\">" + content + "</font>";
    }

    private void initData() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            // 下载页"复用在线播放页"直达指定集: 快照渲染后按 rawUrl 定位并起播
            autoPlayFromCache = bundle.getBoolean("autoPlay", false);
            autoPlayUrl = bundle.getString("playUrl", null);
            loadDetail(bundle.getString("id", null), bundle.getString("sourceKey", ""));
        }
    }

    private void loadDetail(String vid, String key) {
        if (vid != null) {
            vodId = vid;
            sourceKey = key;
            // 每次 loadDetail(进页/换源)重新允许一次自动起播; 详情异步刷新不重跳
            autoJumpDone = false;
            // 快照秒开: 本地有详情快照(含老下载兜底)先渲染界面,详情接口异步刷新覆盖
            if (!tryHydrateFromSnapshot()) {
                showLoading();
            }
            sourceViewModel.getDetail(sourceKey, vodId);
            boolean isVodCollect = RoomDataManger.isVodCollect(sourceKey, vodId);
            if (composeBridge != null) {
                composeBridge.updateCollectState(isVodCollect);
            }
        }
    }

    /** 快照已渲染过界面(详情接口失败时不再回落空态) */
    private boolean hydratedFromSnapshot = false;
    /** 下载页直达: 快照渲染后定位到指定集并起播 */
    private boolean autoPlayFromCache = false;
    private String autoPlayUrl = null;
    /** 本次加载是否已自动起播过(快照秒开/在线详情只跳一次, 异步刷新不重跳) */
    private boolean autoJumpDone = false;
    /** jumpWhenPlayerReady 的 post 是否在途(防快照/网络两条路径各入队一个跳转) */
    private boolean jumpPending = false;

    /**
     * 离线秒开: 从详情快照或下载任务兜底数据渲染详情页。
     * 成功返回 true,后续在线详情到达后经 applySeriesData 覆盖刷新。
     */
    private boolean tryHydrateFromSnapshot() {
        try {
            VodInfo snap = VodSnapshotStore.load(sourceKey, vodId);
            if (snap == null) {
                List<DownloadEpisode> eps = RoomDataManger.getAllDownloadEpisodes();
                snap = VodSnapshotStore.buildFromEpisodes(sourceKey, vodId, eps);
            }
            if (snap == null || snap.seriesMap == null || snap.seriesMap.isEmpty()) {
                return false;
            }
            hydratedFromSnapshot = true;
            vodInfo = snap;
            applySeriesData();
            return true;
        } catch (Throwable th) {
            th.printStackTrace();
            return false;
        }
    }

    /** 角标只在任务状态变化时刷新:进度事件每秒 2-3 次,每次都做主线程
     *  Room 查询 + 集数网格全量重绑,是下载期间页面卡顿的来源之一 */
    private long lastBadgeRefreshTime = 0;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadEvent(DownloadEvent event) {
        if (event.type == DownloadEvent.TYPE_PROGRESS) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBadgeRefreshTime < 1000) {
            return;
        }
        lastBadgeRefreshTime = now;
        refreshCachedBadge();
    }

    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_REFRESH) {
            if (event.obj != null) {
                if (event.obj instanceof Integer) {
                    int index = (int) event.obj;
                    for (int j = 0; j < vodInfo.seriesMap.get(vodInfo.playFlag).size(); j++) {
                        seriesAdapter.getData().get(j).selected = false;
                        seriesAdapter.notifyItemChanged(j);
                    }
                    seriesAdapter.getData().get(index).selected = true;
                    seriesAdapter.notifyItemChanged(index);
                    vodInfo.playIndex = index;
                    //保存历史
                    insertVod(sourceKey, vodInfo);
                    refreshCachedBadge();
                } else if (event.obj instanceof JSONObject) {
                    vodInfo.playerCfg = ((JSONObject) event.obj).toString();
                    //保存历史
                    insertVod(sourceKey, vodInfo);
                }

            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_SELECT) {
            if (event.obj != null) {
                Movie.Video video = (Movie.Video) event.obj;
                loadDetail(video.id, video.sourceKey);
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_WORD_CHANGE) {
            if (event.obj != null) {
                String word = (String) event.obj;
                switchSearchWord(word);
            }
        } else if (event.type == RefreshEvent.TYPE_QUICK_SEARCH_RESULT) {
            try {
                searchData(event.obj == null ? null : (AbsXml) event.obj);
            } catch (Exception e) {
                searchData(null);
            }
        }
    }

    private String searchTitle = "";
    private boolean hadQuickStart = false;
    private final List<Movie.Video> quickSearchData = new ArrayList<>();
    private final List<String> quickSearchWord = new ArrayList<>();
    private ExecutorService searchExecutorService = null;

    private void switchSearchWord(String word) {
        OkGo.getInstance().cancelTag("quick_search");
        quickSearchData.clear();
        searchTitle = word;
        searchResult();
    }

    private void startQuickSearch() {
        initCheckedSourcesForSearch();
        if (hadQuickStart)
            return;
        hadQuickStart = true;
        OkGo.getInstance().cancelTag("quick_search");
        quickSearchWord.clear();
        // 快照兜底打开时 mVideo 可能为空,标题从 vodInfo 取
        searchTitle = mVideo != null && mVideo.name != null ? mVideo.name
                : (vodInfo != null && vodInfo.name != null ? vodInfo.name : "");
        quickSearchData.clear();
        quickSearchWord.addAll(SearchHelper.splitWords(searchTitle));
        // 分词
        OkGo.<String>get("http://api.pullword.com/get.php?source=" + URLEncoder.encode(searchTitle) + "&param1=0&param2=0&json=1")
                .tag("fenci")
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
                            for (JsonElement je : new Gson().fromJson(json, JsonArray.class)) {
                                quickSearchWord.add(je.getAsJsonObject().get("t").getAsString());
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                        List<String> words = new ArrayList<>(new HashSet<>(quickSearchWord));
                        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_WORD, words));
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                    }
                });

        searchResult();
    }

    private void searchResult() {
        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        searchExecutorService = Executors.newFixedThreadPool(5);
        List<SourceBean> searchRequestList = new ArrayList<>();
        searchRequestList.addAll(ApiConfig.get().getSourceBeanList());
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        searchRequestList.remove(home);
        searchRequestList.add(0, home);

        ArrayList<String> siteKey = new ArrayList<>();
        for (SourceBean bean : searchRequestList) {
            if (!bean.isSearchable() || !bean.isQuickSearch()) {
                continue;
            }
            if (mCheckSources != null && !mCheckSources.containsKey(bean.getKey())) {
                continue;
            }
            siteKey.add(bean.getKey());
        }
        for (String key : siteKey) {
            searchExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    sourceViewModel.getQuickSearch(key, searchTitle);
                }
            });
        }
    }

    private void searchData(AbsXml absXml) {
        if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
            List<Movie.Video> data = new ArrayList<>();
            for (Movie.Video video : absXml.movie.videoList) {
                // 去除当前相同的影片
                if (video.sourceKey.equals(sourceKey) && video.id.equals(vodId))
                    continue;
                data.add(video);
            }
            quickSearchData.addAll(data);
            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH, data));
        }
    }

    private void insertVod(String sourceKey, VodInfo vodInfo) {
        if (Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) {//无痕浏览
            return;
        }
        try {
            vodInfo.playNote = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).name;
        } catch (Throwable th) {
            vodInfo.playNote = "";
        }
        RoomDataManger.insertVodRecord(sourceKey, vodInfo);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_HISTORY_REFRESH));
    }

    @Override
    protected void onDestroy() {
        // 播放页销毁即停媒体通知服务: 返回退出直接停; 系统后台回收时 Fragment 视图
        // 已随之销毁、播放早已终止, 停服务避免僵尸"正在播放"通知
        PlayService.stop();
        registerActionReceiver(false);
        super.onDestroy();
        unregisterReceiver(mBatteryReceiver);
        toggleScreenShotListen(false);

        try {
            if (searchExecutorService != null) {
                searchExecutorService.shutdownNow();
                searchExecutorService = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        OkGo.getInstance().cancelTag("fenci");
        OkGo.getInstance().cancelTag("detail");
        OkGo.getInstance().cancelTag("quick_search");
        toggleScreenShotListen(false);
    }

    // 播放页返回层级复杂(选集弹窗/播放控制层/全屏预览), 预见性模式下也保持自行拦截
    @Override
    protected boolean keepBackIntercept() {
        return true;
    }

    @Override
    protected void onBackHandled() {
        if (mAllSeriesRightDialog != null && mAllSeriesRightDialog.isShow()) {
            mAllSeriesRightDialog.dismiss();
            return;
        }
        if (mAllSeriesBottomDialog != null && mAllSeriesBottomDialog.isShow()) {
            mAllSeriesBottomDialog.dismiss();
            return;
        }
        if (composeBridge != null) {
            composeBridge.dismissAllEpisodesDialog();
        }
        if (playFragment != null && playFragment.hideAllDialogSuccess()) {//fragment有弹窗隐藏并拦截返回
            return;
        }
        if (fullWindows) {
            toggleFullPreview();
            return;
        }
        finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null && playFragment != null) {
            // 媒体键(蓝牙耳机/键盘)任何布局形态都转发给播放器;
            // 方向键/OK 仅全屏转发, 竖屏预览下保留详情页自身的焦点导航
            int keyCode = event.getKeyCode();
            boolean mediaKey = keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                    || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    || keyCode == KeyEvent.KEYCODE_MEDIA_STOP;
            if ((fullWindows || mediaKey) && playFragment.dispatchKeyEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // preview
    VodInfo previewVodInfo = null;
    boolean showPreview = Hawk.get(HawkConfig.SHOW_PREVIEW, true);
    ; // true 开启 false 关闭
    boolean fullWindows = false;
    private boolean wasFullscreenBeforePip = false;
    ViewGroup.LayoutParams windowsPreview = null;
    ViewGroup.LayoutParams windowsFull = null;

    public void toggleFullPreview() {
        if (windowsPreview == null) {
            windowsPreview = mBinding.previewPlayer.getLayoutParams();
        }
        if (windowsFull == null) {//全屏尺寸
            windowsFull = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        fullWindows = !fullWindows;

        //交由fragment处理播放器全屏逻辑
        playFragment.changedLandscape(fullWindows);
        //activity处理预览尺寸(全屏/非全屏预览)
        mBinding.previewPlayer.setLayoutParams(fullWindows ? windowsFull : windowsPreview);
        if (!fullWindows) {
            syncPreviewPlaceHeight();
        }
        mBinding.llLayout.setVisibility(fullWindows ? View.GONE : View.VISIBLE);
        toggleSubtitleTextSize();
        updatePipParams();
    }

    void toggleSubtitleTextSize() {
        int subtitleTextSize = SubtitleHelper.getTextSize(this);
        if (!fullWindows) {
            subtitleTextSize *= 0.6;
        }
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SUBTITLE_SIZE_CHANGE, subtitleTextSize));
    }

    /**
     * 应用内缓存选集弹窗
     */
    public void showCacheSelectDialog() {
        if (vodInfo == null || vodInfo.seriesMap == null || vodInfo.seriesMap.get(vodInfo.playFlag) == null
                || vodInfo.seriesMap.get(vodInfo.playFlag).isEmpty()) {
            ToastUtils.showShort("资源异常,请稍后重试");
            return;
        }
        HashSet<String> cachedUrls = getCachedUrls();
        new XPopup.Builder(this).hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
                .isViewMode(true)
                .maxHeight(ScreenUtils.getScreenHeight() - (ScreenUtils.getScreenHeight() / 4))
                .asCustom(new CacheSelectDialog(this, vodInfo, cachedUrls))
                .show();
    }

    private HashSet<String> getCachedUrls() {
        // "已占用"口径: 除失败外全部状态(下载中/等待/暂停)都算, 否则选集弹窗把下载中的集
        // 显示为可选, 重复入队产生重复任务
        HashSet<String> cachedUrls = new HashSet<>();
        try {
            for (DownloadEpisode ep : RoomDataManger.getDownloadEpisodes(sourceKey, vodInfo.id, vodInfo.playFlag)) {
                if (ep.status != DownloadEpisode.STATUS_FAILED && ep.rawUrl != null) {
                    cachedUrls.add(ep.rawUrl);
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return cachedUrls;
    }

    /** 下载门控:本剧已有任意一集成功开始播放(首帧)后,选集确认才直接入队 */
    private boolean playbackStarted = false;
    /** 播放开始前确认的缓存选集,等首帧后自动入队 */
    private final List<DownloadEpisode> pendingDownloadTasks = new ArrayList<>();

    /**
     * 缓存选集确认入口:开始播放后才会自动开始下载。
     * 已有集播放过→直接入队;否则先暂存,等 PlayFragment 首帧回调后自动入队。
     */
    public void queueDownloadAfterPlay(List<DownloadEpisode> tasks) {
        if (playbackStarted || playFragment == null) {
            DownloadTaskManager.get().enqueue(tasks);
            // 立即刷新选集"已下载"角标与弹窗可选态, 否则重开弹窗仍显示未下载
            refreshCachedBadge();
            ToastUtils.showShort("已加入下载队列,可在通知栏查看进度");
            return;
        }
        pendingDownloadTasks.addAll(tasks);
        ToastUtils.showShort(String.format("开始播放后将自动开始下载(%d集)", tasks.size()));
        // 播放器空闲/出错(没有在加载)时主动起播当前集;正在加载则等它自己起播
        if (playFragment.isPlayerIdleOrError()) {
            jumpToPlay();
        }
    }

    /** PlayFragment 首帧回调:任何一集真正开始播放,暂存的缓存任务一次性入队 */
    public void onPlaybackStarted() {
        playbackStarted = true;
        updatePipParams();
        // 前台播放即常驻媒体通知(标题/进度/海报), 后台播放与画中画沿用同一服务;
        // 每次首帧都重绑(服务可能被直播页占用, 静态 videoView 需切回本播放器)
        playServerSwitch(true);
        if (pendingDownloadTasks.isEmpty()) return;
        List<DownloadEpisode> tasks = new ArrayList<>(pendingDownloadTasks);
        pendingDownloadTasks.clear();
        DownloadTaskManager.get().enqueue(tasks);
        refreshCachedBadge();
        ToastUtils.showShort(String.format("已开始播放,自动开始下载(%d集)", tasks.size()));
    }

    /**
     * 刷新集数列表的"已下载"角标
     */
    public void refreshCachedBadge() {
        if (vodInfo == null || vodInfo.seriesMap == null
                || vodInfo.seriesMap.get(vodInfo.playFlag) == null) return;
        if (seriesAdapter != null) {
            seriesAdapter.setCachedUrls(getCachedUrls());
            seriesAdapter.notifyDataSetChanged();
        }
        if (composeBridge != null) {
            composeBridge.updateEpisodes(
                    vodInfo.seriesMap.get(vodInfo.playFlag),
                    vodInfo.playIndex,
                    vodInfo.reverseSort,
                    getCachedUrls()
            );
        }
    }

    public void use1DMDownload() {
        if (vodInfo != null && vodInfo.seriesMap.get(vodInfo.playFlag).size() > 0) {
            VodInfo.VodSeries vod = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex);
            String url = TextUtils.isEmpty(playFragment.getFinalUrl()) ? vod.url : playFragment.getFinalUrl();
            // 创建Intent对象，启动1DM App
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setDataAndType(Uri.parse(url), "video/mp4");
            intent.putExtra("title", vodInfo.name + " " + vod.name); // 传入文件保存名
//            intent.setClassName("idm.internet.download.manager.plus", "idm.internet.download.manager.MainActivity");
            intent.setClassName("idm.internet.download.manager.plus", "idm.internet.download.manager.Downloader");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // 检查1DM App是否已安装
            PackageManager pm = getPackageManager();
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
            boolean isIntentSafe = activities.size() > 0;

            if (isIntentSafe) {
                startActivity(intent); // 启动1DM App
            } else {
                // 如果1DM App未安装，提示用户安装1DM App
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("请先安装1DM+下载管理器");
                builder.setMessage("为了下载视频，请先安装1DM+下载管理器。是否现在安装？");
                builder.setPositiveButton("立即下载", new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        // 跳转到下载链接
                        Intent downloadIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://od.lk/d/MzRfMTg0NTcxMDdf/1DM _v15.6.apk"));
                        startActivity(downloadIntent);
                    }
                });
                builder.setNegativeButton("取消", null);
                builder.show();
            }
        } else {
            ToastUtils.showShort("资源异常,请稍后重试");
        }
    }

    /**
     * 计算视频画面在当前屏幕中的实际显示区域（去除播放器容器上下或左右的黑边）
     */
    private Rect calculateVideoContentRect() {
        if (playFragment != null && playFragment.getPlayer() != null) {
            View renderView = playFragment.getPlayer().getRenderView();
            if (renderView != null && renderView.isShown()) {
                Rect renderRect = new Rect();
                if (renderView.getGlobalVisibleRect(renderRect) && !renderRect.isEmpty()) {
                    return renderRect;
                }
            }
        }
        Rect viewRect = new Rect();
        if (!mBinding.previewPlayer.getGlobalVisibleRect(viewRect) || viewRect.isEmpty()) {
            return null;
        }
        return viewRect;
    }

    /**
     * 构建画中画参数 (比例、操作按钮、精确纯画面动画裁切、Android 12+ 手势自动进入)
     */
    private PictureInPictureParams buildPipParams() {
        if (!Utils.supportsPiPMode()) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
            int vWidth = 0;
            int vHeight = 0;
            if (playFragment != null && playFragment.getPlayer() != null) {
                int[] size = playFragment.getPlayer().getVideoSize();
                if (size != null && size.length >= 2) {
                    vWidth = size[0];
                    vHeight = size[1];
                }
            }
            Rational ratio;
            if (vWidth > 0 && vHeight > 0) {
                double r = (double) vWidth / (double) vHeight;
                if (r > 2.39) {
                    ratio = new Rational(239, 100);
                } else if (r < 1.0 / 2.39) {
                    ratio = new Rational(100, 239);
                } else {
                    ratio = new Rational(vWidth, vHeight);
                }
            } else {
                ratio = new Rational(16, 9);
            }
            builder.setAspectRatio(ratio);

            Rect videoRect = calculateVideoContentRect();
            if (videoRect != null && !videoRect.isEmpty()) {
                builder.setSourceRectHint(videoRect);
            }

            List<RemoteAction> actions = new ArrayList<>();
            actions.add(generateRemoteAction(android.R.drawable.ic_media_previous, IntentKey.BROADCAST_ACTION_PREV, "上一集", "Play Previous"));
            boolean isPlaying = playFragment != null && playFragment.getPlayer() != null && playFragment.getPlayer().isPlaying();
            int playIcon = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
            actions.add(generateRemoteAction(playIcon, IntentKey.BROADCAST_ACTION_PLAYPAUSE, isPlaying ? "暂停" : "播放", "Play/Pause"));
            actions.add(generateRemoteAction(android.R.drawable.ic_media_next, IntentKey.BROADCAST_ACTION_NEXT, "下一集", "Play Next"));
            builder.setActions(actions);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                int bgType = Hawk.get(HawkConfig.BACKGROUND_PLAY_TYPE, 0);
                builder.setAutoEnterEnabled(bgType == 2 && isPlaying);
            }
            return builder.build();
        }
        return null;
    }

    /**
     * 更新画中画参数(播放状态/比例变动时同步系统)
     */
    public void updatePipParams() {
        if (!Utils.supportsPiPMode()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                PictureInPictureParams params = buildPipParams();
                if (params != null) {
                    setPictureInPictureParams(params);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 画中画模式
     */
    public void enterPip() {
        if (!Utils.supportsPiPMode()) return;
        prepareForPip();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                PictureInPictureParams params = buildPipParams();
                if (params != null) {
                    enterPictureInPictureMode(params);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void prepareForPip() {
        if (playFragment != null) {
            playFragment.hideAllDialogSuccess();
            VodController controller = playFragment.getController();
            if (controller != null) {
                controller.setInPipMode(true);
            }
        }
    }

    @Override
    public void onPictureInPictureUiStateChanged(PictureInPictureUiState pipState) {
        super.onPictureInPictureUiStateChanged(pipState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            if (pipState.isTransitioningToPip()) {
                prepareForPip();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private RemoteAction generateRemoteAction(int iconResId, int actionCode, String title, String desc) {
        Intent actionIntent = new Intent(IntentKey.BROADCAST_ACTION);
        actionIntent.putExtra("action", actionCode);
        actionIntent.setPackage(getPackageName());
        final PendingIntent intent =
                PendingIntent.getBroadcast(
                        DetailActivity.this,
                        actionCode,
                        actionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        final Icon icon = Icon.createWithResource(DetailActivity.this, iconResId);
        return (new RemoteAction(icon, title, desc, intent));
    }

    /**
     * 事件接收广播(画中画/后台播放点击事件)
     * @param isRegister 注册/注销
     */
    private void registerActionReceiver(boolean isRegister) {
        if (isRegister) {
            if (mRemoteActionReceiver != null) {
                return;
            }
            mRemoteActionReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || !IntentKey.BROADCAST_ACTION.equals(intent.getAction()) || playFragment == null || playFragment.getController() == null) {
                        return;
                    }

                    int currentStatus = intent.getIntExtra("action", 1);
                    if (currentStatus == IntentKey.BROADCAST_ACTION_PREV) {
                        playFragment.playPrevious();
                    } else if (currentStatus == IntentKey.BROADCAST_ACTION_PLAYPAUSE) {
                        playFragment.getController().togglePlay();
                        updatePipParams();
                    } else if (currentStatus == IntentKey.BROADCAST_ACTION_NEXT) {
                        playFragment.playNext(false);
                    } else if (currentStatus == IntentKey.BROADCAST_ACTION_SEEK) {
                        // 通知栏媒体卡片拖动进度条
                        if (playFragment.getPlayer() != null) {
                            playFragment.getPlayer().seekTo(intent.getLongExtra(IntentKey.BROADCAST_ACTION_POSITION, 0));
                        }
                    } else if (currentStatus == IntentKey.BROADCAST_ACTION_CLOSE) {
                        playServerSwitch(false);
                        finish();
                        NotificationUtils.cancelAll();
                    }
                }
            };
            ContextCompat.registerReceiver(this, mRemoteActionReceiver,
                    new IntentFilter(IntentKey.BROADCAST_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED);
        } else {
            if (mRemoteActionReceiver != null) {
                try {
                    unregisterReceiver(mRemoteActionReceiver);
                } catch (Exception ignored) {
                }
                mRemoteActionReceiver = null;
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        handlePipModeChanged(isInPictureInPictureMode);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        handlePipModeChanged(isInPictureInPictureMode);
    }

    private void handlePipModeChanged(boolean isInPip) {
        if (isInPip) {
            wasFullscreenBeforePip = fullWindows;
            prepareForPip();
            // 画中画模式：强制去除系统栏占位、避让及内边距，让纯视频铺满画中画窗口
            ImmersionBar.with(this)
                    .hideBar(BarHide.FLAG_HIDE_BAR)
                    .fitsSystemWindows(false)
                    .init();
            View content = findViewById(android.R.id.content);
            if (content != null) {
                content.setPadding(0, 0, 0, 0);
            }
            mBinding.getRoot().setPadding(0, 0, 0, 0);
            mBinding.previewPlayer.setPadding(0, 0, 0, 0);

            mBinding.llLayout.setVisibility(View.GONE);
            mBinding.previewPlayer.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            registerActionReceiver(true);
        } else {
            applySystemBarStyle();
            if (playFragment != null && playFragment.getController() != null) {
                playFragment.getController().setInPipMode(false);
            }
            // 退出画中画恢复时：严格根据进画中画前的状态（竖屏 vs 全屏）恢复
            if (windowsPreview == null) {
                windowsPreview = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ConvertUtils.dp2px(250));
            }
            if (windowsFull == null) {
                windowsFull = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            fullWindows = wasFullscreenBeforePip;
            mBinding.previewPlayer.setLayoutParams(fullWindows ? windowsFull : windowsPreview);
            if (!fullWindows) {
                syncPreviewPlaceHeight();
            }
            mBinding.llLayout.setVisibility(fullWindows ? View.GONE : View.VISIBLE);
            if (playFragment != null) {
                playFragment.changedLandscape(fullWindows);
            }
            toggleSubtitleTextSize();
            registerActionReceiver(false);
            // 退出画中画后媒体通知可能仍常驻(前台/后台同一服务), 接收器必须重新挂上,
            // 否则通知栏播放/上下一集按钮全部失效
            if (ServiceUtils.isServiceRunning(PlayService.class)) {
                registerActionReceiver(true);
            }
            updatePipParams();
        }
    }

    /**
     * 后台播放服务开关,开启时注册操作广播,关闭时注销
     */
    private void playServerSwitch(boolean open) {
        if (open) {
            VodInfo.VodSeries vod = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex);
            PlayService.start(playFragment.getPlayer(), vodInfo.name + "&&" + vod.name + "&&"
                    + (vodInfo.pic == null ? "" : vodInfo.pic));
            registerActionReceiver(true);
        } else {
            if (ServiceUtils.isServiceRunning(PlayService.class)) {
                PlayService.stop();
                registerActionReceiver(false);
            }
        }
    }

    public String getCurrentVodUrl() {
        return playFragment == null ? "" : playFragment.getFinalUrl();
    }

    public void quickLineChange() {
        List<VodInfo.VodSeriesFlag> flags = seriesFlagAdapter.getData();
        if (flags.size() > 1) {
            int currentIndex = 0;
            for (int i = 0; i < flags.size(); i++) {
                if (flags.get(i).selected) {
                    currentIndex = i;
                }
            }
            currentIndex += 1;
            if (currentIndex >= flags.size()) {
                currentIndex = 0;
            }
            chooseFlag(currentIndex);
            // chooseFlag 尾部已同步 chooseSeries(vodInfo.playIndex, true) 起播,
            // 固定 300ms 延时的重复 chooseSeries 在换线路慢于 300ms 时会把选集
            // 落到旧线路数据上, 删除
        }
    }

    public void showParseRoot(boolean show, ParseAdapter adapter) {
        mBinding.rvParse.setAdapter(adapter);
        int defaultIndex = 0;
        for (int i = 0; i < adapter.getData().size(); i++) {
            if (adapter.getData().get(i).isDefault()) {
                defaultIndex = i;
                break;
            }
        }
        if (defaultIndex != 0) {
            mBinding.rvParse.scrollToPosition(defaultIndex);
        }
        mBinding.parseRoot.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void toggleScreenShotListen(boolean open) {
        if (open){
            if (screenShotListenManager == null){
                screenShotListenManager = ScreenShotListenManager.newInstance(this);
            }
            screenShotListenManager.setListener(imagePath -> {

                if (playFragment.getPlayer().isInPlaybackState())return;

                new XPopup.Builder(this).hasBlurBg(com.github.tvbox.osc.view.MaterialBarHelper.popupBlurEnabled())
                        .isDarkTheme(Utils.isDarkTheme())
                        .asCenterList("",new String[]{"跳转阿狸","跳转优汐","跳转夸父","关闭"}, null, (position, text) -> {
                            String pkg = "";
                            String cls = "";
                            switch (position){
                                case 0:
                                    pkg = "com.alicloud.databox";
                                    cls = "com.alicloud.databox.launcher.splash.SplashActivity";
                                    break;
                                case 1:
                                    pkg = "com.UCMobile";
                                    cls = "com.uc.browser.InnerUCMobile";
                                    break;
                                case 2:
                                    pkg = "com.quark.browser";
                                    cls = "com.ucpro.MainActivity";
                                    break;
                                case 3:
                                    return;
                            }
                            try {
                                startActivity(new Intent().setComponent(new ComponentName(pkg, cls)));
                            }catch (Exception e){
                                ToastUtils.showShort("未找到应用");
                            }
                        })
                        .show();
            });
            screenShotListenManager.startListen();
        }else {
            if (screenShotListenManager != null) {
                screenShotListenManager.stopListen();
            }
        }
    }
}
