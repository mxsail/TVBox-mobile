package com.tvbox.sync.model;

import java.io.Serializable;

public class SyncProgress implements Serializable {
    public String key;
    public long position;
    public long duration;
    public long rev;
    public long updatedAt;
    public boolean deleted;

    public SyncProgress() {
    }

    public SyncProgress(String key, long position, long duration, long rev, long updatedAt, boolean deleted) {
        this.key = key;
        this.position = position;
        this.duration = duration;
        this.rev = rev;
        this.updatedAt = updatedAt;
        this.deleted = deleted;
    }
}
