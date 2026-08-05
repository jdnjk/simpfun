package cn.jdnjk.simpfun.mcp;

import android.content.Context;

import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 将现有异步 API 包装为同步调用。
 * <p>
 * 复用项目里 SftpTransferCoordinator 的 CountDownLatch + AtomicReference 模式。
 */
public class BlockingApiBridge {

    private final Context appContext;

    public BlockingApiBridge(Context appContext) {
        this.appContext = appContext.getApplicationContext();
    }

    public interface ApiInvoke {
        void run(Consumer<JSONObject> onSuccess, Consumer<String> onFailure);
    }

    public JSONObject await(long timeoutMs, ApiInvoke invoke) throws McpToolException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<JSONObject> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        invoke.run(data -> {
            result.set(data);
            latch.countDown();
        }, err -> {
            error.set(err);
            latch.countDown();
        });

        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new McpToolException("操作超时（" + (timeoutMs / 1000) + "s）");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpToolException("等待中断", e);
        }

        if (error.get() != null) {
            throw new McpToolException(error.get());
        }
        return result.get();
    }

    public static String simpfunToken(Context context) {
        android.content.SharedPreferences sp = context.getSharedPreferences("token", android.content.Context.MODE_PRIVATE);
        String token = sp.getString("token", null);
        if (token == null || token.isEmpty()) {
            return null;
        }
        return token;
    }
}
