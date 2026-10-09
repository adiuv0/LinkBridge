package com.emilianozazueta.linkbridge;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

final class LinkOutbox {
    private static final String PREFS_NAME = "linkbridge_outbox";
    private static final String ITEMS_KEY = "items";
    static final int MAX_ITEMS = 100;

    /** Where the serialized outbox lives; SharedPreferences on a device. */
    interface Store {
        String read();

        boolean write(String encoded);
    }

    private final Store store;

    LinkOutbox(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        store = new Store() {
            @Override
            public String read() {
                return preferences.getString(ITEMS_KEY, "[]");
            }

            @Override
            public boolean write(String encoded) {
                return preferences.edit().putString(ITEMS_KEY, encoded).commit();
            }
        };
    }

    LinkOutbox(Store store) {
        this.store = store;
    }

    synchronized List<LinkItem> all() {
        return deserialize(store.read());
    }

    synchronized LinkItem latest() {
        List<LinkItem> items = all();
        return items.isEmpty() ? null : items.get(items.size() - 1);
    }

    synchronized int count() {
        return all().size();
    }

    synchronized boolean isFull() {
        return count() >= MAX_ITEMS;
    }

    /**
     * Adds an item, or returns false without changing the outbox when it could
     * not be saved or the outbox is full. Queued links are never dropped
     * silently to make room.
     */
    synchronized boolean enqueue(LinkItem item) {
        List<LinkItem> items = all();
        items.removeIf(existing -> existing.id.equals(item.id));
        if (items.size() >= MAX_ITEMS) {
            return false;
        }
        items.add(item);
        return save(items);
    }

    synchronized LinkItem find(String id) {
        for (LinkItem item : all()) {
            if (item.id.equals(id)) return item;
        }
        return null;
    }

    synchronized boolean remove(String id) {
        List<LinkItem> items = all();
        boolean removed = items.removeIf(item -> item.id.equals(id));
        return !removed || save(items);
    }

    private boolean save(List<LinkItem> items) {
        return store.write(serialize(items));
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
        if (encoded == null || encoded.trim().isEmpty()) return new ArrayList<>();
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
