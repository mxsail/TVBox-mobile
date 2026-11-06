package com.github.tvbox.osc.bean;

import java.io.Serializable;

public class LiveSource implements Serializable {
    /** 特殊标识：跟随当前主订阅源自带的直播 */
    public static final String FOLLOW_SUBSCRIPTION = "";

    private String name;
    private String url;
    private boolean isChecked;
    private boolean top;

    public LiveSource() {
    }

    public LiveSource(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public boolean isTop() {
        return top;
    }

    public void setTop(boolean top) {
        this.top = top;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public LiveSource setChecked(boolean checked) {
        isChecked = checked;
        return this;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
