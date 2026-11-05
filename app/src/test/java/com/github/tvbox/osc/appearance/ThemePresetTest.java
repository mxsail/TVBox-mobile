package com.github.tvbox.osc.appearance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.github.tvbox.osc.base.ThemeManager;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * 主题颜色预设表自检: 8 套预设 id 唯一、覆盖层/色板资源有效、经典蓝=现有品牌色板覆盖层。
 */
public class ThemePresetTest {

    @Test
    public void presets_areComplete_andUnique() {
        assertEquals(8, ThemeManager.getPRESETS().size());
        Set<String> ids = new HashSet<>();
        ThemeManager.getPRESETS().forEach(p -> {
            assertTrue("预设 id 不能为空", p.getId() != null && !p.getId().isEmpty());
            assertTrue("预设名不能为空", p.getLabel() != null && !p.getLabel().isEmpty());
            assertNotEquals("覆盖层资源必须有效", 0, p.getOverlayRes());
            assertNotEquals("色板资源必须有效", 0, p.getSwatchRes());
            ids.add(p.getId());
        });
        assertEquals("预设 id 必须唯一", ThemeManager.getPRESETS().size(), ids.size());
    }

    @Test
    public void presetLookup_works_andUnknownReturnsNull() {
        assertNotNull(ThemeManager.presetOf("blue"));
        assertNotNull(ThemeManager.presetOf("teal"));
        assertNotNull(ThemeManager.presetOf("purple"));
        assertEquals(null, ThemeManager.presetOf("not_exists"));
    }

    @Test
    public void classicBlue_usesBrandPaletteOverlay() {
        ThemeManager.Preset blue = ThemeManager.presetOf("blue");
        assertNotNull(blue);
        // 经典蓝直接复用 md_* 品牌色板覆盖层, 保证与默认外观像素级一致
        assertEquals(com.github.tvbox.osc.R.style.ThemeOverlay_Preset_Blue, blue.getOverlayRes());
        assertEquals(com.github.tvbox.osc.R.color.md_primary, blue.getSwatchRes());
    }
}
