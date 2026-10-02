package com.xfy.randomdice.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xfy.randomdice.audio.SoundTimbre

/**
 * 右下角的工具托盘：**⋮ 点一下，向左展开三个开关**。
 *
 * 1. 摇一摇（骰子图标）
 * 2. 震动（手机图标）—— 各机型马达差别很大，所以给个总开关
 * 3. 音效（喇叭图标）—— 点开下拉选材质
 *
 * 收起来时只占一个 ⋮；展开时那三个按钮从右边滑出来（`expandHorizontally` 从 End 展开），
 * 这样它永远贴着屏幕右下角，不会因为展开而把自己推出屏幕。
 */
@Composable
fun ToolTray(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean,
    shakeEnabled: Boolean,
    onShakeEnabledChange: (Boolean) -> Unit,
    vibrationEnabled: Boolean,
    onVibrationEnabledChange: (Boolean) -> Unit,
    timbre: SoundTimbre,
    onTimbreChange: (SoundTimbre) -> Unit,
    modifier: Modifier = Modifier,
) {
    var soundMenuOpen by remember { mutableStateOf(false) }
    // 收起托盘时把下拉也关掉：不然那个菜单会跟着收起前的状态"记着"，下次展开又冒出来
    LaunchedEffect(expanded) {
        if (!expanded) soundMenuOpen = false
    }
    val onColor = MaterialTheme.colorScheme.primary
    val offColor = MaterialTheme.colorScheme.onSurfaceVariant
    // 两个开关共用一套配色：开 = 主色，关 = 灰
    val toggleColors = IconButtonDefaults.iconToggleButtonColors(
        checkedContentColor = onColor,
        contentColor = offColor,
    )

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(durationMillis = 160)) +
                expandHorizontally(tween(durationMillis = 220), expandFrom = Alignment.End),
            exit = fadeOut(tween(durationMillis = 120)) +
                shrinkHorizontally(tween(durationMillis = 180), shrinkTowards = Alignment.End),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconToggleButton(
                        checked = shakeEnabled,
                        onCheckedChange = onShakeEnabledChange,
                        enabled = enabled,
                        colors = toggleColors,
                    ) {
                        Icon(
                            imageVector = ShakeDiceIcon,
                            contentDescription = if (shakeEnabled) "摇一摇：已开" else "摇一摇：已关",
                        )
                    }

                    IconToggleButton(
                        checked = vibrationEnabled,
                        onCheckedChange = onVibrationEnabledChange,
                        enabled = enabled,
                        colors = toggleColors,
                    ) {
                        Icon(
                            imageVector = PhoneShakeIcon,
                            contentDescription = if (vibrationEnabled) "震动：已开" else "震动：已关",
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { soundMenuOpen = true },
                            enabled = enabled,
                        ) {
                            Icon(
                                imageVector = SpeakerIcon,
                                contentDescription = "音效：${timbre.label}",
                                tint = if (timbre == SoundTimbre.Off) offColor else onColor,
                            )
                        }
                        DropdownMenu(
                            expanded = soundMenuOpen,
                            onDismissRequest = { soundMenuOpen = false },
                        ) {
                            SoundTimbre.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(text = option.label) },
                                    onClick = {
                                        onTimbreChange(option)
                                        soundMenuOpen = false
                                    },
                                    trailingIcon = {
                                        if (option == timbre) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = { onExpandedChange(!expanded) },
            enabled = enabled,
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = if (expanded) "收起工具" else "展开工具",
                tint = if (expanded) onColor else offColor,
            )
        }
    }
}
