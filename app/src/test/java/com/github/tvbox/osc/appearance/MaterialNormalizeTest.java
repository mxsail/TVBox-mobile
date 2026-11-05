package com.github.tvbox.osc.appearance;

import static org.junit.Assert.assertEquals;

import android.os.Build;

import com.github.tvbox.osc.base.ThemeManager;
import com.github.tvbox.osc.view.MaterialBarHelper;

import org.junit.Test;

/**
 * 界面材质降级规则: 高斯模糊/液态玻璃仅 Android 12+(API31)可用, 低版本降级半透明。
 */
public class MaterialNormalizeTest {

    @Test
    public void solid_and_translucent_neverDowngrade() {
        assertEquals(ThemeManager.MATERIAL_SOLID, MaterialBarHelper.normalizeMaterial(ThemeManager.MATERIAL_SOLID, 24));
        assertEquals(ThemeManager.MATERIAL_TRANSLUCENT, MaterialBarHelper.normalizeMaterial(ThemeManager.MATERIAL_TRANSLUCENT, 24));
        assertEquals(ThemeManager.MATERIAL_SOLID, MaterialBarHelper.normalizeMaterial(ThemeManager.MATERIAL_SOLID, 34));
    }

    @Test
    public void blur_downgrades_below_S() {
        assertEquals(ThemeManager.MATERIAL_TRANSLUCENT, MaterialBarHelper.normalizeMaterial(ThemeManager.MATERIAL_BLUR, Build.VERSION_CODES.R));
        assertEquals(ThemeManager.MATERIAL_BLUR, MaterialBarHelper.normalizeMaterial(ThemeManager.MATERIAL_BLUR, Build.VERSION_CODES.S));
        assertEquals(ThemeManager.MATERIAL_BLUR, MaterialBarHelper.normalizeMaterial(ThemeManager.MATERIAL_BLUR, 34));
    }

    @Test
    public void liquid_downgrades_below_S() {
        assertEquals(ThemeManager.MATERIAL_TRANSLUCENT, MaterialBarHelper.normalizeMaterial(ThemeManager.MATERIAL_LIQUID, Build.VERSION_CODES.R));
        assertEquals(ThemeManager.MATERIAL_LIQUID, MaterialBarHelper.normalizeMaterial(ThemeManager.MATERIAL_LIQUID, Build.VERSION_CODES.S));
    }
}
