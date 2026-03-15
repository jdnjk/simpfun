package cn.jdnjk.simpfun.model;

public class BenefitCardPlan {
    private final String itemId;
    private final int days;
    private final String publicMoney;
    private final String normalMoney;

    public BenefitCardPlan(String itemId, int days, String publicMoney, String normalMoney) {
        this.itemId = itemId;
        this.days = days;
        this.publicMoney = publicMoney;
        this.normalMoney = normalMoney;
    }

    public String getItemId() {
        return itemId;
    }

    public int getDays() {
        return days;
    }

    public String getPublicMoney() {
        return publicMoney;
    }

    public String getNormalMoney() {
        return normalMoney;
    }

    public String getPrice(String modeId) {
        return "public".equals(modeId) ? publicMoney : normalMoney;
    }

    public String getDaysLabel() {
        return days + "天";
    }
}

