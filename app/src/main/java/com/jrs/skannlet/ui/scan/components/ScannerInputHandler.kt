package com.jrs.skannlet.ui.scan.components

import android.view.KeyEvent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction

@Composable
fun ScannerInputHandler(
    enabled: Boolean,
    onScan: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    fun submit(value: String) {
        val trimmed = value.trim()
        input = ""
        if (trimmed.isNotEmpty()) {
            onScan(trimmed)
        }
    }

    LaunchedEffect(enabled) {
        if (enabled) {
            focusRequester.requestFocus()
        } else {
            input = ""
        }
    }

    OutlinedTextField(
        value = input,
        onValueChange = { value ->
            if (!enabled) return@OutlinedTextField
            val endIndex = value.indexOfFirst { it == '\n' || it == '\r' }
            if (endIndex >= 0) {
                submit(value.substring(0, endIndex))
            } else {
                input = value
            }
        },
        enabled = enabled,
        singleLine = true,
        label = { Text("Scannerinput") },
        supportingText = { Text("Zebra-input avsluttes med Enter.") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit(input) }),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val keyCode = event.nativeKeyEvent.keyCode
                if (
                    keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                ) {
                    submit(input)
                    true
                } else {
                    false
                }
            },
    )
}
