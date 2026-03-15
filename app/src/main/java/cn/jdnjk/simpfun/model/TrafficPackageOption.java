package cn.jdnjk.simpfun.model;

public class TrafficPackageOption {
    private final int traffic;
    private final int pointCost;

    public TrafficPackageOption(int traffic, int pointCost) {
        this.traffic = traffic;
        this.pointCost = pointCost;
    }

    public int getTraffic() {
        return traffic;
    }

    public int getPointCost() {
        return pointCost;
    }

    public String getTrafficLabel() {
        return traffic + "G流量";
    }

    public String getPointCostLabel() {
        return pointCost + "积分";
    }
}

