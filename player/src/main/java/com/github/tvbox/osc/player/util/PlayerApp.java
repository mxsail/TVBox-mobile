package com.github.tvbox.osc.player.util;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;

public class PlayerApp {

    private static Context sContext;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static final com.google.gson.Gson sGson = new com.google.gson.Gson();
    private static okhttp3.OkHttpClient sOkHttpClient;

    public static synchronized okhttp3.OkHttpClient okHttpClient() {
        if (sOkHttpClient == null) {
            sOkHttpClient = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
        }
        return sOkHttpClient;
    }

    public static void setOkHttpClient(okhttp3.OkHttpClient client) {
        sOkHttpClient = client;
    }

    public static com.google.gson.Gson gson() {
        return sGson;
    }

    public static void init(Context context) {
        if (context != null) {
            sContext = context.getApplicationContext();
        }
    }

    public static Context get() {
        if (sContext == null) {
            try {
                Application app = (Application) Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null);
                if (app != null) sContext = app.getApplicationContext();
            } catch (Throwable ignored) {
            }
        }
        return sContext;
    }

    public static void post(Runnable runnable) {
        sHandler.post(runnable);
    }

    public static void postDelayed(Runnable runnable, long delayMillis) {
        sHandler.postDelayed(runnable, delayMillis);
    }

    public static SharedPreferences getPrefs() {
        Context ctx = get();
        if (ctx == null) return null;
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }
}
