package com.tvbox.sync.model;

import java.io.Serializable;

public class SyncHistory implements Serializable {
    public String id;
    public String configHash;
    public String sourceKey;
    public String vodId;
    public String title;
    public String pic;
    public String playFlag;
    public int playIndex;
    public String playNote;
    public long position;
    public long rev;
    public long updatedAt;
    public boolean deleted;
    public String payload;

    public SyncHistory() {
    }
}
