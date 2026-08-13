package com.sillytavern.eink.ui

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun AppCompatActivity.applySystemBars(
    root: View,
    onImeVisibilityChanged: (Boolean) -> Unit = {},
) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val original = Insets.of(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
    var lastImeVisible: Boolean? = null
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        view.setPadding(original.left + bars.left, original.top + bars.top, original.right + bars.right, original.bottom + bars.bottom)
        val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
        if (lastImeVisible != imeVisible) {
            lastImeVisible = imeVisible
            onImeVisibilityChanged(imeVisible)
        }
        insets
    }
    ViewCompat.requestApplyInsets(root)
}

/** Uses the modern insets controller so fullscreen also works on gesture-navigation devices. */
fun AppCompatActivity.setSystemBarsVisible(visible: Boolean) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            show(WindowInsetsCompat.Type.systemBars())
        } else {
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
