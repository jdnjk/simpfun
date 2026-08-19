package cn.jdnjk.simpfun.api;

import android.util.Log;
import cn.jdnjk.simpfun.BuildConfig;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import okio.Buffer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    public static final String BASE_URL = "https://api.simpfun.cn/api";
    public static final String BASE_INS_URL = "https://api.simpfun.cn/api/ins/";
    private static ApiClient instance;
    private final OkHttpClient client;

    private static final String USER_AGENT = "SimpfunAPP/"+ BuildConfig.VERSION_NAME;

    private ApiClient() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(32);
        dispatcher.setMaxRequestsPerHost(16);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .addInterceptor(new UserAgentInterceptor());
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(new LoggingInterceptor());
        }
        this.client = builder.build();
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
        private static final long MAX_LOG_BYTES = 16 * 1024;

        @Override
        public @NotNull Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            // Log Request
            if (BuildConfig.DEBUG) {
                Log.d("ApiClient", "--> " + request.method() + " " + request.url());

                RequestBody requestBody = request.body();
                if (requestBody != null && isPlainText(requestBody.contentType())) {
                    try {
                        long contentLength = requestBody.contentLength();
                        if (contentLength < 0 || contentLength > MAX_LOG_BYTES) {
                            Log.d("ApiClient", "Body: <omitted, " + contentLength + " bytes>");
                        } else {
                            Buffer buffer = new Buffer();
                            requestBody.writeTo(buffer);
                            Log.d("ApiClient", "Body: " + buffer.readString(charset(requestBody.contentType())));
                        }
                    } catch (Exception e) {
                        Log.d("ApiClient", "Could not log body: " + e.getMessage());
                    }
                } else if (requestBody != null) {
                    Log.d("ApiClient", "Body: <binary or unsupported content omitted>");
                }
            }

            Response response = chain.proceed(request);

            if (BuildConfig.DEBUG) {
                Log.d("ApiClient", response.code() + " " + response.request().url());
            }

            ResponseBody responseBody = response.body();
            if (BuildConfig.DEBUG) {
                if (isPlainText(responseBody.contentType())) {
                    try {
                        ResponseBody peekBody = response.peekBody(MAX_LOG_BYTES);
                        String content = peekBody.string();
                        String suffix = responseBody.contentLength() > MAX_LOG_BYTES
                                ? "... (truncated, " + responseBody.contentLength() + " bytes)"
                                : "";
                        Log.d("ApiClient", "Response: " + content + suffix);
                    } catch (Exception e) {
                        Log.d("ApiClient", "Could not log response body: " + e.getMessage());
                    }
                } else {
                    Log.d("ApiClient", "Response: <binary or unsupported content omitted>");
                }
            }

            return response;
        }

        private static boolean isPlainText(MediaType mediaType) {
            if (mediaType == null) {
                return false;
            }
            String type = mediaType.type();
            String subtype = mediaType.subtype();
            return "text".equalsIgnoreCase(type)
                    || subtype.toLowerCase().contains("json")
                    || subtype.toLowerCase().contains("xml")
                    || subtype.toLowerCase().contains("x-www-form-urlencoded");
        }

        private static Charset charset(MediaType mediaType) {
            Charset charset = mediaType != null ? mediaType.charset(StandardCharsets.UTF_8) : StandardCharsets.UTF_8;
            return charset != null ? charset : StandardCharsets.UTF_8;
        }
    }
}