package com.xfy.randomdice.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.xfy.randomdice.data.DecisionStore
import com.xfy.randomdice.data.SettingsStore

/** 页面切换动画时长。 */
private const val SCREEN_TRANSITION_MS = 320

/**
 * 应用外壳：摇骰子页 ↔ 决策记录页。
 *
 * 只有一个布尔开关（没有引 Navigation 库）—— 两个页面、一次跳转，用不着。
 * 但在两件小事上没偷懒：
 *
 * 1. 切换用 M3 的 **shared axis X**（新页从侧向滑入 + 淡入，旧页反向滑出），
 *    前进和返回方向相反，不是硬切；
 * 2. 用 [rememberSaveableStateHolder] 把每一页的 `rememberSaveable` 状态存下来 ——
 *    否则从记录页返回时，摇骰子页会被重建，本轮进度、点数、填写的文字都丢了。
 */
@Composable
fun RandomDiceApp() {
    val context = LocalContext.current
    val store = remember(context) { DecisionStore.from(context) }
    val settingsStore = remember(context) { SettingsStore.from(context) }
    var records by remember(store) { mutableStateOf(store.load()) }
    // 设置也搁在 MutableState 里，更新一律读 .value 再写回：摇一轮会连着写十几次，
    // 回调里若捕获的是某一帧的旧值，改过的规则会被覆盖回去。
    val settings = remember(settingsStore) { mutableStateOf(settingsStore.load()) }
    var showDecisions by rememberSaveable { mutableStateOf(false) }
    val screenStates = rememberSaveableStateHolder()

    // 记录页开着时，系统返回键先回摇骰子页
    BackHandler(enabled = showDecisions) {
        showDecisions = false
    }

    AnimatedContent(
        targetState = showDecisions,
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            (
                slideInHorizontally(
                    animationSpec = tween(durationMillis = SCREEN_TRANSITION_MS),
                    initialOffsetX = { width -> direction * width / 4 },
                ) + fadeIn(tween(durationMillis = SCREEN_TRANSITION_MS))
                ) togetherWith (
                slideOutHorizontally(
                    animationSpec = tween(durationMillis = SCREEN_TRANSITION_MS),
                    targetOffsetX = { width -> -direction * width / 4 },
                ) + fadeOut(tween(durationMillis = SCREEN_TRANSITION_MS))
                )
        },
        label = "screen",
    ) { showRecords ->
        screenStates.SaveableStateProvider(if (showRecords) "decisions" else "dice") {
            if (showRecords) {
                DecisionsScreen(
                    records = records,
                    onDelete = { record ->
                        val updated = records - record
                        records = updated
                        store.save(updated)
                    },
                    onBack = { showDecisions = false },
                )
            } else {
                DiceScreen(
                    settings = settings.value,
                    onTotalRollsChange = { total ->
                        val updated = settings.value.copy(totalRolls = total)
                        settings.value = updated
                        settingsStore.save(updated)
                    },
                    onRuleChange = { rule ->
                        val updated = settings.value.copy(rule = rule)
                        settings.value = updated
                        settingsStore.save(updated)
                    },
                    onOpenDecisions = { showDecisions = true },
                    onSaveDecision = { record ->
                        val updated = listOf(record) + records
                        records = updated
                        store.save(updated)
                    },
                )
            }
        }
    }
}
