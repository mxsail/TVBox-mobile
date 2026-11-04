package com.tvbox.sync;

import java.io.IOException;
import java.util.UUID;

public class WebDavStorage implements SyncStorage {
    private final SyncHttp http;
    private final String baseUrl;
    private final String spaceId;
    private final String authHeader;

    public WebDavStorage(SyncHttp http, String baseUrl, String spaceId, String authHeader) {
        this.http = http;
        this.baseUrl = normalizeUrl(baseUrl);
        this.spaceId = (spaceId == null || spaceId.isEmpty()) ? SyncProtocol.DEFAULT_SPACE_ID : spaceId;
        this.authHeader = authHeader;
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String s = url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private String getSpaceUrl() {
        return baseUrl + "/tvbox/" + spaceId;
    }

    private String getFileUrl(String name) {
        return getSpaceUrl() + "/" + name;
    }

    private void applyAuth(SyncHttpRequest request) {
        if (authHeader != null && !authHeader.isEmpty()) {
            request.headers.put("Authorization", authHeader);
        }
    }

    @Override
    public boolean ensure() throws IOException {
        // MKCOL /tvbox/
        SyncHttpRequest reqRoot = new SyncHttpRequest("MKCOL", baseUrl + "/tvbox/");
        applyAuth(reqRoot);
        SyncHttpResponse resRoot = http.execute(reqRoot);
        if (resRoot.statusCode != 201 && resRoot.statusCode != 200 && resRoot.statusCode != 405) {
            // Some servers return 405 if already exists, which is acceptable
            return false;
        }

        // MKCOL /tvbox/<spaceId>/
        SyncHttpRequest reqSpace = new SyncHttpRequest("MKCOL", getSpaceUrl() + "/");
        applyAuth(reqSpace);
        SyncHttpResponse resSpace = http.execute(reqSpace);
        return resSpace.statusCode == 201 || resSpace.statusCode == 200 || resSpace.statusCode == 405;
    }

    @Override
    public byte[] read(String name) throws IOException {
        SyncHttpRequest request = new SyncHttpRequest("GET", getFileUrl(name));
        applyAuth(request);
        SyncHttpResponse response = http.execute(request);
        if (response.statusCode == 200) {
            return response.body;
        }
        if (response.statusCode == 404) {
            return null;
        }
        throw new IOException("WebDAV read failed with status " + response.statusCode);
    }

    @Override
    public boolean write(String name, byte[] bytes, String etag, boolean create) throws IOException {
        SyncHttpRequest request = new SyncHttpRequest("PUT", getFileUrl(name));
        applyAuth(request);
        request.body = bytes;
        if (create) {
            request.headers.put("If-None-Match", "*");
        } else if (etag != null && !etag.isEmpty()) {
            request.headers.put("If-Match", etag);
        }

        SyncHttpResponse response = http.execute(request);
        return response.statusCode >= 200 && response.statusCode < 300;
    }

    @Override
    public String etag(String name) throws IOException {
        SyncHttpRequest request = new SyncHttpRequest("HEAD", getFileUrl(name));
        applyAuth(request);
        SyncHttpResponse response = http.execute(request);
        if (response.statusCode == 200) {
            String etag = response.getHeader("ETag");
            if (etag != null) return etag;
            return response.getHeader("OC-ETag");
        }
        return null;
    }

    @Override
    public boolean copy(String src, String dst) throws IOException {
        SyncHttpRequest request = new SyncHttpRequest("COPY", getFileUrl(src));
        applyAuth(request);
        request.headers.put("Destination", getFileUrl(dst));
        request.headers.put("Overwrite", "T");
        SyncHttpResponse response = http.execute(request);
        return response.statusCode >= 200 && response.statusCode < 300;
    }

    @Override
    public boolean move(String src, String dst) throws IOException {
        SyncHttpRequest request = new SyncHttpRequest("MOVE", getFileUrl(src));
        applyAuth(request);
        request.headers.put("Destination", getFileUrl(dst));
        request.headers.put("Overwrite", "T");
        SyncHttpResponse response = http.execute(request);
        return response.statusCode >= 200 && response.statusCode < 300;
    }

    @Override
    public boolean delete(String name) throws IOException {
        SyncHttpRequest request = new SyncHttpRequest("DELETE", getFileUrl(name));
        applyAuth(request);
        SyncHttpResponse response = http.execute(request);
        return response.statusCode >= 200 && response.statusCode < 300 || response.statusCode == 404;
    }

    @Override
    public boolean probe() throws IOException {
        if (!ensure()) return false;
        String probeName = ".probe-" + UUID.randomUUID().toString();
        byte[] data = "probe".getBytes("UTF-8");
        boolean writeOk = write(probeName, data, null, true);
        if (writeOk) {
            delete(probeName);
            return true;
        }
        return false;
    }
}
