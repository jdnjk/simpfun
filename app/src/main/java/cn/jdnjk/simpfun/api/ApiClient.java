package cn.jdnjk.simpfun.api;

import android.util.Log;
import cn.jdnjk.simpfun.BuildConfig;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import okio.Buffer;

import java.io.IOException;

public class ApiClient {
    public static final String BASE_URL = "https://api.simpfun.cn/api";
    public static final String BASE_INS_URL = "https://api.simpfun.cn/api/ins/";
    private static ApiClient instance;
    private final OkHttpClient client;

    private static final String USER_AGENT = "Simpfun/"+ BuildConfig.VERSION_NAME;

    private ApiClient() {
        this.client = new OkHttpClient.Builder()
                .addInterceptor(new UserAgentInterceptor())
                .addInterceptor(new LoggingInterceptor())
                .build();
    }
    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    public OkHttpClient getClient() {
        return client;
    }
    private static class UserAgentInterceptor implements Interceptor {
        @Override
        public @NotNull Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            Request requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build();
            return chain.proceed(requestWithUserAgent);
        }
    }

    private static class LoggingInterceptor implements Interceptor {
        @Override
        public @NotNull Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            // Log Request
            Log.d("ApiClient", "--> " + request.method() + " " + request.url());
            if (request.body() != null) {
                try {
                    Buffer buffer = new Buffer();
                    request.body().writeTo(buffer);
                    Log.d("ApiClient", "Body: " + buffer.readUtf8());
                } catch (Exception e) {
                    Log.d("ApiClient", "Could not log body: " + e.getMessage());
                }
            }

            Response response = chain.proceed(request);

            Log.d("ApiClient", response.code() + " " + response.request().url());

            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                try {
                    String content = response.peekBody(1024 * 1024).string();
                    Log.d("ApiClient", "Response: " + content);
                } catch (Exception e) {
                    Log.d("ApiClient", "Could not log response body: " + e.getMessage());
                }
            }

            return response;
        }
    }
}