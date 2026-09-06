package cn.jdnjk.simpfun.utils;

import net.schmizz.sshj.userauth.UserAuthException;

import java.security.Provider;
import java.security.Security;
import java.util.Locale;

/**
 * 用 SSHJ 前后要做的两件小事，各处共用一份。
 */
public final class SftpSupport {

    private SftpSupport() {
    }

    /**
     * SSHJ 需要完整版 BouncyCastle。系统里可能已经注册了一个同名的裁剪版，
     * 用打包进来的这份顶掉它，否则握手会因为缺算法失败。
     */
    public static void ensureBouncyCastleRegistered() {
        try {
            Provider bc = Security.getProvider("BC");
            if (bc == null || !bc.getClass().getName().equals("org.bouncycastle.jce.provider.BouncyCastleProvider")) {
                Security.removeProvider("BC");
                Security.insertProviderAt(new org.bouncycastle.jce.provider.BouncyCastleProvider(), 1);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 异常链里出现鉴权失败就返回 true。调用方据此决定是换一份新密码重试，还是直接报错。
     */
    public static boolean isAuthFailure(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof UserAuthException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("auth")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
