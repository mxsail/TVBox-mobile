package com.tvbox.sync.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncConfigGroup implements Serializable {

    public static class SubscriptionItem implements Serializable {
        public String name;
        public String url;
        public boolean checked;
        public boolean top;
        public boolean builtIn;

        public SubscriptionItem() {
        }

        public SubscriptionItem(String name, String url, boolean checked, boolean top, boolean builtIn) {
            this.name = name;
            this.url = url;
            this.checked = checked;
            this.top = top;
            this.builtIn = builtIn;
        }
    }

    public static class ContentGroup implements Serializable {
        public long rev;
        public String writer;
        public String apiUrl;
        public List<SubscriptionItem> subscriptions = new ArrayList<SubscriptionItem>();
        public String homeApi;
        public String liveUrl;
        public String epgUrl;
        public String defaultParse;

        public ContentGroup() {
        }
    }

    public static class NetworkGroup implements Serializable {
        public long rev;
        public String writer;
        public int dohUrl;

        public NetworkGroup() {
        }
    }

    public Map<String, Object> groups = new HashMap<String, Object>();
    public Map<String, Map<String, Object>> extras = new HashMap<String, Map<String, Object>>();

    public SyncConfigGroup() {
    }
}
