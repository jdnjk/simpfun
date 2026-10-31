package cn.jdnjk.simpfun.ui.ins.term;

import android.content.Context;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.appcompat.widget.AppCompatEditText;

/** 允许长命令自动换行显示，同时保留软键盘的“发送”动作。 */
public class TerminalCommandEditText extends AppCompatEditText {
    public TerminalCommandEditText(Context context) {
        super(context);
    }

    public TerminalCommandEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TerminalCommandEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection connection = super.onCreateInputConnection(outAttrs);
        // TextView 默认给多行输入添加 NO_ENTER_ACTION，会把发送键变成换行键。
        outAttrs.imeOptions = (outAttrs.imeOptions
                & ~EditorInfo.IME_FLAG_NO_ENTER_ACTION & ~EditorInfo.IME_MASK_ACTION)
                | EditorInfo.IME_ACTION_SEND;
        return connection;
    }
}
