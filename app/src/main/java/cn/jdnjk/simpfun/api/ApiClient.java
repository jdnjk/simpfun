package cn.jdnjk.simpfun.api;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class ApiClient {
    private static ApiClient instance;
    private final OkHttpClient client;

    private static final String USER_AGENT = "SimpfunAPP/1.0";

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
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            Request requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build();
            return chain.proceed(requestWithUserAgent);
        }
    }
}