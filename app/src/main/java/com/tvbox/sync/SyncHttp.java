package com.tvbox.sync;

import java.io.IOException;

public interface SyncHttp {
    SyncHttpResponse execute(SyncHttpRequest request) throws IOException;
}
