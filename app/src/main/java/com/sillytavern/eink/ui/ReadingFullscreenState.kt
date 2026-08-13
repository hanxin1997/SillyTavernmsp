package com.sillytavern.eink.ui

/** Pure UI policy kept separate so fullscreen escape behavior can be unit tested. */
internal data class ReadingFullscreenState(
    val enabled: Boolean = false,
    val imeVisible: Boolean = false,
) {
    val browserBarVisible: Boolean
        get() = !enabled

    val restoreControlVisible: Boolean
        get() = enabled && !imeVisible
}
