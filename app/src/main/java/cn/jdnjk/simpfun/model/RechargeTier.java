package cn.jdnjk.simpfun.model;

public class RechargeTier {
    private final int point;
    private final String publicMoney;
    private final String proMoney;

    public RechargeTier(int point, String publicMoney, String proMoney) {
        this.point = point;
        this.publicMoney = publicMoney;
        this.proMoney = proMoney;
    }

    public int getPoint() {
        return point;
    }

    public String getPublicMoney() {
        return publicMoney;
    }

    public String getProMoney() {
        return proMoney;
    }

    public String getPrice(String modeId) {
        return "normal".equals(modeId) ? proMoney : publicMoney;
    }
}

