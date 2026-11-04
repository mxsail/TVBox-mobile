package com.tvbox.sync;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SyncOpLog {

    public static class SyncOp implements Serializable {
        public long seq;
        public String spaceId;
        public String entity; // history, collect, progress, config.content, config.player, config.network, extras
        public String id;
        public String op; // upsert, delete, clear
        public String data;
        public long ts;

        public SyncOp() {
        }

        public SyncOp(long seq, String spaceId, String entity, String id, String op, String data, long ts) {
            this.seq = seq;
            this.spaceId = spaceId;
            this.entity = entity;
            this.id = id;
            this.op = op;
            this.data = data;
            this.ts = ts;
        }
    }

    public static final int MAX_UNACKED_OPS = 5000;

    private final List<SyncOp> ops = new ArrayList<SyncOp>();
    private long seqCounter = 0;

    public synchronized long nextSeq() {
        return ++seqCounter;
    }

    public synchronized void record(SyncOp op) {
        if (op == null) return;
        if (op.seq <= 0) {
            op.seq = nextSeq();
        } else if (op.seq > seqCounter) {
            seqCounter = op.seq;
        }

        // Compaction: deduplicate same (spaceId, entity, id) if not clear
        if (!"clear".equals(op.op) && op.id != null && !op.id.isEmpty()) {
            Iterator<SyncOp> it = ops.iterator();
            while (it.hasNext()) {
                SyncOp existing = it.next();
                if (op.spaceId.equals(existing.spaceId)
                        && op.entity.equals(existing.entity)
                        && op.id.equals(existing.id)) {
                    it.remove();
                }
            }
        }

        ops.add(op);

        // Cap limit to avoid infinite growth
        if (ops.size() > MAX_UNACKED_OPS) {
            // Trim oldest ops
            int excess = ops.size() - MAX_UNACKED_OPS;
            for (int i = 0; i < excess; i++) {
                ops.remove(0);
            }
        }
    }

    public synchronized List<SyncOp> readOps(String spaceId, long maxSeq) {
        List<SyncOp> result = new ArrayList<SyncOp>();
        for (SyncOp op : ops) {
            if (op.spaceId != null && op.spaceId.equals(spaceId)) {
                if (maxSeq <= 0 || op.seq <= maxSeq) {
                    result.add(op);
                }
            }
        }
        return result;
    }

    public synchronized void ack(String spaceId, long upToSeq) {
        Iterator<SyncOp> it = ops.iterator();
        while (it.hasNext()) {
            SyncOp op = it.next();
            if (op.spaceId != null && op.spaceId.equals(spaceId) && op.seq <= upToSeq) {
                it.remove();
            }
        }
    }

    public synchronized long getLatestSeq() {
        return seqCounter;
    }

    public synchronized int size() {
        return ops.size();
    }

    public synchronized void clear() {
        ops.clear();
    }
}
