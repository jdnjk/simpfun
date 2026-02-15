package cn.jdnjk.simpfun.model;

public class InviteData {
    private int registerTimes;
    private int registerVerifyTimes;
    private int registerTotalIncome;
    private int registerTotalIncomeFromPro;
    private String inviteCode;

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
