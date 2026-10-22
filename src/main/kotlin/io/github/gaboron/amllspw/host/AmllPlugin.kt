package io.github.gaboron.amllspw.host

import com.xuncorp.spw.workshop.api.*
import com.xuncorp.spw.workshop.api.hook.HookHandle
import io.github.gaboron.amllspw.interop.TtmlBridge
import io.github.gaboron.amllspw.interop.FooterBridge

@OptIn(UnstableSpwWorkshopApi::class)
class AmllPlugin(context: PluginContext) : SpwPlugin(context) {
    private var handle: HookHandle? = null
    private var active: AmllRuntime? = null
    private var bridge: TtmlBridge? = null
    private var footerBridge: FooterBridge? = null

    @OptIn(UnstableSpwWorkshopApi::class)
    override fun start() {
        if (active != null) return
        require(WorkshopApi.manager.isPermissionGranted(PluginPermission.CLASS_TRANSFORM) &&
            WorkshopApi.manager.isPermissionGranted(PluginPermission.LIBRARY_READ)) { "请授权修改宿主界面和读取曲库后启用 AMLL into SPW（MiSans）" }
        val target = NativeLyricsTarget.discover()
        val renderer = NativeLyricsTarget.renderer(target.owner)
            ?: error("无法定位宿主歌词行渲染组件，请更新插件以适配当前宿主版本")
        // Resolve the original entry point before registering anything. Without it the replacement
        // slot would draw nothing and the host subtree could not run, which is the state that made
        // the player page lose the measurement its background panel depends on. Refusing to start
        // leaves the native lyrics untouched instead of producing a broken page.
        val original = renderer.reflective()
            ?: error("无法反射定位宿主歌词组件 ${renderer.owner}#${renderer.method}，请更新插件以适配当前宿主版本")
        val created = AmllRuntime()
        try {
            // Keep the original renderer running (it owns the measurement the player page reads)
            // and simply make it invisible; our layer draws beside it in the same slot.
            handle = WorkshopApi.hookRegistrar.replaceComposable(
                renderer.owner, renderer.method, target.owner, renderer.parameters) { call ->
                HostLyricsPassthrough(call, renderer, original, created)
            }
            active = created
            current = created
            bridge = TtmlBridge(TtmlReceiver(created))
            footerBridge = FooterBridge(FooterReceiver(created))
            created.start()
        } catch (e: Throwable) {
            footerBridge?.close(); footerBridge = null
            bridge?.close(); bridge = null
            current = null; active = null
            handle?.close(); handle = null; created.close(); throw e
        }
    }

    override fun stop() {
        val old = active ?: return
        if (current === old) current = null
        footerBridge?.close(); footerBridge = null
        bridge?.close(); bridge = null
        handle?.close(); handle = null
        active = null; old.close()
    }
    override fun update() = stop()
    override fun delete() = stop()

    companion object {
        @Volatile internal var current: AmllRuntime? = null; private set
    }
}

/** PF4J constructs extensions independently; this bridge is active only for the plugin lifetime. */
class AmllPlaybackExtension : PlaybackExtensionPoint {
    override fun onBeforeLoadLyrics(mediaItem: PlaybackExtensionPoint.MediaItem): String? {
        AmllPlugin.current?.trackChanged(mediaItem)
        return null
    }
    override fun onLyricsLinesUpdated(lyricsLines: List<PlaybackExtensionPoint.LyricsLine>) { AmllPlugin.current?.lyricsChanged() }
    override fun onPositionUpdated(position: Long) { AmllPlugin.current?.positionChanged(position) }
    override fun onIsPlayingChanged(isPlaying: Boolean) { AmllPlugin.current?.playingChanged(isPlaying) }
    override fun onSeekTo(position: Long) { AmllPlugin.current?.positionChanged(position, seek = true) }
    override fun onStateChanged(state: PlaybackExtensionPoint.State) {
        if (state in setOf(PlaybackExtensionPoint.State.Idle, PlaybackExtensionPoint.State.Ended, PlaybackExtensionPoint.State.Buffering))
            AmllPlugin.current?.playingChanged(false)
    }
}
