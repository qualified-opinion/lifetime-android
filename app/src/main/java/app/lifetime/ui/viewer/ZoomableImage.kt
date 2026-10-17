package app.lifetime.ui.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * A photo that pinches, pans and double-taps to zoom.
 *
 * At 1x, one-finger drags are left unconsumed so the pager can swipe between moments;
 * once zoomed in, drags pan the photo instead.
 */
@Composable
fun ZoomableImage(
    file: File,
    isCurrentPage: Boolean,
    onTap: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    // Pointer-input coroutines survive recomposition. Always call the latest callbacks,
    // especially onTap, whose caller captures the current toolbar visibility.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnZoomedChange by rememberUpdatedState(onZoomedChange)

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun set(newScale: Float, newOffset: Offset) {
        val wasZoomed = scale > 1f
        scale = newScale
        offset = clamp(newOffset, newScale)
        if (wasZoomed != (scale > 1f)) currentOnZoomedChange(scale > 1f)
    }

    fun animateTo(targetScale: Float, targetOffset: Offset) {
        scope.launch {
            val anim = Animatable(scale)
            val startOffset = offset
            val startScale = scale
            anim.animateTo(targetScale) {
                val t = if (targetScale == startScale) 1f else (value - startScale) / (targetScale - startScale)
                set(value, startOffset + (targetOffset - startOffset) * t)
            }
        }
    }

    // Swiping away resets the zoom.
    LaunchedEffect(isCurrentPage) { if (!isCurrentPage && scale != 1f) set(1f, Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            animateTo(1f, Offset.Zero)
                        } else {
                            // Zoom so the tapped point stays under the finger.
                            val center = Offset(size.width / 2f, size.height / 2f)
                            animateTo(DOUBLE_TAP_SCALE, (center - tap) * (DOUBLE_TAP_SCALE - 1f))
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                            // Keep the content under the fingers in place while scaling.
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val newOffset = if (!centroid.isSpecified) {
                                offset + pan
                            } else {
                                val focus = centroid - center
                                (offset - focus) * (newScale / scale) + focus + pan
                            }
                            set(newScale, newOffset)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = file,
            contentDescription = "Photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
