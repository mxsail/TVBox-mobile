package com.tvbox.sync;

import java.io.IOException;

public interface SyncStorage {
    byte[] read(String name) throws IOException;

    boolean write(String name, byte[] bytes, String etag, boolean create) throws IOException;

    boolean ensure() throws IOException;

    String etag(String name) throws IOException;

    boolean copy(String src, String dst) throws IOException;

    boolean move(String src, String dst) throws IOException;

    boolean delete(String name) throws IOException;

    boolean probe() throws IOException;
}
