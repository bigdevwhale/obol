package app.obol.watch

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A Compose window drawn by the accessibility service over other apps. Accessibility overlays
 * need no "draw over apps" permission.
 */
class OverlayHost(
    private val context: Context,
    /** Takes touches and covers the whole screen (the gate), or a small see-through badge. */
    private val blocking: Boolean,
) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private var view: ComposeView? = null

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    val showing: Boolean get() = view != null

    fun show(content: @Composable () -> Unit) {
        view?.let {
            it.setContent(content)
            return
        }
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@OverlayHost)
            setViewTreeSavedStateRegistryOwner(this@OverlayHost)
            setViewTreeViewModelStoreOwner(this@OverlayHost)
            setContent(content)
        }
        val flags = if (blocking) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        val size = if (blocking) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
        val lp = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = if (blocking) Gravity.CENTER else Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        runCatching { wm.addView(v, lp) }.onSuccess {
            view = v
            registry.currentState = Lifecycle.State.RESUMED
        }
    }

    fun hide() {
        val v = view ?: return
        registry.currentState = Lifecycle.State.CREATED
        runCatching { wm.removeView(v) }
        view = null
    }

    fun destroy() {
        hide()
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
