package com.tvbox.sync.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncSnapshot implements Serializable {
    public String schema = "tvbox.sync/1";
    public String spaceId = "default";
    public long revision;
    public long updatedAt;
    public String writer;
    public String checksum;
    public Map<String, SyncDevice> devices = new HashMap<String, SyncDevice>();
    public SyncConfigGroup config = new SyncConfigGroup();
    public List<SyncHistory> history = new ArrayList<SyncHistory>();
    public List<SyncCollect> collects = new ArrayList<SyncCollect>();
    public List<SyncProgress> progress = new ArrayList<SyncProgress>();
    public Map<String, SyncClear> clears = new HashMap<String, SyncClear>();

    public SyncSnapshot() {
    }
}
