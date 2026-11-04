package com.tvbox.sync.model;

import java.io.Serializable;

public class SyncDevice implements Serializable {
    public String name;
    public String platform;
    public long lastSeen;
    public long lastAckRevision;

    public SyncDevice() {
    }

    public SyncDevice(String name, String platform, long lastSeen, long lastAckRevision) {
        this.name = name;
        this.platform = platform;
        this.lastSeen = lastSeen;
        this.lastAckRevision = lastAckRevision;
    }
}
