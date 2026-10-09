package com.greenlight.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.greenlight.app.domain.Decision
import com.greenlight.app.ui.theme.InterestedColor
import com.greenlight.app.ui.theme.PassColor
import com.greenlight.app.ui.theme.StampStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Controls the top card so the PASS / INTERESTED buttons animate exactly like a swipe. */
@Stable
class SwipeStackState internal constructor(private val scope: CoroutineScope) {
    internal val offsetX = Animatable(0f)
    internal val offsetY = Animatable(0f)
    internal var widthPx by mutableStateOf(1f)
    internal var onSwiped: (Decision) -> Unit = {}
    internal var topKey: Any? = null
    /** The card that just flew off; hidden until the list drops it, so it never flashes back. */
    internal var swipedKey: Any? by mutableStateOf(null)
    var animating by mutableStateOf(false)
        private set

    /** -1 (full PASS) … 0 … 1 (full INTERESTED) */
    val progress: Float get() = (offsetX.value / (widthPx * 0.35f)).coerceIn(-1f, 1f)

    fun swipe(decision: Decision) {
        if (animating) return
        scope.launch { flyOut(decision) }
    }

    internal suspend fun flyOut(decision: Decision) {
        animating = true
        swipedKey = null
        val target = if (decision == Decision.INTERESTED) widthPx * 1.6f else -widthPx * 1.6f
        scope.launch { offsetY.animateTo(offsetY.value + 60f, tween(260)) }
        offsetX.animateTo(target, tween(260))
        swipedKey = topKey
        onSwiped(decision)
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
        animating = false
    }

    internal suspend fun settle() {
        scope.launch { offsetY.animateTo(0f, spring(Spring.DampingRatioMediumBouncy)) }
        offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
    }
}

@Composable
fun rememberSwipeStackState(): SwipeStackState {
    val scope = rememberCoroutineScope()
    return remember { SwipeStackState(scope) }
}

/**
 * Swipe-card deck: drag right = INTERESTED, left = PASS. The card tilts while dragging, shows a
 * stamp, flies off, and the next card rises from underneath. Uses graphicsLayer transforms only
 * (no relayout) to keep it smooth. TalkBack users get "Pass" / "Interested" custom actions.
 */
@Composable
fun <T> SwipeCardStack(
    items: List<T>,
    key: (T) -> Any,
    state: SwipeStackState,
    onSwiped: (T, Decision) -> Unit,
    /** A tap (not a drag) on the top card, e.g. to open its details. */
    onTap: ((T) -> Unit)? = null,
    modifier: Modifier = Modifier,
    card: @Composable (T) -> Unit,
) {
    val currentOnSwiped by rememberUpdatedState(onSwiped)
    val top = items.firstOrNull { key(it) != state.swipedKey }
    state.topKey = top?.let(key)
    state.onSwiped = { d -> top?.let { currentOnSwiped(it, d) } }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        state.widthPx = with(density) { maxWidth.toPx() }
        val visible = items.filter { key(it) != state.swipedKey }.take(3)
        visible.asReversed().forEachIndexed { reversedIndex, item ->
            val depth = visible.size - 1 - reversedIndex
            key(key(item)) {
                if (depth == 0) {
                    val p = state.progress
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = state.offsetX.value
                                translationY = state.offsetY.value
                                rotationZ = state.offsetX.value / state.widthPx * 18f
                            }
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction("Pass") { state.swipe(Decision.PASSED); true },
                                    CustomAccessibilityAction("Interested") { state.swipe(Decision.INTERESTED); true },
                                )
                            }
                            .pointerInput(key(item), onTap != null) {
                                if (onTap != null) detectTapGestures(onTap = { onTap(item) })
                            }
                            .pointerInput(key(item)) {
                                val tracker = VelocityTracker()
                                detectDragGestures(
                                    onDragStart = { tracker.resetTracking() },
                                    onDragEnd = {
                                        val vx = tracker.calculateVelocity().x
                                        val fling = abs(vx) > 1800f && abs(state.offsetX.value) > size.width * 0.1f
                                        val far = abs(state.offsetX.value) > size.width * 0.3f
                                        scope.launch {
                                            if (far || fling) {
                                                val right = if (fling) vx > 0 else state.offsetX.value > 0
                                                state.flyOut(if (right) Decision.INTERESTED else Decision.PASSED)
                                            } else state.settle()
                                        }
                                    },
                                    onDragCancel = { scope.launch { state.settle() } },
                                ) { change, drag ->
                                    change.consume()
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    scope.launch {
                                        state.offsetX.snapTo(state.offsetX.value + drag.x)
                                        state.offsetY.snapTo(state.offsetY.value + drag.y * 0.3f)
                                    }
                                }
                            },
                    ) {
                        card(item)
                        if (p > 0.05f) Stamp("INTERESTED", InterestedColor, p, Alignment.TopStart, -14f)
                        if (p < -0.05f) Stamp("PASS", PassColor, -p, Alignment.TopEnd, 14f)
                    }
                } else {
                    // Cards underneath grow toward full size as the top card moves away.
                    val lift = abs(state.progress)
                    val scale = 1f - 0.05f * depth + 0.05f * lift * (if (depth == 1) 1 else 0)
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationY = (14.dp.toPx() * depth) * (1 - lift * (if (depth == 1) 1 else 0))
                        },
                    ) { card(item) }
                }
            }
        }
    }
}

@Composable
private fun Stamp(text: String, color: Color, strength: Float, alignment: Alignment, angle: Float) {
    Box(Modifier.fillMaxSize().padding(28.dp)) {
        Text(
            text,
            style = StampStyle,
            color = color,
            modifier = Modifier
                .align(alignment)
                .graphicsLayer { alpha = strength.coerceIn(0f, 1f) }
                .rotate(angle)
                .border(4.dp, color, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
