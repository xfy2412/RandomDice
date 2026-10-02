package com.xfy.randomdice.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/*
 * 配色说明：
 * lightColorScheme() / darkColorScheme() 不传参时用的就是 Material 3 官方基线配色；
 * Android 12（API 31）及以上再叠加「动态取色」，跟着系统壁纸走。
 * 以后想换成自己的品牌色，只要在下面两张表里收窄 primary/secondary/tertiary 一族即可。
 */
private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun RandomDiceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
