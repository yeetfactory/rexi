package top.yeetfactory.rexi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import top.yeetfactory.rexi.core.DayPart

/** The filled button for the one main action on a screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 56.dp,
    container: Color = LocalPalette.current.primary,
    content: Color = LocalPalette.current.onPrimary,
) {
    val palette = LocalPalette.current
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(if (enabled) container else palette.line)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (enabled) content else palette.muted
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Type.body.copy(fontSize = Type.name.fontSize), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A quieter button with an outline. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    color: Color = LocalPalette.current.ink,
    border: Color = LocalPalette.current.outline,
) {
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .border(1.5.dp, border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.body, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A button on a soft fill, for secondary actions inside cards. */
@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    background: Color = LocalPalette.current.surface,
    color: Color = LocalPalette.current.ink,
) {
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.label.copy(fontSize = Type.small.fontSize), color = color, maxLines = 1)
    }
}

/** A text-only action, such as "I'll do it later". */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.ink,
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.body.copy(fontSize = Type.text.fontSize), color = color)
    }
}

/** A round button holding only an icon. [label] is what a screen reader says for it. */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color = LocalPalette.current.surface,
    tint: Color = LocalPalette.current.ink,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.42f))
    }
}

/** A choice chip. Selected chips fill with ink. */
@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unselected: Color = LocalPalette.current.surface,
    bordered: Boolean = false,
    height: Dp = 44.dp,
) {
    val palette = LocalPalette.current
    val background by animateColorAsState(if (selected) palette.primary else unselected, tween(160), label = "chip")
    val content by animateColorAsState(if (selected) palette.onPrimary else palette.ink, tween(160), label = "chipText")
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(background)
            .then(if (bordered && !selected) Modifier.border(1.5.dp, palette.outline, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Type.label.copy(fontSize = Type.small.fontSize), color = content, maxLines = 1)
    }
}

/** Two to four options in one bar, such as System, Light and Dark. */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    container: Color = LocalPalette.current.surface,
) {
    val palette = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .padding(5.dp)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val on = index == selected
            val background by animateColorAsState(if (on) palette.primary else Color.Transparent, tween(200), label = "segment")
            val content by animateColorAsState(if (on) palette.onPrimary else palette.muted, tween(200), label = "segmentText")
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(background)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(option, style = Type.label.copy(fontSize = Type.text.fontSize), color = content, maxLines = 1)
            }
        }
    }
}

/** The app's switch, drawn to match the designs. */
@Composable
fun RexiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val track by animateColorAsState(if (checked) palette.primary else palette.line, tween(180), label = "track")
    // An off knob in the surface colour vanishes in dark mode, so there it takes the muted grey instead.
    val offKnob = if (palette.surface.luminance() < 0.5f) palette.muted else palette.surface
    val knob by animateColorAsState(if (checked) palette.onPrimary else offKnob, tween(180), label = "knob")
    val shift by animateDpAsState(if (checked) 20.dp else 0.dp, tween(180), label = "shift")
    Box(
        modifier
            .size(52.dp, 32.dp)
            .clip(CircleShape)
            .background(track)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(shift.roundToPx(), 0) }
                .size(26.dp)
                .clip(CircleShape)
                .background(knob),
        )
    }
}

/** A white card with large rounded corners, the main surface on every screen. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.surface,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = Type.label,
        color = LocalPalette.current.muted,
        modifier = modifier.padding(start = 8.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** A screen's large title with an optional action on the right. */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Type.display, color = LocalPalette.current.ink, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null) action()
    }
}

/** A back button, optionally labelled with where it goes, and an optional action on the right. */
@Composable
fun TopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
    action: @Composable (RowScope.() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    Row(
        modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (backLabel != null) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .heightIn(min = 44.dp)
                    .padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(RexiIcons.Back, contentDescription = null, tint = palette.ink, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(2.dp))
                Text(backLabel, style = Type.body, color = palette.ink)
            }
        } else {
            IconCircleButton(RexiIcons.Back, "Back", onBack)
        }
        Spacer(Modifier.weight(1f))
        if (action != null) action()
    }
}

/** Short bars for the steps of a flow. Bars, not dots. */
@Composable
fun StepBar(
    step: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Step $step of $total" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(24.dp, 6.dp)
                    .clip(CircleShape)
                    .background(if (index < step) palette.primary else palette.line),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text("$step of $total", style = Type.label, color = palette.muted)
    }
}

/** A small tag in a part of the day's colours, such as "Morning". */
@Composable
fun PartTag(
    part: DayPart,
    modifier: Modifier = Modifier,
    text: String = part.label,
) {
    val tint = LocalPalette.current.tint(part)
    Box(
        modifier
            .height(22.dp)
            .clip(CircleShape)
            .background(tint.background)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.micro.copy(fontWeight = Type.label.fontWeight), color = tint.label, maxLines = 1)
    }
}

/** A plain tag on the soft fill. */
@Composable
fun SoftTag(
    text: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Box(
        modifier
            .height(22.dp)
            .clip(CircleShape)
            .background(palette.well)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.micro.copy(fontWeight = Type.label.fontWeight), color = palette.muted, maxLines = 1)
    }
}

/** A warm note for something that needs attention, such as a medicine running low. */
@Composable
fun Banner(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: @Composable (() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.alertSoft)
            .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = palette.alert, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body.copy(fontSize = Type.text.fontSize, fontWeight = Type.name.fontWeight), color = palette.alertText)
            Spacer(Modifier.height(2.dp))
            Text(text, style = Type.small, color = palette.ink)
        }
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            action()
        }
    }
}

/** A text field in the app's style: a white rounded box with a label above it. */
@Composable
fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    singleLine: Boolean = true,
    textStyle: TextStyle = Type.body.copy(fontSize = Type.name.fontSize),
    background: Color = LocalPalette.current.surface,
) {
    val palette = LocalPalette.current
    Column(modifier) {
        if (label.isNotEmpty()) {
            Text(label, style = Type.label.copy(fontSize = Type.small.fontSize, fontWeight = Type.body.fontWeight), color = palette.muted)
            Spacer(Modifier.height(8.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = textStyle.copy(color = palette.ink),
            cursorBrush = SolidColor(palette.ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label.ifEmpty { placeholder } },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(background)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = textStyle.copy(fontWeight = Type.text.fontWeight), color = palette.faint)
                    }
                    inner()
                }
            },
        )
    }
}

/** A row in a settings card that opens something: a title, an optional line under it, and a value. */
@Composable
fun SettingRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
) {
    val palette = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle != null) 66.dp else 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body, color = palette.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Type.label.copy(fontWeight = Type.text.fontWeight), color = palette.muted)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = Type.text, color = palette.muted, textAlign = TextAlign.End)
        }
        Spacer(Modifier.width(6.dp))
        Icon(RexiIcons.Chevron, contentDescription = null, tint = palette.muted, modifier = Modifier.size(18.dp))
    }
}

/** A row with a switch. The whole row toggles, so it's an easy target. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val palette = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body, color = palette.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Type.label.copy(fontWeight = Type.text.fontWeight), color = palette.muted)
            }
        }
        Spacer(Modifier.width(14.dp))
        RexiSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A hairline between rows inside a card. */
@Composable
fun RowLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(1.dp)
            .background(LocalPalette.current.well),
    )
}

/** A plus and minus around a value, such as "1 tablet" or "08:00". */
@Composable
fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusLabel: String = "Less",
    plusLabel: String = "More",
    valueStyle: TextStyle = Type.body,
    buttonSize: Dp = 44.dp,
    buttonColor: Color = LocalPalette.current.well,
    tint: Color = LocalPalette.current.ink,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(RexiIcons.Minus, minusLabel, onMinus, size = buttonSize, background = buttonColor, tint = tint)
        Text(
            value,
            style = valueStyle,
            color = tint,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 10.dp).width(stepperValueWidth(valueStyle)),
        )
        IconCircleButton(RexiIcons.Plus, plusLabel, onPlus, size = buttonSize, background = buttonColor, tint = tint)
    }
}

/** Keeps a stepper from jumping about as its value changes width. */
private fun stepperValueWidth(style: TextStyle): Dp = if (style.fontSize.value > 30f) 140.dp else 92.dp
