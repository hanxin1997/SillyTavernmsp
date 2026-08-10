package com.sillytavern.eink.ui

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

fun AppCompatActivity.applySystemBars(root: View) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val original = Insets.of(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        view.setPadding(original.left + bars.left, original.top + bars.top, original.right + bars.right, original.bottom + bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(root)
}
