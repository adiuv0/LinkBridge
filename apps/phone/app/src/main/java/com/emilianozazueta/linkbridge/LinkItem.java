package com.emilianozazueta.linkbridge;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

final class LinkItem {
    static final int PROTOCOL_VERSION = 1;
    static final int MAX_URL_BYTES = 500;
    static final int MAX_METADATA_BYTES = 500;

    final String id;
    final String url;
    final String title;
    final long createdAt;

    LinkItem(String id, String url, String title, long createdAt) {
        this.id = id;
        this.url = url;
        this.title = cleanTitle(title, url);
        this.createdAt = createdAt;
    }

    static LinkItem create(String url, String title) {
        return new LinkItem(UUID.randomUUID().toString(), url.trim(), title, System.currentTimeMillis());
    }

    JSONObject toStorageJson() throws JSONException {
        return new JSONObject()
            .put("v", PROTOCOL_VERSION)
            .put("id", id)
            .put("url", url)
            .put("title", title)
            .put("createdAt", createdAt);
    }

    static LinkItem fromStorageJson(JSONObject json) throws JSONException {
        if (json.getInt("v") != PROTOCOL_VERSION) {
            throw new JSONException("Unsupported LinkBridge protocol version");
        }
        String id = json.getString("id").trim();
        String url = json.getString("url").trim();
        if (id.isEmpty() || !isHttpUrl(url)) {
            throw new JSONException("Invalid LinkBridge item");
        }
        return new LinkItem(
            id,
            url,
            json.optString("title", ""),
            json.optLong("createdAt", 0L));
    }

    String metadataJson(int remaining) throws JSONException {
        String candidateTitle = title;
        int urlBytes = url.getBytes(StandardCharsets.UTF_8).length;
        while (true) {
            // "urlBytes" lets the receiver detect a truncated URL read, and
            // "remaining" tells it how many more links are queued after this one.
            String encoded = new JSONObject()
                .put("v", PROTOCOL_VERSION)
                .put("id", id)
                .put("title", candidateTitle)
                .put("createdAt", createdAt)
                .put("urlBytes", urlBytes)
                .put("remaining", Math.max(0, remaining))
                .toString();
            if (encoded.getBytes(StandardCharsets.UTF_8).length <= MAX_METADATA_BYTES) {
                return encoded;
            }
            if (candidateTitle.isEmpty()) {
                throw new JSONException("Link metadata is too large");
            }
            int codePoints = candidateTitle.codePointCount(0, candidateTitle.length());
            int keep = Math.max(0, codePoints - 8);
            int end = candidateTitle.offsetByCodePoints(0, keep);
            candidateTitle = candidateTitle.substring(0, end).trim();
        }
    }

    static String cleanTitle(String rawTitle, String url) {
        String cleaned = rawTitle == null ? "" : rawTitle.replaceAll("\\s+", " ").trim();
        if (cleaned.equals(url)) cleaned = "";
        if (cleaned.isEmpty()) {
            try {
                String host = URI.create(url).getHost();
                if (host != null && !host.isEmpty()) {
                    cleaned = host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (cleaned.isEmpty()) cleaned = "Web link";
        int codePoints = cleaned.codePointCount(0, cleaned.length());
        if (codePoints > 160) {
            int end = cleaned.offsetByCodePoints(0, 157);
            cleaned = cleaned.substring(0, end).trim() + "…";
        }
        return cleaned;
    }

    static boolean isHttpUrl(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }
}
