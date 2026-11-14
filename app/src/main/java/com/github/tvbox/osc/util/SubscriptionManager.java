package com.github.tvbox.osc.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.Subscription;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.orhanobut.hawk.Hawk;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 订阅源统一管理器。
 *
 * <p>读取按优先级回退，写入主存储的同时保留兼容写：
 * <ol>
 *   <li>主存储：独立 SharedPreferences 中的明文 JSON。不依赖 Hawk 的加密密钥，
 *       也不走 Hawk 泛型 List 的 {@code Class.forName} / {@code toList} 反序列化链路，
 *       改包名、备份恢复、密钥轮换后仍可读。</li>
 *   <li>兼容：Hawk 中的 String({@link #HAWK_KEY_JSON})，随 Hawk2.xml 一起被备份/恢复。</li>
 *   <li>兼容：Hawk 中的旧 {@code List<Subscription>}({@link HawkConfig#SUBSCRIPTIONS})，
 *       用于老版本数据的一次性迁移。</li>
 * </ol>
 */
public class SubscriptionManager {
    private static final String TAG = "SubscriptionManager";
    private static final String PREF_NAME = "tvbox_subscriptions";
    private static final String PREF_KEY_JSON = "subscriptions_json";
    private static final String HAWK_KEY_JSON = "api_history_json";

    private static final Gson GSON = new Gson();
    private static final Type SUBSCRIPTION_LIST_TYPE = new TypeToken<ArrayList<Subscription>>() {}.getType();

    private static SharedPreferences prefs() {
        return App.getInstance().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 读取所有订阅源列表（深拷贝，独立实例）
     */
    public static synchronized List<Subscription> getSubscriptions() {
        // 1. 主存储：明文 JSON
        List<Subscription> result = new ArrayList<>();
        try {
            result = readJson(prefs().getString(PREF_KEY_JSON, ""), "prefs");
        } catch (Throwable th) {
            LOG.e(TAG, "read subscriptions from prefs failed: " + th);
        }
        if (!result.isEmpty()) {
            return result;
        }

        // 2. 兼容：Hawk String，命中后迁移到主存储
        try {
            result = readJson(Hawk.get(HAWK_KEY_JSON, ""), "hawk json");
        } catch (Throwable th) {
            LOG.e(TAG, "read subscriptions from hawk json failed: " + th);
        }
        if (!result.isEmpty()) {
            saveSubscriptions(result);
            return result;
        }

        // 3. 兼容：Hawk 旧泛型 List，命中后迁移到主存储
        result = readLegacyHawkList();
        if (!result.isEmpty()) {
            saveSubscriptions(result);
        }
        return result;
    }

    private static List<Subscription> readJson(String json, String from) {
        List<Subscription> result = new ArrayList<>();
        if (TextUtils.isEmpty(json)) {
            return result;
        }
        try {
            List<Subscription> parsed = GSON.fromJson(json, SUBSCRIPTION_LIST_TYPE);
            if (parsed != null) {
                for (Subscription sub : parsed) {
                    if (sub != null) {
                        result.add(sub);
                    }
                }
            }
        } catch (Throwable th) {
            LOG.e(TAG, "parse subscriptions from " + from + " failed: " + th);
        }
        return result;
    }

    /**
     * 旧格式：Hawk 泛型 List，元素可能是 Subscription / LinkedTreeMap，也可能整体是 JSON 字符串
     */
    private static List<Subscription> readLegacyHawkList() {
        List<Subscription> result = new ArrayList<>();
        try {
            Object raw = Hawk.get(HawkConfig.SUBSCRIPTIONS, null);
            if (raw instanceof List) {
                List<?> list = (List<?>) raw;
                if (!list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof Subscription) {
                        for (Object o : list) {
                            if (o instanceof Subscription) {
                                result.add((Subscription) o);
                            }
                        }
                    } else {
                        List<Subscription> parsed = GSON.fromJson(GSON.toJson(raw), SUBSCRIPTION_LIST_TYPE);
                        if (parsed != null) {
                            result.addAll(parsed);
                        }
                    }
                }
            } else if (raw instanceof String) {
                result.addAll(readJson((String) raw, "hawk legacy string"));
            }
        } catch (Throwable th) {
            LOG.e(TAG, "read legacy hawk subscriptions failed: " + th);
        }
        return result;
    }

    /**
     * 保存订阅源列表（主存储同步落盘；兼容写失败不影响主存储）
     */
    public static synchronized void saveSubscriptions(List<Subscription> subscriptions) {
        ArrayList<Subscription> copy = subscriptions == null
                ? new ArrayList<>() : new ArrayList<>(subscriptions);
        String json = GSON.toJson(copy, SUBSCRIPTION_LIST_TYPE);
        try {
            if (!prefs().edit().putString(PREF_KEY_JSON, json).commit()) {
                LOG.e(TAG, "commit subscriptions to prefs failed");
            }
        } catch (Throwable th) {
            LOG.e(TAG, "write subscriptions to prefs failed: " + th);
        }
        // 兼容写：备份/恢复与 App.putDefaultApi() 的 contains 判断仍依赖 Hawk2.xml
        try {
            if (!Hawk.put(HAWK_KEY_JSON, json)) {
                LOG.e(TAG, "write hawk json subscriptions failed");
            }
        } catch (Throwable th) {
            LOG.e(TAG, "write hawk json subscriptions failed: " + th);
        }
        try {
            Hawk.put(HawkConfig.SUBSCRIPTIONS, copy);
        } catch (Throwable th) {
            LOG.e(TAG, "write legacy hawk subscriptions failed: " + th);
        }
    }

    /**
     * 备份恢复后调用：清掉主存储，强制下次从恢复出来的 Hawk 数据重建
     */
    public static synchronized void invalidateMainStore() {
        try {
            prefs().edit().remove(PREF_KEY_JSON).commit();
        } catch (Throwable th) {
            LOG.e(TAG, "invalidate subscriptions store failed: " + th);
        }
    }

    /**
     * 获取当前选中的订阅源 URL
     */
    public static String getSelectedApiUrl() {
        return Hawk.get(HawkConfig.API_URL, "");
    }

    /**
     * 设置当前选中的订阅源 URL 并同步更新列表中各源的 checked 状态
     */
    public static synchronized void setSelectedApiUrl(String url) {
        if (url == null) url = "";
        Hawk.put(HawkConfig.API_URL, url);
        List<Subscription> list = getSubscriptions();
        boolean changed = false;
        for (Subscription sub : list) {
            boolean shouldCheck = !TextUtils.isEmpty(url) && url.equals(sub.getUrl());
            if (sub.isChecked() != shouldCheck) {
                sub.setChecked(shouldCheck);
                changed = true;
            }
        }
        if (changed) {
            saveSubscriptions(list);
        }
    }
}
