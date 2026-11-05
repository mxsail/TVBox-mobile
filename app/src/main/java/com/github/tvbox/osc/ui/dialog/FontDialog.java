package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.lxj.xpopup.core.CenterPopupView;
import com.orhanobut.hawk.Hawk;

import java.io.File;

/**
 * 字体设置弹窗: 展示当前字体+预览, 支持从手机导入(.ttf/.otf)与恢复默认。
 * 导入/重置的实际落盘与重建由宿主 Activity 回调完成。
 */
public class FontDialog extends CenterPopupView {

    public interface Callback {
        /** 点击「从手机导入」, 由 Activity 拉起系统文件选择器 */
        void onImport();

        /** 点击「恢复默认」 */
        void onReset();
    }

    private final Callback callback;

    public FontDialog(@NonNull Context context, Callback callback) {
        super(context);
        this.callback = callback;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_font;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        String path = Hawk.get(HawkConfig.FONT_PATH, "");
        TextView tvCurrent = findViewById(R.id.tvCurrentFont);
        tvCurrent.setText(path.isEmpty() ? "当前: 系统默认" : "当前: " + new File(path).getName());
        // 预览文字所在视图由 FontFactory2 创建, 已自动套用生效字体
        findViewById(R.id.btnFontImport).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            dismiss();
            callback.onImport();
        });
        findViewById(R.id.btnFontReset).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            dismiss();
            callback.onReset();
        });
    }
}
