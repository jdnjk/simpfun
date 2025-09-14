package cn.jdnjk.simpfun.api.ins.file;

import org.json.JSONObject;

public interface FileCallback {
    void onSuccess(JSONObject data);
    void onFailure(String errorMsg);
}