package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.blankj.utilcode.util.ConvertUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.databinding.DialogPlayingControlBinding;
import com.github.tvbox.osc.player.controller.VodController;
import com.github.tvbox.osc.ui.widget.FilterChip;
import com.github.tvbox.osc.util.PlayerHelper;

import java.util.ArrayList;
import java.util.Collections;

/**
 * 播放控制弹窗"缩放/播放器"横向档位行(与倍速行同形制的 FilterChip 排布)。
 * 点播全屏右侧抽屉与竖屏底部弹窗共用同一份实现。
 * 播放器档位依赖设备上实际可用的播放器列表, 故用代码生成 chip,
 * 样式逐项对齐 styles.xml 的 AppChip(FilterChip 以 selected 映射 checked 态)。
 */
final class PlayingControlRows {

    private PlayingControlRows() {
    }

    /** 缩放档位: 与 VideoView.SCREEN_SCALE_* 及 PlayerHelper.getScaleName 一一对应 */
    private static final int[] SCALE_TYPES = {
            xyz.doikki.videoplayer.player.VideoView.SCREEN_SCALE_DEFAULT,
            xyz.doikki.videoplayer.player.VideoView.SCREEN_SCALE_16_9,
            xyz.doikki.videoplayer.player.VideoView.SCREEN_SCALE_4_3,
            xyz.doikki.videoplayer.player.VideoView.SCREEN_SCALE_MATCH_PARENT,
            xyz.doikki.videoplayer.player.VideoView.SCREEN_SCALE_ORIGINAL,
            xyz.doikki.videoplayer.player.VideoView.SCREEN_SCALE_CENTER_CROP,
    };

    /** 绑定缩放行点击并刷新选中态(chip 文字取自 getScaleName, 与控制器按钮文字恒等, 选中态按文字比对) */
    static void setupScaleRow(DialogPlayingControlBinding binding, VodController controller) {
        View.OnClickListener listener = v -> {
            controller.selectScaleType(scaleTypeOf(v));
            updateScaleRow(binding, controller);
        };
        binding.scale0.setOnClickListener(listener);
        binding.scale1.setOnClickListener(listener);
        binding.scale2.setOnClickListener(listener);
        binding.scale3.setOnClickListener(listener);
        binding.scale4.setOnClickListener(listener);
        binding.scale5.setOnClickListener(listener);
        updateScaleRow(binding, controller);
    }

    static void updateScaleRow(DialogPlayingControlBinding binding, VodController controller) {
        String current = controller.mPlayerScaleBtn.getText().toString();
        binding.scale0.setSelected(current.equals(binding.scale0.getText().toString()));
        binding.scale1.setSelected(current.equals(binding.scale1.getText().toString()));
        binding.scale2.setSelected(current.equals(binding.scale2.getText().toString()));
        binding.scale3.setSelected(current.equals(binding.scale3.getText().toString()));
        binding.scale4.setSelected(current.equals(binding.scale4.getText().toString()));
        binding.scale5.setSelected(current.equals(binding.scale5.getText().toString()));
    }

    private static int scaleTypeOf(View v) {
        int id = v.getId();
        if (id == R.id.scale1) return SCALE_TYPES[1];
        if (id == R.id.scale2) return SCALE_TYPES[2];
        if (id == R.id.scale3) return SCALE_TYPES[3];
        if (id == R.id.scale4) return SCALE_TYPES[4];
        if (id == R.id.scale5) return SCALE_TYPES[5];
        return SCALE_TYPES[0];
    }

    /**
     * 生成播放器行档位 chip(仅设备上可用的播放器)并刷新选中态。
     * @param onPlayerChanged 档位切换后的回调(弹窗刷新解码按钮可见性等)
     */
    static void setupPlayerRow(DialogPlayingControlBinding binding, VodController controller,
                               Runnable onPlayerChanged) {
        Context context = binding.containerPlayer.getContext();
        binding.containerPlayer.removeAllViews();

        ArrayList<Integer> playerTypes = PlayerHelper.getExistPlayerTypes();
        Collections.sort(playerTypes);
        String current = controller.mPlayerBtn.getText().toString();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = ConvertUtils.dp2px(8);

        for (Integer playerType : playerTypes) {
            FilterChip chip = createChip(context, PlayerHelper.getPlayerName(playerType));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                if (chip.isSelected()) {
                    return;
                }
                controller.selectPlayerType(playerType);
                updatePlayerRow(binding, controller);
                if (onPlayerChanged != null) {
                    onPlayerChanged.run();
                }
            });
            chip.setSelected(current.equals(chip.getText().toString()));
            binding.containerPlayer.addView(chip);
        }
    }

    static void updatePlayerRow(DialogPlayingControlBinding binding, VodController controller) {
        String current = controller.mPlayerBtn.getText().toString();
        for (int i = 0; i < binding.containerPlayer.getChildCount(); i++) {
            View child = binding.containerPlayer.getChildAt(i);
            child.setSelected(current.equals(((android.widget.TextView) child).getText().toString()));
        }
    }

    /** 代码生成 AppChip 样式的 FilterChip(XML 里的 style 属性无法用于代码构造, 逐项对齐) */
    private static FilterChip createChip(Context context, String text) {
        FilterChip chip = new FilterChip(context);
        chip.setText(text);
        chip.setTextColor(ContextCompat.getColorStateList(context, R.color.selector_chip_text));
        chip.setChipBackgroundColor(ContextCompat.getColorStateList(context, R.color.selector_chip_bg));
        chip.setChipStrokeColor(ContextCompat.getColorStateList(context, R.color.selector_chip_stroke));
        chip.setChipStrokeWidth(ConvertUtils.dp2px(1));
        chip.setCheckedIconVisible(false);
        chip.setChipStartPadding(ConvertUtils.dp2px(10));
        chip.setChipEndPadding(ConvertUtils.dp2px(10));
        chip.setTextStartPadding(0);
        chip.setTextEndPadding(0);
        chip.setEnsureMinTouchTargetSize(false);
        return chip;
    }
}
