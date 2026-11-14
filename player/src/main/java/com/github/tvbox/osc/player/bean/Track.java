package com.github.tvbox.osc.player.bean;

import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Track {

    private static final Map<String, List<Track>> sCache = new ConcurrentHashMap<>();

    private int id;
    private int type;
    private String key;
    private String name;
    private String format;
    private boolean selected;

    public Track() {
    }

    public Track(int type, String name, String format) {
        this.type = type;
        this.name = name;
        this.format = format;
    }

    public static List<Track> find(String key) {
        if (TextUtils.isEmpty(key)) return Collections.emptyList();
        List<Track> list = sCache.get(key);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    public static void delete(String key) {
        if (TextUtils.isEmpty(key)) return;
        sCache.remove(key);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public Track key(String key) {
        setKey(key);
        return this;
    }

    public Track toggle() {
        setSelected(!isSelected());
        return this;
    }

    public Track save() {
        if (TextUtils.isEmpty(getKey())) return this;
        sCache.computeIfAbsent(getKey(), k -> new ArrayList<>()).add(this);
        return this;
    }
}
