package com.github.tvbox.osc.util.live;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TxtSubscribe {

    private static final Pattern M3U_GROUP_PATTERN = Pattern.compile("group-title=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern M3U_NAME_PATTERN = Pattern.compile("tvg-name=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    public static void parse(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        if (str == null || str.trim().isEmpty()) {
            return;
        }
        if (str.startsWith("\uFEFF")) {
            str = str.substring(1);
        }
        if (str.contains("#EXTM3U") || str.contains("#EXTINF")) {
            parseM3u(linkedHashMap, str);
        } else {
            parseTxt(linkedHashMap, str);
        }
    }

    private static void parseM3u(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        try (BufferedReader bufferedReader = new BufferedReader(new StringReader(str))) {
            String readLine;
            String currentGroup = "其它";
            String currentName = null;

            while ((readLine = bufferedReader.readLine()) != null) {
                String line = readLine.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#EXTM3U")) {
                    continue;
                }
                if (line.startsWith("#EXTGRP:")) {
                    String grp = line.substring(8).trim();
                    if (!grp.isEmpty()) {
                        currentGroup = grp;
                    }
                    continue;
                }
                if (line.startsWith("#EXTINF")) {
                    currentGroup = "其它";
                    Matcher groupMatcher = M3U_GROUP_PATTERN.matcher(line);
                    if (groupMatcher.find()) {
                        String g = groupMatcher.group(1);
                        if (g != null && !g.trim().isEmpty()) {
                            currentGroup = g.trim();
                        }
                    }

                    int commaIndex = line.lastIndexOf(',');
                    if (commaIndex != -1 && commaIndex < line.length() - 1) {
                        currentName = line.substring(commaIndex + 1).trim();
                    }
                    if (currentName == null || currentName.isEmpty()) {
                        Matcher nameMatcher = M3U_NAME_PATTERN.matcher(line);
                        if (nameMatcher.find()) {
                            String n = nameMatcher.group(1);
                            if (n != null && !n.trim().isEmpty()) {
                                currentName = n.trim();
                            }
                        }
                    }
                    continue;
                }

                // 播放链接行（非注释）
                if (!line.startsWith("#") && isValidLiveUrl(line)) {
                    String name = (currentName != null && !currentName.isEmpty()) ? currentName : "未知频道";
                    String group = (currentGroup != null && !currentGroup.isEmpty()) ? currentGroup : "其它";

                    LinkedHashMap<String, ArrayList<String>> groupMap = linkedHashMap.computeIfAbsent(group, k -> new LinkedHashMap<>());
                    ArrayList<String> urls = groupMap.computeIfAbsent(name, k -> new ArrayList<>());
                    if (!urls.contains(line)) {
                        urls.add(line);
                    }
                    currentName = null; // 消费后重置
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private static void parseTxt(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        ArrayList<String> arrayList;
        try (BufferedReader bufferedReader = new BufferedReader(new StringReader(str))) {
            String readLine;
            LinkedHashMap<String, ArrayList<String>> defaultGroupMap = new LinkedHashMap<>();
            LinkedHashMap<String, ArrayList<String>> currentGroupMap = defaultGroupMap;

            while ((readLine = bufferedReader.readLine()) != null) {
                String line = readLine.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] split = line.split(",", 2);
                if (split.length < 2) {
                    continue;
                }
                if (line.contains("#genre#")) {
                    String groupName = split[0].trim();
                    if (!linkedHashMap.containsKey(groupName)) {
                        currentGroupMap = new LinkedHashMap<>();
                        linkedHashMap.put(groupName, currentGroupMap);
                    } else {
                        currentGroupMap = linkedHashMap.get(groupName);
                    }
                } else {
                    String channelName = split[0].trim();
                    for (String str2 : split[1].trim().split("#")) {
                        String url = str2.trim();
                        if (isValidLiveUrl(url)) {
                            if (!currentGroupMap.containsKey(channelName)) {
                                arrayList = new ArrayList<>();
                                currentGroupMap.put(channelName, arrayList);
                            } else {
                                arrayList = currentGroupMap.get(channelName);
                            }
                            if (!arrayList.contains(url)) {
                                arrayList.add(url);
                            }
                        }
                    }
                }
            }
            if (!defaultGroupMap.isEmpty() && !linkedHashMap.containsKey("未分组")) {
                linkedHashMap.put("未分组", defaultGroupMap);
            }
        } catch (Throwable unused) {
        }
    }

    private static boolean isValidLiveUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("rtmp://") || lower.startsWith("rtsp://")
                || lower.startsWith("rtp://") || lower.startsWith("p2p://")
                || lower.startsWith("mitv://") || lower.startsWith("clan://")
                || lower.startsWith("proxy://") || lower.startsWith("file://");
    }

    public static JsonArray live2JsonArray(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap) {
        JsonArray jsonarr = new JsonArray();
        for (String str : linkedHashMap.keySet()) {
            JsonArray jsonarr2 = new JsonArray();
            LinkedHashMap<String, ArrayList<String>> linkedHashMap2 = linkedHashMap.get(str);
            if (linkedHashMap2 != null && !linkedHashMap2.isEmpty()) {
                for (String str2 : linkedHashMap2.keySet()) {
                    ArrayList<String> arrayList = linkedHashMap2.get(str2);
                    if (arrayList != null && !arrayList.isEmpty()) {
                        JsonArray jsonarr3 = new JsonArray();
                        for (int i = 0; i < arrayList.size(); i++) {
                            jsonarr3.add(arrayList.get(i));
                        }
                        JsonObject jsonobj = new JsonObject();
                        try {
                            jsonobj.addProperty("name", str2);
                            jsonobj.add("urls", jsonarr3);
                        } catch (Throwable ignored) {
                        }
                        jsonarr2.add(jsonobj);
                    }
                }
                JsonObject jsonobj2 = new JsonObject();
                try {
                    jsonobj2.addProperty("group", str);
                    jsonobj2.add("channels", jsonarr2);
                } catch (Throwable ignored) {
                }
                jsonarr.add(jsonobj2);
            }
        }
        return jsonarr;
    }
}
