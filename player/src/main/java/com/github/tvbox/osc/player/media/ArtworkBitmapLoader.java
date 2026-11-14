package com.github.tvbox.osc.player.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.datasource.DataSourceBitmapLoader;
import androidx.media3.session.MediaSession;

import com.google.common.util.concurrent.ListenableFuture;

public final class ArtworkBitmapLoader implements BitmapLoader {

    private final DataSourceBitmapLoader decoder;

    public ArtworkBitmapLoader(Context context) {
        decoder = new DataSourceBitmapLoader.Builder(context).setMaximumOutputDimension(MediaSession.getBitmapDimensionLimit(context) * 2 - 1).build();
    }

    @Override
    public boolean supportsMimeType(@NonNull String mimeType) {
        return decoder.supportsMimeType(mimeType);
    }

    @NonNull
    @Override
    public ListenableFuture<Bitmap> decodeBitmap(@NonNull byte[] data) {
        return decoder.decodeBitmap(data);
    }

    @NonNull
    @Override
    public ListenableFuture<Bitmap> loadBitmap(@NonNull Uri uri) {
        return decoder.loadBitmap(uri);
    }
}
