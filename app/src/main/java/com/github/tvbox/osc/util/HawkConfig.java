package com.github.tvbox.osc.util;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class HawkConfig {
    public static final String API_URL = "api_url";
    public static final String LIVE_URL = "live_url";
    public static final String LIVE_SOURCES = "live_sources";
    public static final String LIVE_SOURCE_SELECTED = "live_source_selected";
    public static final String EPG_URL = "epg_url";
    public static final String SHOW_PREVIEW = "show_preview";
    public static final String SUBSCRIPTIONS = "api_history";
    public static final String LIVE_HISTORY = "live_history";
    public static final String EPG_HISTORY = "epg_history";
    public static final String HOME_API = "home_api";
    public static final String DEFAULT_PARSE = "parse_default";
    public static final String DEBUG_OPEN = "debug_open";
    public static final String IJK_CODEC = "ijk_codec";
    public static final String PLAY_TYPE = "play_type";//0 系统 1 ijk 2 exo 10 MXPlayer
    public static final String PLAY_RENDER = "play_render"; //0 texture 2
    public static final String PLAY_SCALE = "play_scale"; //0 texture 2
    public static final String PLAY_TIME_STEP = "play_time_step"; //0 texture 2
    public static final String DOH_URL = "doh_url";
    /**
     * 0 豆瓣热播 1 数据源推荐 2 关闭主页
     */
    public static final String HOME_REC = "home_rec";
    public static final String HISTORY_NUM = "history_num";
    public static final String LIVE_CHANNEL = "last_live_channel_name";
    public static final String LIVE_CHANNEL_REVERSE = "live_channel_reverse";
    public static final String LIVE_CROSS_GROUP = "live_cross_group";
    public static final String LIVE_CONNECT_TIMEOUT = "live_connect_timeout";
    public static final String LIVE_SHOW_NET_SPEED = "live_show_net_speed";
    public static final String LIVE_SHOW_TIME = "live_show_time";
    public static final String FAST_SEARCH_MODE = "fast_search_mode";
    public static final String SUBTITLE_TEXT_SIZE = "subtitle_text_size";
    public static final String SUBTITLE_TIME_DELAY = "subtitle_time_delay";
    public static final String SOURCES_FOR_SEARCH = "checked_sources_for_search";
    public static final String NOW_DATE = "now_date"; //当前日期
    public static final String REMOTE_TVBOX = "remote_tvbox_host";
    public static final String IJK_CACHE_PLAY = "ijk_cache_play";
    /**
     * 无痕浏览
     */
    public static final String PRIVATE_BROWSING = "private_browsing";
    /**
     * 主题,跟随系统0,浅1,深2
     */
    public static final String THEME_TAG = "theme_tag";
    /**
     * 主题颜色: dynamic=动态取色(Android 12+), 其余为预设色 id(见 ThemeManager.PRESETS)
     */
    public static final String THEME_COLOR = "theme_color";
    /**
     * 界面材质: solid实底/半透明translucent/高斯模糊blur/液态玻璃liquid(见 ThemeManager.MATERIAL_*)
     */
    public static final String UI_MATERIAL = "ui_material";
    /**
     * 液态玻璃微调配置项: 模糊半径 / 折射高度 / 折射强度 / 色散开关
     */
    public static final String LIQUID_BLUR_RADIUS = "liquid_blur_radius";
    public static final String LIQUID_REFRACTION_HEIGHT = "liquid_refraction_height";
    public static final String LIQUID_REFRACTION_AMOUNT = "liquid_refraction_amount";
    public static final String LIQUID_DISPERSION = "liquid_dispersion";
    /**
     * 自定义字体文件路径(空串=系统默认)
     */
    public static final String FONT_PATH = "font_path";
    /**
     * 预见性返回手势(API 33+ 系统预览动画)
     */
    public static final String PREDICTIVE_BACK = "predictive_back";
    /**
     * 悬浮底栏(HyperOS 风格胶囊)
     */
    public static final String BAR_FLOATING = "bar_floating";
    /**
     * 底栏显示文字名称(false=纯图标)
     */
    public static final String BAR_SHOW_LABELS = "bar_show_labels";
    /**
     * 底栏随滚动隐藏(上滑收起/下滑呼出)
     */
    public static final String BAR_AUTO_HIDE = "bar_auto_hide";
    /**
     * 后台播放模式 0 关闭,1 开启,2 画中画
     */
    public static final String BACKGROUND_PLAY_TYPE = "background_play_type";
    /**
     * 广告过滤
     */
    public static final String VIDEO_PURIFY = "video_purify";
    /**
     * 长按的倍速播放设置
     */
    public static final String VIDEO_SPEED = "video_speed";
    /**
     * 搜索记录
     */
    public static final String HISTORY_SEARCH = "history_search";
    /**
     * 应用内下载:仅Wi-Fi下载
     */
    public static final String DOWNLOAD_WIFI_ONLY = "download_wifi_only";
    /**
     * 应用内下载:已同意免责声明
     */
    public static final String DOWNLOAD_DISCLAIMER_AGREED = "download_disclaimer_agreed";
    /**
     * 应用内下载:已请求过电池优化白名单(只弹一次)
     */
    public static final String DOWNLOAD_BATTERY_PROMPTED = "download_battery_prompted";

    // --- 云同步 tvbox.sync/1 ---
    public static final String SYNC_ENABLED = "sync_enabled";
    public static final String SYNC_BACKEND = "sync_backend";
    public static final String SYNC_URL = "sync_url";
    public static final String SYNC_USER = "sync_user";
    public static final String SYNC_PASSWORD = "sync_password";
    public static final String SYNC_SPACE_ID = "sync_space_id";
    public static final String SYNC_AUTO = "sync_auto";
    public static final String SYNC_WIFI_ONLY = "sync_wifi_only";
    public static final String SYNC_SCOPE_CONFIG = "sync_scope_config";
    public static final String SYNC_SCOPE_HISTORY = "sync_scope_history";
    public static final String SYNC_SCOPE_COLLECT = "sync_scope_collect";
    public static final String SYNC_SCOPE_PROGRESS = "sync_scope_progress";
    public static final String SYNC_SCOPE_PLAYER_SETTINGS = "sync_scope_player_settings";
    public static final String SYNC_DEVICE_ID = "sync_device_id";
    public static final String SYNC_DEVICE_NAME = "sync_device_name";
}