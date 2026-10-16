package app.lifetime.ui.viewer

import android.net.Uri
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * Media3 player for one video page. It plays only while its page is the settled one,
 * pauses when the app goes to the background, and is released when the page leaves.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    file: File,
    isActive: Boolean,
    onControlsVisibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onVisibility = rememberUpdatedState(onControlsVisibleChange)

    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    LaunchedEffect(isActive) {
        if (isActive) {
            player.play()
        } else {
            player.pause()
            player.seekTo(0)
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                controllerShowTimeoutMs = 2_500
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        onVisibility.value(visibility == View.VISIBLE)
                    },
                )
            }
        },
        update = { it.player = player },
        modifier = modifier.fillMaxSize().navigationBarsPadding(),
    )
}
