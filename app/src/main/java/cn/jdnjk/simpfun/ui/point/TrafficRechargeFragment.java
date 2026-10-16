package cn.jdnjk.simpfun.ui.point;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cn.jdnjk.simpfun.R;
import cn.jdnjk.simpfun.adapter.OptionCardAdapter;
import cn.jdnjk.simpfun.api.ins.MainApi;
import cn.jdnjk.simpfun.api.UserApi;
import cn.jdnjk.simpfun.databinding.FragmentRechargeTrafficBinding;
import cn.jdnjk.simpfun.model.OptionCard;
import cn.jdnjk.simpfun.model.RechargeMeta;
import cn.jdnjk.simpfun.utils.InstanceDetailStore;

/**
 * 充值 · 充流量：为选定实例购买流量包，消耗积分；余额不足时禁用购买。
 */
public class TrafficRechargeFragment extends BaseRechargeFragment {

    private static final String TAG = "TrafficRechargeFragment";
    private static final int MORE_TRAFFIC_MULTIPLIER = 3;
    private static final int[][] PACKAGE_SPECS = {{1, 10}, {3, 30}, {10, 100}, {30, 300}};

    private FragmentRechargeTrafficBinding binding;
    private OptionCardAdapter packageAdapter;

    private final List<Instance> instances = new ArrayList<>();
    private final Map<Integer, Long> remainBytes = new HashMap<>();
    private final Map<Integer, Boolean> moreTrafficMap = new HashMap<>();
    private final Map<Integer, Boolean> loadingDetail = new HashMap<>();

    private int selectedInstanceIndex = -1;
    private int selectedPackageIndex = 0;
    private boolean moreTraffic = false;
    private ArrayAdapter<String> instanceSpinnerAdapter;

    private static class Instance {
        final int id;
        final String name;

        Instance(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRechargeTrafficBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        packageAdapter = new OptionCardAdapter(this::onPackageClick);
        packageAdapter.setCardHeightDp(80);
        binding.recyclerPackages.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.recyclerPackages.setAdapter(packageAdapter);

        instanceSpinnerAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, new ArrayList<>());
        binding.spinnerInstance.setAdapter(instanceSpinnerAdapter);
        binding.spinnerInstance.setOnItemClickListener((parent, v, position, id) -> {
            selectedInstanceIndex = position;
            updateMultiplierForSelectedInstance();
            requestInstanceDetail();
            submitPackages();
            updateBuyButton();
        });

        binding.btnBuy.setOnClickListener(v -> onBuyClick());

        submitPackages();
        loadInstances();

        RechargeHomeFragment parent = home();
        if (parent != null) parent.whenMetaReady(this);
    }

    private void onPackageClick(int position) {
        selectedPackageIndex = position;
        submitPackages();
        updateBuyButton();
    }

    private void onBuyClick() {
        Instance instance = currentInstance();
        PackageSpec spec = currentSpec();
        if (instance == null || spec == null) return;

        StringBuilder message = new StringBuilder()
                .append("实例：").append(instance.name).append("\n");
        Long remain = remainBytes.get(instance.id);
        if (remain != null && remain >= 0) {
            message.append("剩余流量：").append(formatTrafficBytes(remain)).append("\n");
        }
        message.append("流量包：").append(spec.traffic).append(" GB\n")
                .append("消耗积分：").append(spec.cost).append("\n\n确认购买？");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("确认购买流量")
                .setMessage(message.toString())
                .setNegativeButton("取消", null)
                .setPositiveButton("确认购买", (d, w) -> executeBuy(instance, spec))
                .show();
    }

    private void executeBuy(Instance instance, PackageSpec spec) {
        String token = getToken();
        if (token.isEmpty()) {
            showSnack("请先登录");
            return;
        }
        binding.btnBuy.setEnabled(false);
        binding.btnBuy.setText("请求中...");

        payApi.buyTrafficPackage(token, instance.id, spec.traffic, new cn.jdnjk.simpfun.api.PayApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isViewAlive()) return;
                showSnack(data.optString("msg", "购买流量成功"));
                InstanceDetailStore.getInstance().clear(instance.id);
                remainBytes.remove(instance.id);
                moreTrafficMap.remove(instance.id);
                requestInstanceDetail();
                RechargeHomeFragment parent = home();
                if (parent != null) parent.reloadMetaAndNotify();
                updateBuyButton();
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e(TAG, "buyTrafficPackage failed: " + errorMsg);
                if (!isViewAlive()) return;
                showSnack(errorMsg);
                updateBuyButton();
            }
        });
    }

    @Override
    public void onMetaLoaded(RechargeMeta meta) {
        this.meta = meta;
        if (!isViewAlive()) return;
        binding.tvTrafficBalance.setText(String.format(Locale.getDefault(), "当前积分 %,d", meta.point));
        updateBuyButton();
    }

    // ---------------------------------------------------------------- 实例

    private void loadInstances() {
        Context context = getContext();
        if (context == null) return;
        String token = getToken();
        if (token.isEmpty()) return;

        new UserApi(requireContext().getApplicationContext()).getInstanceList(token, new UserApi.InstanceCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!isViewAlive()) return;
                applyInstances(data.optJSONArray("list"));
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e(TAG, "loadInstances failed: " + errorMsg);
                if (!isViewAlive()) return;
                applyInstances(null);
            }
        });
    }

    private void applyInstances(@Nullable JSONArray list) {
        instances.clear();
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject obj = list.optJSONObject(i);
                if (obj == null) continue;
                int id = obj.optInt("id", -1);
                if (id <= 0) continue;
                String name = obj.optString("name", "").trim();
                if (name.isEmpty()) name = "未命名实例";
                instances.add(new Instance(id, name));
            }
        }

        int preselect = -1;
        RechargeHomeFragment parent = home();
        if (parent != null) preselect = parent.getPreselectInstance();

        selectedInstanceIndex = instances.isEmpty() ? -1 : 0;
        if (preselect > 0) {
            for (int i = 0; i < instances.size(); i++) {
                if (instances.get(i).id == preselect) {
                    selectedInstanceIndex = i;
                    break;
                }
            }
        }

        boolean empty = instances.isEmpty();
        binding.inputInstance.setEnabled(!empty);
        binding.inputInstance.setHelperText(empty ? "暂无实例，请先创建服务器" : "切换后显示对应实例剩余流量");
        refreshInstanceSpinner();
        updateMultiplierForSelectedInstance();
        requestInstanceDetail();
        submitPackages();
        updateBuyButton();
    }

    private void refreshInstanceSpinner() {
        if (!isViewAlive()) return;
        List<String> labels = new ArrayList<>();
        for (Instance instance : instances) {
            labels.add(instanceLabel(instance));
        }
        instanceSpinnerAdapter.clear();
        instanceSpinnerAdapter.addAll(labels);
        instanceSpinnerAdapter.notifyDataSetChanged();

        if (!instances.isEmpty() && selectedInstanceIndex >= 0 && selectedInstanceIndex < instances.size()) {
            binding.spinnerInstance.setText(labels.get(selectedInstanceIndex), false);
        }
    }

    /** "生存服 · 剩余 2.35 GB"，不展示内部 ID。 */
    private String instanceLabel(Instance instance) {
        Long remain = remainBytes.get(instance.id);
        if (remain != null && remain >= 0) {
            return instance.name + " · 剩余 " + formatTrafficBytes(remain);
        }
        return instance.name;
    }

    private void updateMultiplierForSelectedInstance() {
        boolean enabled = false;
        Instance instance = currentInstance();
        if (instance != null) {
            enabled = Boolean.TRUE.equals(moreTrafficMap.get(instance.id));
        }
        if (moreTraffic != enabled) {
            moreTraffic = enabled;
            submitPackages();
            updateBuyButton();
        }
    }

    private void requestInstanceDetail() {
        Instance instance = currentInstance();
        if (instance == null || !isAdded()) return;
        if (remainBytes.containsKey(instance.id) || Boolean.TRUE.equals(loadingDetail.get(instance.id))) {
            refreshInstanceSpinner();
            return;
        }
        String token = getToken();
        if (token.isEmpty()) return;

        loadingDetail.put(instance.id, true);
        refreshInstanceSpinner();

        new MainApi(requireContext()).getInstanceDetail(token, String.valueOf(instance.id), new MainApi.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                loadingDetail.remove(instance.id);
                if (!isViewAlive()) return;
                JSONObject detail = data.optJSONObject("data");
                JSONObject traffic = detail != null ? detail.optJSONObject("traffic") : null;
                if (traffic != null) {
                    remainBytes.put(instance.id, traffic.optLong("remain_bytes", -1));
                    moreTrafficMap.put(instance.id, traffic.optBoolean("more_traffic", false));
                }
                updateMultiplierForSelectedInstance();
                refreshInstanceSpinner();
                updateBuyButton();
            }

            @Override
            public void onFailure(String errorMsg) {
                Log.e(TAG, "getInstanceDetail failed: " + errorMsg);
                loadingDetail.remove(instance.id);
                if (!isViewAlive()) return;
                refreshInstanceSpinner();
            }
        });
    }

    // ---------------------------------------------------------------- 流量包

    private static class PackageSpec {
        final int traffic;
        final int cost;

        PackageSpec(int traffic, int cost) {
            this.traffic = traffic;
            this.cost = cost;
        }
    }

    private List<PackageSpec> currentSpecs() {
        int multiplier = moreTraffic ? MORE_TRAFFIC_MULTIPLIER : 1;
        List<PackageSpec> specs = new ArrayList<>();
        for (int[] item : PACKAGE_SPECS) {
            specs.add(new PackageSpec(item[0] * multiplier, item[1]));
        }
        return specs;
    }

    @Nullable
    private PackageSpec currentSpec() {
        List<PackageSpec> specs = currentSpecs();
        if (specs.isEmpty() || selectedPackageIndex < 0 || selectedPackageIndex >= specs.size()) return null;
        return specs.get(selectedPackageIndex);
    }

    @Nullable
    private Instance currentInstance() {
        if (instances.isEmpty() || selectedInstanceIndex < 0 || selectedInstanceIndex >= instances.size()) {
            return null;
        }
        return instances.get(selectedInstanceIndex);
    }

    private void submitPackages() {
        List<OptionCard> cards = new ArrayList<>();
        for (PackageSpec spec : currentSpecs()) {
            cards.add(OptionCard.of(spec.traffic + " GB", spec.cost + " 积分"));
        }
        if (selectedPackageIndex >= cards.size()) selectedPackageIndex = 0;
        packageAdapter.submit(cards, selectedPackageIndex);
    }

    private void updateBuyButton() {
        if (!isViewAlive()) return;
        PackageSpec spec = currentSpec();
        Instance instance = currentInstance();
        boolean hasBalance = meta != null && spec != null && meta.point >= spec.cost;
        boolean enabled = instance != null && spec != null && hasBalance;
        binding.btnBuy.setEnabled(enabled);
        binding.btnBuy.setText(spec != null
                ? String.format(Locale.getDefault(), "立即购买 %,d 积分", spec.cost)
                : "立即购买");
    }

    private static String formatTrafficBytes(long bytes) {
        if (bytes < 0) return "未知";
        double gb = bytes / 1024d / 1024d / 1024d;
        return String.format(Locale.getDefault(), "%.2f GB", gb);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        packageAdapter = null;
        instanceSpinnerAdapter = null;
        super.onDestroyView();
    }
}
