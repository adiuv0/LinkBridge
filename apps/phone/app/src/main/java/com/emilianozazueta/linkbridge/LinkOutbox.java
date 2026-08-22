package com.emilianozazueta.linkbridge;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LinkOutbox {
    private static final String PREFS_NAME = "linkbridge_outbox";
    private static final String ITEMS_KEY = "items";
    private static final int MAX_ITEMS = 100;

    private final SharedPreferences preferences;

    LinkOutbox(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    synchronized List<LinkItem> all() {
        return deserialize(preferences.getString(ITEMS_KEY, "[]"));
    }

    synchronized LinkItem latest() {
        List<LinkItem> items = all();
        return items.isEmpty() ? null : items.get(items.size() - 1);
    }

    synchronized int count() {
        return all().size();
    }

    synchronized boolean enqueue(LinkItem item) {
        List<LinkItem> items = all();
        items.removeIf(existing -> existing.id.equals(item.id));
        items.add(item);
        if (items.size() > MAX_ITEMS) {
            items = new ArrayList<>(items.subList(items.size() - MAX_ITEMS, items.size()));
        }
        return save(items);
    }

    synchronized boolean remove(String id) {
        List<LinkItem> items = all();
        boolean removed = items.removeIf(item -> item.id.equals(id));
        return !removed || save(items);
    }

    private boolean save(List<LinkItem> items) {
        return preferences.edit().putString(ITEMS_KEY, serialize(items)).commit();
    }

    static String serialize(List<LinkItem> items) {
        JSONArray json = new JSONArray();
        for (LinkItem item : items) {
            try {
                json.put(item.toStorageJson());
            } catch (JSONException ignored) {
            }
        }
        return json.toString();
    }

    static List<LinkItem> deserialize(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return Collections.emptyList();
        List<LinkItem> items = new ArrayList<>();
        try {
            JSONArray json = new JSONArray(encoded);
            for (int index = 0; index < json.length(); index++) {
                try {
                    items.add(LinkItem.fromStorageJson(json.getJSONObject(index)));
                } catch (JSONException ignored) {
                }
            }
        } catch (JSONException ignored) {
        }
        return items;
    }
}
