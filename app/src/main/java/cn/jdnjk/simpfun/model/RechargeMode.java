package cn.jdnjk.simpfun.model;

public class RechargeMode {
    private final String id;
    private final String name;
    private final String rule;

    public RechargeMode(String id, String name, String rule) {
        this.id = id;
        this.name = name;
        this.rule = rule;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRule() {
        return rule;
    }
}

