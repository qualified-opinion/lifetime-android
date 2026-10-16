package app.lifetime

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LifetimeApp : Application(), SingletonImageLoader.Factory {

    /** Simple manual dependency container; swap for Hilt later if the app grows. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch(Dispatchers.IO) { container.repository.cleanUpIncoming() }
    }

    /** One shared Coil loader that can also pull preview frames out of video files. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
