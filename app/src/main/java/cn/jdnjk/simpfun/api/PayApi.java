package cn.jdnjk.simpfun.api;

import android.os.Handler;
import android.os.Looper;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_URL;

public class PayApi {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(JSONObject data);
        void onFailure(String errorMsg);
    }

    /**
     * 创建充值订单
     * @param token 认证Token
     * @param item 充值项目 (point/auto_sign/double_sign)
     * @param point 充值数量或天数
     * @param method 支付方式
     * @param mode 充值模式 (public/normal)
     * @param callback 回调
     */
    public void createOrder(String token, String item , int point, String method, String mode, Callback callback) {
        FormBody formBody = new FormBody.Builder()
                .add("item", item)
                .add("num", String.valueOf(point))
                .add("method", method)
                .add("redirect_url","simpfun://pay/ok")
                .add("recharge_mode", mode)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/pay/web/create")
                .header("Authorization", token)
                .post(formBody)
                .build();

        sendRequest(request, callback);
    }

    /**
     * 查询订单状态
     * @param token 认证Token
     * @param orderId 订单ID
     * @param callback 回调
     */
    public void getOrderStatus(String token, long orderId, Callback callback) {
        FormBody formBody = new FormBody.Builder()
                .add("order_id", String.valueOf(orderId))
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/pay/web/status")
                .header("Authorization", token)
                .put(formBody)
                .build();

        sendRequest(request, callback);
    }

    /**
     * 获取增值列表 (Meta)
     * @param token 认证Token
     * @param callback 回调
     */
    public void getRechargeMeta(String token, Callback callback) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/pay/web/meta")
                .header("Authorization", token)
                .get()
                .build();

        sendRequest(request, callback);
    }

    private void sendRequest(Request request, Callback callback) {
        ApiClient.getInstance().getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                mainHandler.post(() -> callback.onFailure("网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    String responseData = responseBody != null ? responseBody.string() : "";
                    if (!responseData.isEmpty()) {
                        try {
                            JSONObject jsonObject = new JSONObject(responseData);
                            if (response.isSuccessful()) {
                                mainHandler.post(() -> callback.onSuccess(jsonObject));
                            } else {
                                String msg = jsonObject.optString("msg", "请求失败: " + response.code());
                                mainHandler.post(() -> callback.onFailure(msg));
                            }
                        } catch (JSONException e) {
                            if (response.isSuccessful()) {
                                mainHandler.post(() -> callback.onFailure("解析数据失败"));
                            } else {
                                mainHandler.post(() -> callback.onFailure("请求失败: " + response.code()));
                            }
                        }
                    } else {
                        mainHandler.post(() -> callback.onFailure("服务器响应为空: " + response.code()));
                    }
                }
            }
        });
    }
}
