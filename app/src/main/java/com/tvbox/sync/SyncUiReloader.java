package com.tvbox.sync;

import android.content.Context;
import android.content.Intent;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.ui.activity.MainActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MD5;
import com.orhanobut.hawk.Hawk;

import java.io.File;

public class SyncUiReloader {

    public static void onConfigApplied() {
        Context context = App.getInstance();
        if (context == null) return;

        // 1. Delete cached config file
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");
        if (apiUrl != null && !apiUrl.isEmpty()) {
            File cacheFile = new File(context.getFilesDir(), MD5.string2MD5(apiUrl));
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
        }

        // 2. Restart MainActivity to reload new home data
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
