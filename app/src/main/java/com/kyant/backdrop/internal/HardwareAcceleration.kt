package com.kyant.backdrop.internal

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

/**
 * 软件渲染画布(如 XPopup 弹窗背景经 view2Bitmap 对 DecorView 截图)不支持
 * Canvas.drawRenderNode, 直接 drawLayer(GraphicsLayer) 会抛
 * IllegalArgumentException("Software rendering doesn't support drawRenderNode")。
 * 依赖 GraphicsLayer 的效果绘制前必须经此判断, 软件画布上跳过效果只画内容
 * (截图里仅损失液态玻璃细节, 对被模糊的弹窗背景无碍)。
 */
internal fun DrawScope.isHardwareAccelerated(): Boolean =
    drawContext.canvas.nativeCanvas.isHardwareAccelerated
