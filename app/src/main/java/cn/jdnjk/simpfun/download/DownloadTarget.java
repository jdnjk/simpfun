package cn.jdnjk.simpfun.download;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 下载的写入目标。
 *
 * <p>三种保存位置里，应用私有目录和公共下载目录（API 23-28）产出 {@code java.io.File}，
 * MediaStore 和 SAF 自定义目录产出 {@code content://} Uri。下载引擎不该关心区别，
 * 所以把「开流 / 收尾 / 清理半成品 / 给用户看的位置」收进这个接口。
 */
public interface DownloadTarget {

    /** 打开写入流。调用方负责关闭。 */
    OutputStream openOutputStream() throws IOException;

    /**
     * 写入成功后调用。MediaStore 实现在这里清 IS_PENDING 让文件对外可见；
     * 其他实现为空操作。
     */
    void finish() throws IOException;

    /** 失败或取消后清理半成品。实现内部吞掉异常——清理失败不该覆盖真正的错误原因。 */
    void deletePartial();

    /** 展示给用户的位置，如 "/sdcard/Download/backup-3.bin"。 */
    String getDisplayPath();

    /** 可交给 ACTION_VIEW 的 Uri，拿不到则返回 null。 */
    @Nullable
    Uri getViewableUri(Context context);

    /**
     * 开流前把目标名改为 {@code newName}（如下载响应头里下发的真实文件名）。
     * 支持则更新内部目标名并返回 true；不支持则返回 false，沿用原名。
     */
    default boolean renameTo(String newName) {
        return false;
    }
}
