package com.github.tvbox.osc.event;

public class LiveSourceChangedEvent {
    private final String sourceUrl;
    private final boolean isLoading;

    public LiveSourceChangedEvent(String sourceUrl, boolean isLoading) {
        this.sourceUrl = sourceUrl;
        this.isLoading = isLoading;
    }

    public LiveSourceChangedEvent(String sourceUrl) {
        this(sourceUrl, false);
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public boolean isLoading() {
        return isLoading;
    }
}
