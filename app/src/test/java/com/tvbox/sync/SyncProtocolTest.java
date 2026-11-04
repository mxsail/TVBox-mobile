package com.tvbox.sync;

import com.tvbox.sync.model.SyncSnapshot;
import org.junit.Assert;
import org.junit.Test;

public class SyncProtocolTest {

    @Test
    public void testNormalize() {
        Assert.assertEquals("https://example.com/api.json", SyncProtocol.normalize("  https://example.com/api.json  "));
        Assert.assertEquals("https://example.com/api.json", SyncProtocol.normalize("https://example.com/api.json;pk;123456"));
        Assert.assertEquals("", SyncProtocol.normalize(null));
    }

    @Test
    public void testConfigHash() {
        String hash1 = SyncProtocol.configHash("https://example.com/api.json");
        String hash2 = SyncProtocol.configHash("https://example.com/api.json;pk;test");
        Assert.assertEquals(8, hash1.length());
        Assert.assertEquals(hash1, hash2);
    }

    @Test
    public void testMakeId() {
        String id = SyncProtocol.makeId("https://example.com/api.json", "siteA", "1001");
        Assert.assertTrue(id.contains(":siteA:1001"));
    }

    @Test
    public void testValidateSpaceId() {
        Assert.assertTrue(SyncProtocol.validateSpaceId("default"));
        Assert.assertTrue(SyncProtocol.validateSpaceId("living-room_01"));
        Assert.assertFalse(SyncProtocol.validateSpaceId("."));
        Assert.assertFalse(SyncProtocol.validateSpaceId(".."));
        Assert.assertFalse(SyncProtocol.validateSpaceId("space/sub"));
        Assert.assertFalse(SyncProtocol.validateSpaceId("space\\sub"));
        Assert.assertFalse(SyncProtocol.validateSpaceId(""));
        Assert.assertFalse(SyncProtocol.validateSpaceId(null));
    }

    @Test
    public void testChecksum() {
        SyncSnapshot snapshot = new SyncSnapshot();
        snapshot.revision = 10;
        snapshot.spaceId = "default";
        snapshot.checksum = SyncProtocol.calculateChecksum(snapshot);

        Assert.assertTrue(SyncProtocol.verifyChecksum(snapshot));

        // Tamper test
        snapshot.revision = 11;
        Assert.assertFalse(SyncProtocol.verifyChecksum(snapshot));
    }
}
