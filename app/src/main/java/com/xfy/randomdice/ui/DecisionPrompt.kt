package com.xfy.randomdice.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 一轮摇完后出现在**底部**的一行：写下**这次要决定的事情**（题目），右边「忽略」+ 一个对勾按钮。
 * 它占的就是数量滑杆那一行，不遮挡别的东西。
 *
 * 事情归这里，**结果**归上面那行「本次决策」—— 判决、换项、自己写的结果都在那边。
 *
 * **不会自己消失** —— 想不想记、什么时候记都由你定，只有三种情况会收起来：
 * 点「忽略」、点对勾保存、或者又按了摇骰子的按钮。
 *
 * 文字状态由调用方持有（[value] / [onValueChange]）—— 因为「这条记录存什么」是上面那行
 * 一起决定的，两边得看同一份状态。
 */
@Composable
fun DecisionPrompt(
    value: String,
    onValueChange: (String) -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    onIgnore: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "要决定的是什么事",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(text = placeholder) },
            singleLine = true,
        )

        Spacer(modifier = Modifier.width(8.dp))

        TextButton(onClick = onIgnore) {
            Text(text = "忽略")
        }

        FilledIconButton(
            onClick = onSave,
            enabled = canSave,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "记录这次的决定",
            )
        }
    }
}
