package com.tvbox.sync.model;

import java.io.Serializable;

public class SyncClear implements Serializable {
    public long rev;
    public String writer;
    public long updatedAt;

    public SyncClear() {
    }

    public SyncClear(long rev, String writer, long updatedAt) {
        this.rev = rev;
        this.writer = writer;
        this.updatedAt = updatedAt;
    }
}
