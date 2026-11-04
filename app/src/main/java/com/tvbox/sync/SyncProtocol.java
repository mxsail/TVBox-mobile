package com.tvbox.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tvbox.sync.model.SyncSnapshot;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

public class SyncProtocol {
    public static final String SCHEMA = "tvbox.sync/1";
    public static final String DEFAULT_SPACE_ID = "default";
    private static final Pattern SPACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    private static final Gson GSON = new GsonBuilder().create();

    public static String normalize(String apiUrl) {
        if (apiUrl == null) {
            return "";
        }
        String s = apiUrl.trim();
        int idx = s.indexOf(";pk;");
        if (idx >= 0) {
            s = s.substring(0, idx);
        }
        return s.trim();
    }

    public static String md5(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String sha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String configHash(String apiUrl) {
        String normalized = normalize(apiUrl);
        String fullMd5 = md5(normalized);
        if (fullMd5.length() >= 8) {
            return fullMd5.substring(0, 8);
        }
        return fullMd5;
    }

    public static String makeId(String apiUrl, String sourceKey, String vodId) {
        return configHash(apiUrl) + ":" + (sourceKey == null ? "" : sourceKey) + ":" + (vodId == null ? "" : vodId);
    }

    public static boolean validateSpaceId(String spaceId) {
        if (spaceId == null || spaceId.isEmpty()) {
            return false;
        }
        if (spaceId.equals(".") || spaceId.equals("..")) {
            return false;
        }
        if (spaceId.contains("/") || spaceId.contains("\\")) {
            return false;
        }
        return SPACE_ID_PATTERN.matcher(spaceId).matches();
    }

    public static String calculateChecksum(SyncSnapshot snapshot) {
        if (snapshot == null) return "";
        try {
            String json = GSON.toJson(snapshot);
            JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
            obj.remove("checksum");
            return sha256(obj.toString());
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean verifyChecksum(SyncSnapshot snapshot) {
        if (snapshot == null || snapshot.checksum == null || snapshot.checksum.isEmpty()) {
            return false;
        }
        return snapshot.checksum.equalsIgnoreCase(calculateChecksum(snapshot));
    }
}
