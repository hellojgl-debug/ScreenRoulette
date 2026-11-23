package com.hellojgl.screenroulette.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

private fun TextStyle.compact() = copy(
    fontSize = fontSize * 0.94f,
    lineHeight = lineHeight * 0.94f
)

val Typography = Typography().let { defaults ->
    defaults.copy(
        headlineMedium = defaults.headlineMedium.compact(),
        headlineSmall = defaults.headlineSmall.compact(),
        titleLarge = defaults.titleLarge.compact(),
        titleMedium = defaults.titleMedium.compact(),
        titleSmall = defaults.titleSmall.compact(),
        bodyLarge = defaults.bodyLarge.compact(),
        bodyMedium = defaults.bodyMedium.compact(),
        bodySmall = defaults.bodySmall.compact(),
        labelLarge = defaults.labelLarge.compact(),
        labelMedium = defaults.labelMedium.compact(),
        labelSmall = defaults.labelSmall.compact()
    )
}
