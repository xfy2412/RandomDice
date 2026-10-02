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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 一轮摇完后出现在**底部**的一行「记录这次的决定」：
 * 左边填文字，右边「忽略」+ 一个对勾按钮。它占的就是数量滑杆那一行，不遮挡别的东西。
 *
 * **不会自己消失** —— 想不想记、什么时候记都由你定，只有三种情况会收起来：
 * 点「忽略」、点对勾保存、或者又按了摇骰子的按钮。
 */
@Composable
fun DecisionPrompt(
    onSave: (String) -> Unit,
    onIgnore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var decision by rememberSaveable { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = decision,
            onValueChange = { decision = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("这次决定了什么") },
            singleLine = true,
        )

        Spacer(modifier = Modifier.width(8.dp))

        TextButton(onClick = onIgnore) {
            Text("忽略")
        }

        FilledIconButton(
            onClick = { onSave(decision.trim()) },
            enabled = decision.isNotBlank(),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "记录这次的决定",
            )
        }
    }
}
