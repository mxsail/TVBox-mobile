package com.github.tvbox.osc.player.mpv;

import android.net.Uri;
import com.github.tvbox.osc.player.util.PlayerPath;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MpvConfigFile {

    private static final String MPV_CONF = "mpv.conf";
    private static final char UTF_8_BOM = '\uFEFF';

    private static File file() {
        return PlayerPath.mpv(MPV_CONF);
    }

    public static String read() {
        return PlayerPath.read(file());
    }

    public static boolean write(String content) {
        return PlayerPath.write(file(), content);
    }

    public static List<String> findInterfaceManagedOptions(CharSequence content) {
        Set<String> configured = getDefaultOptions(content);
        return MpvUtil.getManagedOptionNames().stream().filter(option -> configured.contains(option) || configured.contains("no-" + option)).toList();
    }

    public static boolean importFrom(Uri uri) {
        try {
            android.content.Context ctx = com.github.tvbox.osc.player.util.PlayerApp.get();
            if (ctx == null) return false;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(file())) {
                if (in == null) return false;
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                return true;
            }
        } catch (Throwable e) {
            return false;
        }
    }

    private static Set<String> getDefaultOptions(CharSequence content) {
        Set<String> options = new HashSet<>();
        if (content == null || content.length() == 0) return options;
        String[] lines = content.toString().split("\\r?\\n");
        for (String line : lines) {
            String trimmed = stripBom(line).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int eq = trimmed.indexOf('=');
            String key = eq >= 0 ? trimmed.substring(0, eq).trim() : trimmed;
            if (!key.isEmpty()) options.add(key);
        }
        return options;
    }

    private static String stripBom(String line) {
        return line != null && !line.isEmpty() && line.charAt(0) == UTF_8_BOM ? line.substring(1) : line;
    }
}
