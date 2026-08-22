package com.emilianozazueta.linkbridge;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LinkProtocolTest {
    @Test
    public void outboxRoundTripPreservesExactUrlAndUnicodeTitle() {
        String url = "https://example.com/Case?escaped=%2B%26%3D#Frag";
        LinkItem item = new LinkItem("item-1", url, "  A   title about Saïx  ", 1234L);

        String encoded = LinkOutbox.serialize(Arrays.asList(item));
        List<LinkItem> decoded = LinkOutbox.deserialize(encoded);

        assertEquals(1, decoded.size());
        assertEquals("item-1", decoded.get(0).id);
        assertEquals(url, decoded.get(0).url);
        assertEquals("A title about Saïx", decoded.get(0).title);
        assertEquals(1234L, decoded.get(0).createdAt);
    }

    @Test
    public void missingTitleFallsBackToHostname() {
        assertEquals(
            "example.com",
            LinkItem.cleanTitle("", "https://www.example.com/a/b"));
    }

    @Test
    public void metadataIsBoundedAndDoesNotExposeTheUrl() throws Exception {
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < 600; i++) title.append("é");
        LinkItem item = new LinkItem(
            "4db44560-8f3d-4d82-a6a6-99b246429aec",
            "https://secret.example/private-path",
            title.toString(),
            1234L);

        String metadata = item.metadataJson();
        JSONObject parsed = new JSONObject(metadata);

        assertTrue(metadata.getBytes(StandardCharsets.UTF_8).length <= LinkItem.MAX_METADATA_BYTES);
        assertEquals(LinkItem.PROTOCOL_VERSION, parsed.getInt("v"));
        assertEquals(item.id, parsed.getString("id"));
        assertFalse(metadata.contains(item.url));
    }

    @Test
    public void malformedOutboxEntriesAreSkippedWithoutLosingValidItems() {
        String encoded = "[{\"v\":1,\"id\":\"bad\",\"url\":\"not a URL\"}," +
            "{\"v\":1,\"id\":\"good\",\"url\":\"https://example.com\",\"title\":\"Example\"}]";

        List<LinkItem> decoded = LinkOutbox.deserialize(encoded);

        assertEquals(1, decoded.size());
        assertEquals("good", decoded.get(0).id);
    }
}
