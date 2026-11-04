package com.tvbox.sync.model;

import java.io.Serializable;

public class SyncCollect implements Serializable {
    public String id;
    public String configHash;
    public String sourceKey;
    public String vodId;
    public String title;
    public String pic;
    public int type;
    public long rev;
    public long updatedAt;
    public boolean deleted;

    public SyncCollect() {
    }
}
