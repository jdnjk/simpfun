package cn.jdnjk.simpfun.model;

public class FirewallCheckResult {
    private final String ip;
    private final boolean isBlocked;
    private final String blockType;
    private final String blockTime;
    private final String timeout;
    private final int dailyQueriesRemaining;
    private final int dailyUnblocksRemaining;

    public FirewallCheckResult(String ip, boolean isBlocked, String blockType,
                               String blockTime, String timeout,
                               int dailyQueriesRemaining, int dailyUnblocksRemaining) {
        this.ip = ip;
        this.isBlocked = isBlocked;
        this.blockType = blockType;
        this.blockTime = blockTime;
        this.timeout = timeout;
        this.dailyQueriesRemaining = dailyQueriesRemaining;
        this.dailyUnblocksRemaining = dailyUnblocksRemaining;
    }

    public String getIp() {
        return ip;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public String getBlockType() {
        return blockType;
    }

    public String getBlockTime() {
        return blockTime;
    }

    public String getTimeout() {
        return timeout;
    }

    public int getDailyQueriesRemaining() {
        return dailyQueriesRemaining;
    }

    public int getDailyUnblocksRemaining() {
        return dailyUnblocksRemaining;
    }
}
