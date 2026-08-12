package com.sillytavern.eink.eink

enum class EinkThemeMode {
    OFF,
    BALANCED,
    HIGH_CONTRAST,
    ;

    companion object {
        /** Resolves both the new enum setting and the legacy enabled Boolean. */
        fun resolve(storedMode: String?, legacyEnabled: Boolean?): EinkThemeMode {
            if (storedMode != null) {
                return entries.firstOrNull { it.name.equals(storedMode, ignoreCase = true) } ?: BALANCED
            }
            return when (legacyEnabled) {
                false -> OFF
                true, null -> BALANCED
            }
        }
    }
}
