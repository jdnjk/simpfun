package cn.jdnjk.simpfun.model;

/**
 * 可单选的选项卡片（档位 / 充值方式 / 卡类型 / 时长 / 流量包）的展示数据。
 */
public class OptionCard {
    public final String title;
    public final CharSequence body;

    public OptionCard(String title, CharSequence body) {
        this.title = title;
        this.body = body;
    }

    public static OptionCard of(String title, String body) {
        return new OptionCard(title, body);
    }

    public static OptionCard spanned(String title, CharSequence body) {
        return new OptionCard(title, body);
    }
}
