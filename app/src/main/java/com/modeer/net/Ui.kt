package com.modeer.net

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
import java.util.Locale

/** رقم عشري بأرقام عربية، مثلاً ٧٥٫٠ */
fun fmt(v: Double): String = String.format(Locale("ar", "EG"), "%.1f", v)

/** رقم صحيح بأرقام عربية. */
fun fmtI(n: Int): String = String.format(Locale("ar", "EG"), "%d", n)

/** يحوّل الأرقام العربية (٣٠٠) والفاصلة العربية لأرقام إنجليزي عشان نقدر نقراها. */
fun latin(s: String): String = s.trim().map {
    when (it) {
        in '٠'..'٩' -> '0' + (it - '٠')
        '٫', '،', ',' -> '.'
        else -> it
    }
}.joinToString("")

fun shareCode(ctx: Context, code: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "انضم لمجموعة النت بتاعتنا في تطبيق مدير النت بالكود: $code")
    }
    ctx.startActivity(Intent.createChooser(send, null))
}

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
fun AppCard(modifier: Modifier = Modifier, delayMs: Int = 0, content: @Composable ColumnScope.() -> Unit) {
    // الكارت بيطلع من تحت بنعومة أول ما يظهر.
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = a.value
                translationY = (1f - a.value) * 48f
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** شريط الاستهلاك: بيتملي بحركة، وبيغيّر لونه: أخضر، أصفر عند 80%، برتقالي عند 100%. */
@Composable
fun UsageBar(frac: Float, height: Dp = 12.dp) {
    val cs = MaterialTheme.colorScheme
    val target = when {
        frac >= 1f -> cs.error
        frac >= 0.8f -> cs.tertiary
        else -> cs.primary
    }
    val color by animateColorAsState(target, tween(600), label = "barColor")
    var shown by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(frac) { shown = frac }
    val f by animateFloatAsState(shown, tween(1000, easing = FastOutSlowInEasing), label = "bar")
    val round = RoundedCornerShape(99.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(round)
            .background(cs.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(f.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(round)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))),
        )
    }
}

/** رقم كبير بيعدّ لحد قيمته. */
@Composable
fun AnimatedNumber(value: Double, fontSize: TextUnit, color: Color) {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(value) { target = value.toFloat() }
    val v by animateFloatAsState(target, tween(1000, easing = FastOutSlowInEasing), label = "num")
    Text(fmt(v.toDouble()), fontSize = fontSize, fontWeight = FontWeight.Bold, color = color)
}

class Press(val src: MutableInteractionSource, val scale: State<Float>)

@Composable
fun rememberPress(): Press {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc = animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "press")
    return Press(src, sc)
}

fun Modifier.press(p: Press): Modifier = graphicsLayer {
    scaleX = p.scale.value
    scaleY = p.scale.value
}

/** بانر أخضر في أول التطبيق، فيه دايرة بتنبض. */
@Composable
fun Hero(title: String, sub: String) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(
        1f, 1.3f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "s",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0B6E66), Color(0xFF17A194)))),
    ) {
        Box(
            Modifier
                .size(170.dp)
                .align(Alignment.TopStart)
                .offset(x = (-40).dp, y = (-40).dp)
                .graphicsLayer { scaleX = s; scaleY = s }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.09f)),
        )
        Box(
            Modifier
                .size(90.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 20.dp, y = 20.dp)
                .graphicsLayer { scaleX = 2.3f - s; scaleY = 2.3f - s }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White))
                Text("مدير النت", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
            Text(sub, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
        }
    }
}

@Composable
fun Notice(text: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = fg, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
    }
}

@Composable
fun LevelPill(level: Int) {
    val c = MaterialTheme.colorScheme
    val bg = when (level) {
        100 -> c.errorContainer
        80 -> c.tertiaryContainer
        else -> c.primaryContainer
    }
    val fg = when (level) {
        100 -> c.onErrorContainer
        80 -> c.onTertiaryContainer
        else -> c.onPrimaryContainer
    }
    Surface(color = bg, shape = RoundedCornerShape(99.dp)) {
        Text(
            "وصل ${fmtI(level)}%",
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val p = rememberPress()
    Box(
        modifier
            .press(p)
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) c.primary else c.background)
            .border(1.dp, if (selected) c.primary else c.outline, shape)
            .clickable(interactionSource = p.src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) c.onPrimary else c.onSurface,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = onMinus,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(44.dp),
        ) { Text("−", fontSize = 20.sp) }
        Text(
            value,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 76.dp),
        )
        OutlinedButton(
            onClick = onPlus,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(44.dp),
        ) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text("مدير النت", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
