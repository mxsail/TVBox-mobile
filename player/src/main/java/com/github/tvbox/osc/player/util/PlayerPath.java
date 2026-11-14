package com.github.tvbox.osc.player.util;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class PlayerPath {

    public static File files(String name) {
        Context ctx = PlayerApp.get();
        File dir = ctx != null ? ctx.getFilesDir() : new File("/data/data/com.ecbud.player/files");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name);
    }

    public static File cache(String name) {
        Context ctx = PlayerApp.get();
        File dir = ctx != null ? ctx.getCacheDir() : new File("/data/data/com.ecbud.player/cache");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name);
    }

    public static File exoCache() {
        return cache("exo");
    }

    public static File mpv() {
        Context ctx = PlayerApp.get();
        File dir = new File(ctx != null ? ctx.getFilesDir() : new File("/data/data/com.ecbud.player/files"), "mpv");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File mpvCache() {
        Context ctx = PlayerApp.get();
        File dir = new File(ctx != null ? ctx.getCacheDir() : new File("/data/data/com.ecbud.player/cache"), "mpv");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File mpv(String name) {
        return new File(mpv(), name);
    }

    public static boolean exists(File file) {
        return file != null && file.exists();
    }

    public static void clear(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) clear(child);
            }
        }
        file.delete();
    }

    public static long size(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long size = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) size += size(child);
        }
        return size;
    }

    public static long available(File file) {
        if (file == null) return 0;
        try {
            return file.getFreeSpace();
        } catch (Throwable e) {
            return 0;
        }
    }

    public static String read(File file) {
        if (file == null || !file.exists()) return "";
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            in.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return "";
        }
    }

    public static boolean write(File file, String content) {
        if (file == null) return false;
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
