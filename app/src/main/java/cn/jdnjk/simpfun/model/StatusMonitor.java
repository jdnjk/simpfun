package cn.jdnjk.simpfun.model;

public class StatusMonitor {
    private final long id;
    private final String name;
    private final String type;
    private final String url;
    private final boolean sendUrl;
    private final long certExpiryDaysRemaining;
    private final boolean validCert;

    public StatusMonitor(long id, String name, String type, String url, boolean sendUrl,
                         long certExpiryDaysRemaining, boolean validCert) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.url = url;
        this.sendUrl = sendUrl;
        this.certExpiryDaysRemaining = certExpiryDaysRemaining;
        this.validCert = validCert;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public boolean isSendUrl() {
        return sendUrl;
    }

    public long getCertExpiryDaysRemaining() {
        return certExpiryDaysRemaining;
    }

    public boolean isValidCert() {
        return validCert;
    }

    public boolean hasUrl() {
        return sendUrl && url != null && !url.isEmpty();
    }
}
