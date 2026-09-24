package cn.jdnjk.simpfun.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class McpSseStream extends InputStream {
    private static final int QUEUE_CAPACITY = 16;

    private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private volatile boolean closed = false;
    private byte[] current = null;
    private int pos = 0;

    public void send(String text) {
        if (closed) return;
        if (!queue.offer(text.getBytes(StandardCharsets.UTF_8))) {
            closed = true;
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public void closeStream() {
        closed = true;
        queue.offer(new byte[0]);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        while (current == null || pos >= current.length) {
            byte[] next;
            try {
                next = queue.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
            if (next == null) {
                continue;
            }
            if (next.length == 0) {
                return -1;
            }
            current = next;
            pos = 0;
        }
        int n = Math.min(len, current.length - pos);
        System.arraycopy(current, pos, b, off, n);
        pos += n;
        return n;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n == -1 ? -1 : (one[0] & 0xFF);
    }
}
