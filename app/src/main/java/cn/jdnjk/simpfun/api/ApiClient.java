package cn.jdnjk.simpfun.api;

import cn.jdnjk.simpfun.BuildConfig;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

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
}