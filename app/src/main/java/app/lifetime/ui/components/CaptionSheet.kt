package app.lifetime.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.lifetime.data.Captions
import kotlinx.coroutines.launch

/**
 * Write or edit the words that go with a moment. [onSave] returns whether the caption was
 * stored; the sheet only closes when it was, so a failed save keeps the draft.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionSheet(
    initial: String?,
    onDismiss: () -> Unit,
    onSave: suspend (String) -> Boolean,
) {
    var text by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().imePadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            Text(if (initial == null) "Add a caption" else "Edit caption", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(Captions.MAX_LENGTH) },
                placeholder = { Text("What happened?") },
                minLines = 3,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                supportingText = {
                    if (text.length > Captions.MAX_LENGTH - 200) Text("${text.length} / ${Captions.MAX_LENGTH}")
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            val saved = onSave(text)
                            saving = false
                            if (saved) onDismiss()
                        }
                    },
                ) { Text("Save") }
            }
        }
        // Open with the keyboard up, like a new post. Not worth crashing over if focus isn't ready.
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}
