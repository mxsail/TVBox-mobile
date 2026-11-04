package com.tvbox.sync;

import com.google.gson.Gson;
import com.tvbox.sync.model.SyncClear;
import com.tvbox.sync.model.SyncConfigGroup;
import com.tvbox.sync.model.SyncHistory;
import com.tvbox.sync.model.SyncSnapshot;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SyncMergerTest {
    private static final Gson GSON = new Gson();

    @Test
    public void testFirstTimeMergeCreatesSnapshot() {
        List<SyncOpLog.SyncOp> ops = new ArrayList<SyncOpLog.SyncOp>();

        SyncHistory h = new SyncHistory();
        h.id = "hash:site:1";
        h.title = "Test Movie";
        h.vodId = "1";

        ops.add(new SyncOpLog.SyncOp(1, "default", "history", h.id, "upsert", GSON.toJson(h), 1000L));

        SyncSnapshot merged = SyncMerger.merge(null, ops, "dev1", "mobile", "MyPhone", 1000L);

        Assert.assertNotNull(merged);
        Assert.assertEquals(1L, merged.revision);
        Assert.assertEquals("dev1", merged.writer);
        Assert.assertEquals(1, merged.history.size());
        Assert.assertEquals("Test Movie", merged.history.get(0).title);
        Assert.assertTrue(SyncProtocol.verifyChecksum(merged));
    }

    @Test
    public void testIncrementalMergeAndTombstone() {
        SyncSnapshot cloud = new SyncSnapshot();
        cloud.revision = 5;

        SyncHistory h1 = new SyncHistory();
        h1.id = "hash:site:1";
        h1.title = "Movie 1";
        h1.rev = 2;
        cloud.history.add(h1);

        List<SyncOpLog.SyncOp> ops = new ArrayList<SyncOpLog.SyncOp>();
        // Delete Movie 1
        ops.add(new SyncOpLog.SyncOp(1, "default", "history", "hash:site:1", "delete", null, 2000L));

        SyncSnapshot merged = SyncMerger.merge(cloud, ops, "dev2", "tv", "LivingRoomTV", 2000L);

        Assert.assertEquals(6L, merged.revision);
        Assert.assertEquals(1, merged.history.size());
        Assert.assertTrue(merged.history.get(0).deleted);
    }

    @Test
    public void testGlobalClearHistory() {
        SyncSnapshot cloud = new SyncSnapshot();
        cloud.revision = 10;

        SyncHistory h1 = new SyncHistory();
        h1.id = "hash:site:1";
        h1.rev = 5;
        cloud.history.add(h1);

        List<SyncOpLog.SyncOp> ops = new ArrayList<SyncOpLog.SyncOp>();
        ops.add(new SyncOpLog.SyncOp(1, "default", "history", null, "clear", null, 3000L));

        SyncSnapshot merged = SyncMerger.merge(cloud, ops, "dev1", "mobile", "MyPhone", 3000L);

        Assert.assertEquals(11L, merged.revision);
        Assert.assertEquals(1, merged.history.size());
        Assert.assertTrue(merged.history.get(0).deleted);
        Assert.assertNotNull(merged.clears.get("history"));
        Assert.assertEquals(11L, merged.clears.get("history").rev);
    }

    @Test
    public void testConfigContentLwwDoesNotResurrectDeleted() {
        SyncSnapshot cloud = new SyncSnapshot();
        cloud.revision = 5;

        SyncConfigGroup.ContentGroup cloudContent = new SyncConfigGroup.ContentGroup();
        cloudContent.rev = 3;
        cloudContent.apiUrl = "https://old.com/api.json";
        cloudContent.subscriptions.add(new SyncConfigGroup.SubscriptionItem("OldSource", "https://old.com", true, false, false));
        cloudContent.subscriptions.add(new SyncConfigGroup.SubscriptionItem("DeletedSource", "https://deleted.com", false, false, false));
        cloud.config.groups.put("content", cloudContent);

        // Mobile deletes "DeletedSource" and updates content group
        SyncConfigGroup.ContentGroup newContent = new SyncConfigGroup.ContentGroup();
        newContent.apiUrl = "https://new.com/api.json";
        newContent.subscriptions.add(new SyncConfigGroup.SubscriptionItem("OldSource", "https://old.com", true, false, false));
        // Note: DeletedSource is removed!

        List<SyncOpLog.SyncOp> ops = new ArrayList<SyncOpLog.SyncOp>();
        ops.add(new SyncOpLog.SyncOp(1, "default", "config.content", null, "upsert", GSON.toJson(newContent), 4000L));

        SyncSnapshot merged = SyncMerger.merge(cloud, ops, "dev1", "mobile", "MyPhone", 4000L);

        SyncConfigGroup.ContentGroup mergedContent = (SyncConfigGroup.ContentGroup) merged.config.groups.get("content");
        Assert.assertEquals(6L, merged.revision);
        Assert.assertEquals("https://new.com/api.json", mergedContent.apiUrl);
        Assert.assertEquals(1, mergedContent.subscriptions.size());
        Assert.assertEquals("OldSource", mergedContent.subscriptions.get(0).name);
    }
}
