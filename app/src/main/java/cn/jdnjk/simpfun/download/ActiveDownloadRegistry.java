package cn.jdnjk.simpfun.download;

import androidx.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内的“活跃下载取消”注册表。
 *
 * <p>下载转后台后，进度通知带一个「取消」动作；该动作由系统经
 * {@code DownloadCancelReceiver} 触发，无法直接调用 Fragment/Controller 里的对象。
 * 这里在下载开始注册一个按 notificationId 取取消回调的单例，receiver 据此路由。
 */
public final class ActiveDownloadRegistry {

    private static final ActiveDownloadRegistry INSTANCE = new ActiveDownloadRegistry();

    public static ActiveDownloadRegistry getInstance() {
        return INSTANCE;
    }

    private final Map<Integer, Runnable> cancelActions = new ConcurrentHashMap<>();

    private ActiveDownloadRegistry() {
    }

    /** 下载开始时注册取消动作；同一 notificationId 会覆盖旧注册。 */
    public void register(int notificationId, Runnable cancelAction) {
        if (cancelAction != null) {
            cancelActions.put(notificationId, cancelAction);
        }
    }

    /** 下载结束（成功/失败/取消）时清除注册，避免回调泄漏。 */
    public void unregister(int notificationId) {
        cancelActions.remove(notificationId);
    }

    /** 触发指定下载的取消。返回是否找到了对应下载。 */
    public boolean cancel(int notificationId) {
        Runnable action = cancelActions.remove(notificationId);
        if (action == null) {
            return false;
        }
        try {
            action.run();
        } catch (RuntimeException e) {
            return false;
        }
        return true;
    }

    @Nullable
    public Runnable get(int notificationId) {
        return cancelActions.get(notificationId);
    }
}
