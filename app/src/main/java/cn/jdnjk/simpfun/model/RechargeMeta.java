package cn.jdnjk.simpfun.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /pay/web/meta 的解析结果：用户资产、签到卡状态、积分档位、充值方式、签到卡价格与支付渠道。
 */
public class RechargeMeta {

    public int uid;
    public int point;
    public int diamond;
    public boolean doubleSignValid;
    public long doubleSignExpireSeconds;
    public boolean autoSignValid;
    public long autoSignExpireSeconds;

    public final List<RechargeTier> pointTiers = new ArrayList<>();
    public final List<RechargeMode> modes = new ArrayList<>();
    public final Map<String, List<BenefitCardPlan>> cardPlans = new HashMap<>();
    public final List<PayChannel> channels = new ArrayList<>();

    public static class PayChannel {
        public final String id;
        public final String name;
        public final String note;
        public final boolean enabled;

        public PayChannel(String id, String name, String note, boolean enabled) {
            this.id = id;
            this.name = name;
            this.note = note == null ? "" : note;
            this.enabled = enabled;
        }

        /** 展示名，如 "支付宝 1（减免 0.5%）"，不暴露渠道内部 ID。 */
        public String label() {
            if (note.trim().isEmpty()) return name;
            return name + "（" + note + "）";
        }
    }

    public boolean isEmpty() {
        return pointTiers.isEmpty() && modes.isEmpty() && channels.isEmpty();
    }

    public RechargeMode findMode(String modeId) {
        for (RechargeMode mode : modes) {
            if (mode.getId().equals(modeId)) return mode;
        }
        return null;
    }

    /** 默认选中普通充值（meta 缺省时保底）。 */
    public int defaultModeIndex() {
        for (int i = 0; i < modes.size(); i++) {
            if (RechargeMode.MODE_NORMAL.equals(modes.get(i).getId())) return i;
        }
        return modes.isEmpty() ? -1 : 0;
    }

    /** 第一个可用支付渠道的下标；全部不可用时返回 -1。 */
    public int firstEnabledChannelIndex() {
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).enabled) return i;
        }
        return -1;
    }

    public static RechargeMeta parse(JSONObject data) {
        RechargeMeta meta = new RechargeMeta();
        if (data == null) return meta;

        JSONObject user = data.optJSONObject("user");
        if (user != null) {
            meta.uid = user.optInt("uid", 0);
            meta.point = user.optInt("point", 0);
            meta.diamond = user.optInt("diamond", 0);
            meta.doubleSignValid = user.optBoolean("double_sign_card_is_valid", false);
            meta.doubleSignExpireSeconds = user.optLong("double_sign_card_valid_time", 0);
            meta.autoSignValid = user.optBoolean("auto_sign_card_is_valid", false);
            meta.autoSignExpireSeconds = user.optLong("auto_sign_card_valid_time", 0);
        }

        JSONObject itemPriceTiers = data.optJSONObject("item_price_tiers");
        JSONArray pointArray = itemPriceTiers != null
                ? itemPriceTiers.optJSONArray("point")
                : data.optJSONArray("point_price_tiers");
        if (pointArray != null) {
            for (int i = 0; i < pointArray.length(); i++) {
                JSONObject t = pointArray.optJSONObject(i);
                if (t == null) continue;
                meta.pointTiers.add(new RechargeTier(
                        t.optInt("point", 0),
                        t.optString("public_recharge_money", "0"),
                        t.optString("pro_recharge_money", "0")));
            }
        }

        JSONObject rechargeModes = data.optJSONObject("recharge_modes");
        if (rechargeModes != null) {
            JSONObject pub = rechargeModes.optJSONObject("public_recharge");
            if (pub != null) {
                meta.modes.add(new RechargeMode(RechargeMode.MODE_PUBLIC,
                        pub.optString("name", "公益充值"), pub.optString("rule", "")));
            }
            JSONObject pro = rechargeModes.optJSONObject("pro_recharge");
            if (pro != null) {
                meta.modes.add(new RechargeMode(RechargeMode.MODE_NORMAL,
                        pro.optString("name", "普通充值"), pro.optString("rule", "")));
            }
        }

        JSONObject signCardTiers = data.optJSONObject("sign_card_price_tiers");
        for (String itemId : new String[]{"double_sign", "auto_sign"}) {
            JSONArray array = null;
            if (itemPriceTiers != null) array = itemPriceTiers.optJSONArray(itemId);
            if (array == null && signCardTiers != null) array = signCardTiers.optJSONArray(itemId);
            List<BenefitCardPlan> plans = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) continue;
                    plans.add(new BenefitCardPlan(
                            itemId,
                            item.optInt("days", 0),
                            item.optString("public_recharge_money", "0"),
                            item.optString("normal_recharge_money", "0")));
                }
            }
            meta.cardPlans.put(itemId, plans);
        }

        JSONArray channelArray = data.optJSONArray("channels");
        if (channelArray != null) {
            for (int i = 0; i < channelArray.length(); i++) {
                JSONObject c = channelArray.optJSONObject(i);
                if (c == null) continue;
                String note = firstNonEmpty(c, "note", "discount", "desc", "description", "tip");
                meta.channels.add(new PayChannel(
                        c.optString("id", ""),
                        c.optString("name", "支付渠道"),
                        note,
                        c.optBoolean("enabled", true)));
            }
        }
        return meta;
    }

    private static String firstNonEmpty(JSONObject obj, String... keys) {
        for (String key : keys) {
            String v = obj.optString(key, "");
            if (!v.trim().isEmpty()) return v;
        }
        return "";
    }
}
