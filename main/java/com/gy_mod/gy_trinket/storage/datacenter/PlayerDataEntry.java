package com.gy_mod.gy_trinket.storage.datacenter;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataEntry {

    private final Map<String, Object> data = new ConcurrentHashMap<>();

    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) data.getOrDefault(key, defaultValue);
    }

    public <T> void set(String key, T value) {
        if (value == null) {
            data.remove(key);
        } else {
            data.put(key, value);
        }
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    public void remove(String key) {
        data.remove(key);
    }

    public void clear() {
        data.clear();
    }

    public Map<String, Object> getAll() {
        return Collections.unmodifiableMap(data);
    }
}
