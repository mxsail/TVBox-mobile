package com.github.tvbox.osc.constant;

public class IntentKey {

    /**
     * 画中画/后台服务
     */
    public static final String BROADCAST_ACTION = "VOD_CONTROL";
    public static final int BROADCAST_ACTION_PREV = 0;
    public static final int BROADCAST_ACTION_PLAYPAUSE = 1;
    public static final int BROADCAST_ACTION_NEXT = 2;
    public static final int BROADCAST_ACTION_CLOSE = 3;
    /** 通知栏媒体卡片拖动进度: extra "position" = 目标毫秒 */
    public static final int BROADCAST_ACTION_SEEK = 4;
    public static final String BROADCAST_ACTION_POSITION = "position";
    public static final String CACHE_CONFIG_CHANGED = "use_cache";
}