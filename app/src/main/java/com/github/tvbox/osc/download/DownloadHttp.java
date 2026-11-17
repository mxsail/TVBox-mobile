package com.github.tvbox.osc.download;

import com.github.tvbox.osc.util.OkGoHelper;

import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

/**
 * 下载引擎专用 OkHttp 客户端。
 * 使用独立 Dispatcher:暂停/取消时可以通过 cancelTag 单独打断指定集数，或 cancelAll 全部打断,
 * 且不会误伤共用默认客户端的播放、订阅等请求。
 */
public class DownloadHttp {

    private static OkHttpClient client;

    public static synchronized OkHttpClient client() {
        if (client == null) {
            Dispatcher dispatcher = new Dispatcher();
            dispatcher.setMaxRequests(64);
            dispatcher.setMaxRequestsPerHost(20);
            client = OkGoHelper.getDefaultClient().newBuilder()
                    .dispatcher(dispatcher)
                    .build();
        }
        return client;
    }

    /** HLS 播放列表/分片/密钥请求用 callTimeout 兜底,防止对端黑洞连接无限阻塞 */
    public static OkHttpClient hlsClient() {
        return client().newBuilder()
                .callTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /** 打断指定集数的所有在途请求 */
    public static void cancelTag(Object tag) {
        if (tag == null) return;
        try {
            Dispatcher dispatcher = client().dispatcher();
            for (Call call : dispatcher.queuedCalls()) {
                if (tag.equals(call.request().tag())) {
                    call.cancel();
                }
            }
            for (Call call : dispatcher.runningCalls()) {
                if (tag.equals(call.request().tag())) {
                    call.cancel();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 暂停/取消全部时打断引擎内所有在途请求(阻塞中的 execute 会立即抛 IOException) */
    public static void cancelAll() {
        try {
            client().dispatcher().cancelAll();
        } catch (Throwable ignored) {
        }
    }
}
