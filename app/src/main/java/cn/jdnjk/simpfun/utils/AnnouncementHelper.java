package cn.jdnjk.simpfun.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import cn.jdnjk.simpfun.api.UserApi;

public final class AnnouncementHelper {

    private static final String PREFS_USER_INFO = "user_info";
    private static final String PREFS_TOKEN = "token";
    private static final String KEY_TITLE = "announcement_title";
    private static final String KEY_TEXT = "announcement_text";
    private static final String KEY_SHOW = "announcement_show";

    private AnnouncementHelper() {
    }

    public static void maybeShowAnnouncement(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_SHOW, false)) {
            return;
        }
        String title = prefs.getString(KEY_TITLE, "暂无公告");
        String text = prefs.getString(KEY_TEXT, "");
        if (text.isEmpty()) {
            text = "暂无内容";
        }
        showAnnouncementDialog(context, title, text);
    }

    public static void showAnnouncementDialog(Context context, String title, String text) {
        Spanned styledText;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            styledText = Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT);
        } else {
            styledText = Html.fromHtml(text);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(styledText)
                .setPositiveButton("我知道了", (dialogInterface, which) -> {
                    markAnnouncementAsRead(context);
                    context.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
                            .edit().putBoolean(KEY_SHOW, false).apply();
                })
                .setCancelable(false)
                .show();

        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
            messageView.setLinksClickable(true);
            messageView.setTextIsSelectable(false);
        }
    }

    private static void markAnnouncementAsRead(Context context) {
        String token = context.getSharedPreferences(PREFS_TOKEN, Context.MODE_PRIVATE)
                .getString("token", null);
        if (token != null) {
            new UserApi(context).readAnnouncement(token);
        }
    }
}
