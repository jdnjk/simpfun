package cn.jdnjk.simpfun.download;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 把下载内容收进内存，供「要内容不要文件」的调用方使用（如文件编辑器）。
 *
 * <p>设了字节上限：编辑器只处理文本，真下到一个大文件会把内存吃光，
 * 所以超限当场失败，让调用方去走「请下载后再处理」的提示。
 */
public final class MemoryDownloadTarget implements DownloadTarget {

    private final String displayName;
    private final long maxBytes;
    /** finish() 之后才有值；写在 OkHttp 线程、读在主线程，故用 volatile。 */
    private volatile byte[] content;
    private ByteArrayOutputStream buffer;

    public MemoryDownloadTarget(@NonNull String displayName, long maxBytes) {
        this.displayName = displayName;
        this.maxBytes = maxBytes;
    }

    /** 下载成功后的完整内容，未完成时为 null。 */
    @Nullable
    public byte[] getContent() {
        return content;
    }

    @Override
    public OutputStream openOutputStream() {
        buffer = new ByteArrayOutputStream(64 * 1024);
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                ensureCapacity(1);
                buffer.write(b);
            }

            @Override
            public void write(@NonNull byte[] b, int off, int len) throws IOException {
                ensureCapacity(len);
                buffer.write(b, off, len);
            }

            private void ensureCapacity(int incoming) throws IOException {
                if (buffer.size() + (long) incoming > maxBytes) {
                    throw new IOException("文件过大，无法在编辑器中打开");
                }
            }
        };
    }

    @Override
    public void finish() {
        content = buffer == null ? new byte[0] : buffer.toByteArray();
        buffer = null;
    }

    @Override
    public void deletePartial() {
        buffer = null;
        content = null;
    }

    @Override
    public String getDisplayPath() {
        return displayName;
    }

    @Nullable
    @Override
    public Uri getViewableUri(Context context) {
        // 内存里的内容没有可交给其他应用的 Uri。
        return null;
    }
}
