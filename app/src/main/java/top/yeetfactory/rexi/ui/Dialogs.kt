package top.yeetfactory.rexi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
private fun DialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.ink,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(text, style = Type.body, color = if (enabled) color else LocalPalette.current.faint)
    }
}

/** Picks a time of day with Material's clock, which follows the phone's 12 or 24 hour setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(
    title: String,
    minutes: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    validate: (Int) -> String? = { null },
) {
    val palette = LocalPalette.current
    val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = rememberUse24h())
    val picked = state.hour * 60 + state.minute
    val error = validate(picked)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text(title, style = Type.heading, color = palette.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TimePicker(
                    state = state,
                    colors =
                        TimePickerDefaults.colors(
                            clockDialColor = palette.well,
                            selectorColor = palette.primary,
                            periodSelectorSelectedContainerColor = palette.primary,
                            periodSelectorSelectedContentColor = palette.onPrimary,
                            timeSelectorSelectedContainerColor = palette.primary,
                            timeSelectorSelectedContentColor = palette.onPrimary,
                            timeSelectorUnselectedContainerColor = palette.well,
                            timeSelectorUnselectedContentColor = palette.ink,
                        ),
                )
                if (error != null) Text(error, style = Type.small, color = palette.alert)
            }
        },
        confirmButton = { DialogButton("Done", { onPick(picked) }, enabled = error == null) },
        dismissButton = { DialogButton("Cancel", onDismiss, color = palette.muted) },
    )
}

/** One choice from a short list, such as a snooze length. */
@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text(title, style = Type.heading, color = palette.ink) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    val on = value == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(selected = on, role = Role.RadioButton) { onPick(value) }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = Type.body, color = palette.ink, modifier = Modifier.weight(1f))
                        if (on) Icon(RexiIcons.Check, contentDescription = null, tint = palette.ink, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        confirmButton = { DialogButton("Cancel", onDismiss, color = palette.muted) },
    )
}

/** Asks for a number, such as how many tablets are in a new pack. */
@Composable
internal fun NumberDialog(
    title: String,
    initial: Double,
    unit: String,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    var text by rememberSaveable {
        mutableStateOf(
            if (initial > 0) {
                top.yeetfactory.rexi.core.Format
                    .number(initial)
            } else {
                ""
            },
        )
    }
    val value = text.replace(',', '.').replace("½", ".5").toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text(title, style = Type.heading, color = palette.ink) },
        text = {
            Column {
                Field(
                    label =
                        unit.replaceFirstChar {
                            it.uppercaseChar()
                        },
                    value = text,
                    onValueChange = { text = it },
                    keyboardType = KeyboardType.Decimal,
                    background = palette.well,
                )
            }
        },
        confirmButton = {
            DialogButton("Save", {
                if (value != null &&
                    value >= 0
                ) {
                    onSave(value)
                }
            }, color = if (value != null) palette.ink else palette.faint)
        },
        dismissButton = { DialogButton("Cancel", onDismiss, color = palette.muted) },
    )
}

/** Free text, such as notes on a medicine. */
@Composable
internal fun TextDialog(
    title: String,
    initial: String,
    placeholder: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text(title, style = Type.heading, color = palette.ink) },
        text = {
            Field(
                label = "",
                value = text,
                onValueChange = { text = it },
                placeholder = placeholder,
                singleLine = false,
                textStyle = Type.text,
                background = palette.well,
            )
        },
        confirmButton = { DialogButton("Save", { onSave(text) }) },
        dismissButton = { DialogButton("Cancel", onDismiss, color = palette.muted) },
    )
}

/** A yes or no question. [danger] colours the confirming button red. */
@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text(title, style = Type.heading, color = palette.ink) },
        text = { Text(text, style = Type.text, color = palette.muted) },
        confirmButton = { DialogButton(confirm, onConfirm, color = if (danger) palette.alert else palette.ink) },
        dismissButton = { DialogButton("Cancel", onDismiss, color = palette.muted) },
    )
}

/** A message with one button, for things that need explaining. */
@Composable
internal fun MessageDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text(title, style = Type.heading, color = palette.ink) },
        text = { Text(text, style = Type.text, color = palette.muted) },
        confirmButton = { DialogButton("OK", onDismiss) },
    )
}

/** Space between stacked sections of a screen. */
internal val SectionGap = 22.dp

/** A spacer of [SectionGap], for between sections. */
@Composable
internal fun SectionSpacer() {
    Spacer(Modifier.height(SectionGap))
}

/** Lays out [content] as a row of equal parts with a gap, for button rows. */
internal val ButtonGap = Arrangement.spacedBy(8.dp)
