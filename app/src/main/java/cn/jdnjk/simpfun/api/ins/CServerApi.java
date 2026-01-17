package cn.jdnjk.simpfun.api.ins;

import cn.jdnjk.simpfun.api.ApiClient;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Request;

import static cn.jdnjk.simpfun.api.ApiClient.BASE_INS_URL;
import static cn.jdnjk.simpfun.api.ApiClient.BASE_URL;

public class CServerApi {
    public static Call getGameList(boolean isCustom, String token) {
        String url = isCustom ? BASE_URL + "/games/list?custom=true" : BASE_URL + "/games/list";
        Request.Builder builder = new Request.Builder().url(url);
        if (token != null) builder.header("Authorization", token);
        return ApiClient.getInstance().getClient().newCall(builder.build());
    }

    public static Call getImageKindList(boolean isCustom, int gameId, String token) {
        String url;
        if (isCustom) {
            url = BASE_URL + "/games/customlist?game_id=" + gameId;
        } else {
            url = BASE_URL + "/games/kindlist?game_id=" + gameId;
        }
        Request.Builder builder = new Request.Builder().url(url);
        if (token != null) builder.header("Authorization", token);
        return ApiClient.getInstance().getClient().newCall(builder.build());
    }

    public static Call getVersionList(boolean isCustom, int imageKindId, String token) {
        String url;
        if (isCustom) {
            url = BASE_URL + "/games/custom_versionlist?kind_id=" + imageKindId;
        } else {
            url = BASE_URL + "/games/versionlist?kind_id=" + imageKindId;
        }
        Request.Builder builder = new Request.Builder().url(url);
        if (token != null) builder.header("Authorization", token);
        return ApiClient.getInstance().getClient().newCall(builder.build());
    }

    public static Call getSpecList(boolean isCustom, int versionId, int imageKindId, String token) {
        String url = BASE_URL + "/shop/list?version_id=" + versionId + "&kind_id=" + imageKindId + (isCustom ? "&custom=true" : "");
        Request.Builder builder = new Request.Builder().url(url);
        if (token != null) builder.header("Authorization", token);
        return ApiClient.getInstance().getClient().newCall(builder.build());
    }

    public static Call getConfirmation(boolean isCustom, int versionId, int specId, String token) {
        String url = BASE_URL + "/shop/confirmation?version_id=" + versionId + "&item_id=" + specId + (isCustom ? "&custom=true" : "");
        Request.Builder builder = new Request.Builder().url(url);
        if (token != null) builder.header("Authorization", token);
        return ApiClient.getInstance().getClient().newCall(builder.build());
    }

    public static Call createInstance(boolean isCustom, int versionId, int specId, String token) {
        FormBody.Builder fb = new FormBody.Builder();
        fb.add("item_id", String.valueOf(specId));
        fb.add("version_id", String.valueOf(versionId));
        if (isCustom) fb.add("custom", "true");

        Request request = new Request.Builder()
                .url(BASE_INS_URL + "create")
                .post(fb.build())
                .header("Authorization", token == null ? "" : token)
                .build();
        return ApiClient.getInstance().getClient().newCall(request);
    }
}

