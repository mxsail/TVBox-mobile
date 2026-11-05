package com.github.tvbox.osc.event;

/**
 * 外观设置变化广播, 订阅方(如 MainActivity/SettingActivity)按类型刷新。
 */
public class AppearanceChangedEvent {
    /** 样式级变化(界面材质/悬浮底栏/底栏滚动隐藏): 实时重排即可, 无需重建页面 */
    public static final int TYPE_STYLE = 0;
    /** 主题级变化(主题颜色/自定义字体): Activity 主题覆盖层已变, 订阅方应 recreate 重新套用 */
    public static final int TYPE_RECREATE = 1;

    public int type;

    public AppearanceChangedEvent() {
        this(TYPE_STYLE);
    }

    public AppearanceChangedEvent(int type) {
        this.type = type;
    }
}
