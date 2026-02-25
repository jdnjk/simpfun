package cn.jdnjk.simpfun.model;

public class InviteData {
    private final int registerTimes;
    private final int registerVerifyTimes;
    private final int registerTotalIncome;
    private final int registerTotalIncomeFromPro;
    private final String inviteCode;

    public InviteData(int registerTimes, int registerVerifyTimes, int registerTotalIncome, int registerTotalIncomeFromPro, String inviteCode) {
        this.registerTimes = registerTimes;
        this.registerVerifyTimes = registerVerifyTimes;
        this.registerTotalIncome = registerTotalIncome;
        this.registerTotalIncomeFromPro = registerTotalIncomeFromPro;
        this.inviteCode = inviteCode;
    }

    public int getRegisterTimes() {
        return registerTimes;
    }

    public int getRegisterVerifyTimes() {
        return registerVerifyTimes;
    }

    public int getRegisterTotalIncome() {
        return registerTotalIncome;
    }

    public int getRegisterTotalIncomeFromPro() {
        return registerTotalIncomeFromPro;
    }

    public String getInviteCode() {
        return inviteCode;
    }
}
