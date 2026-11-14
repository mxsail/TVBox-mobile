package com.github.tvbox.osc.player.util;

import android.net.Uri;
import java.io.File;

public class UrlUtil {

    public static Uri uri(String url) {
        if (url == null) return Uri.EMPTY;
        url = url.trim().replace("\\", "");
        return url.startsWith("/") ? Uri.fromFile(new File(url)) : Uri.parse(url);
    }

    public static String scheme(String url) {
        return url == null ? "" : scheme(uri(url));
    }

    public static String scheme(Uri uri) {
        if (uri == null) return "";
        String scheme = uri.getScheme();
        return scheme == null ? "" : scheme.toLowerCase().trim();
    }

    public static String host(String url) {
        return url == null ? "" : host(uri(url));
    }

    public static String host(Uri uri) {
        if (uri == null) return "";
        String host = uri.getHost();
        return host == null ? "" : host.toLowerCase().trim();
    }

    public static String path(String url) {
        return url == null ? "" : path(uri(url));
    }

    public static String path(Uri uri) {
        if (uri == null) return "";
        String path = uri.getLastPathSegment();
        return path == null ? "" : path.trim();
    }
}
