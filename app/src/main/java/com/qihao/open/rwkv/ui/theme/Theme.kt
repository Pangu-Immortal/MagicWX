/**
 * Theme.kt - Compose Material3 主题定义
 *
 * 功能：
 * - RWKVTheme: 应用全局 Compose 主题
 * - 支持亮色/暗色模式
 */
package com.qihao.open.rwkv.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 亮色配色方案
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1B6B50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F2D0),
    secondaryContainer = Color(0xFFD7E8DE),
    surface = Color(0xFFFBFDF8),
    background = Color(0xFFFBFDF8)
)

// 暗色配色方案
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8BD6B5),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF00513B),
    secondaryContainer = Color(0xFF3A4E43),
    surface = Color(0xFF191C1A),
    background = Color(0xFF191C1A)
)

@Composable
fun RWKVTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Android 12+ 动态取色
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Android 12+ 支持动态主题色
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
