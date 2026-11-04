package com.tvbox.sync.model;

import java.io.Serializable;

public class SyncPlayer implements Serializable {
    public long rev;
    public String writer;
    public String profile;
    public int render;
    public int scale;
    public double speed = 1.0;
    public int subtitleSize = 24;
    public int subtitleDelay;
    public int timeStep = 1;
    public int background;
    public String codec;
    public boolean purify;
    public boolean ijkCache;

    public SyncPlayer() {
    }
}
