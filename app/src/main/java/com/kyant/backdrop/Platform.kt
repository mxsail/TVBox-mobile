package com.kyant.backdrop

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

// Source: https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0)
// Android-only implementation merged from androidMain + commonMain

@ChecksSdkIntAtLeast(Build.VERSION_CODES.S)
fun isRenderEffectSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@ChecksSdkIntAtLeast(Build.VERSION_CODES.TIRAMISU)
fun isRuntimeShaderSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
