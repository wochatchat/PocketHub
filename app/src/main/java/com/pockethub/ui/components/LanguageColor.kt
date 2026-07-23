package com.pockethub.ui.components

import androidx.compose.ui.graphics.Color

/** Maps a spoken language name (lowercased) to GitHub's official dot color hex, including #. */
private val languageHexMap = mapOf(
    "kotlin" to "#A97BFF",
    "typescript" to "#3178C6",
    "python" to "#3572A5",
    "rust" to "#DEA584",
    "go" to "#00ADD8",
    "swift" to "#F05138",
    "java" to "#B07219",
    "c++" to "#F34B7D",
    "c" to "#555555",
    "c#" to "#178600",
    "javascript" to "#F1E05A",
    "html" to "#E34C26",
    "css" to "#563D7C",
    "php" to "#4F5D95",
    "ruby" to "#701516",
    "dart" to "#00B4AB",
    "shell" to "#89E051",
    "objective-c" to "#438EFF",
    "scala" to "#C22D40",
    "lua" to "#000080",
    "elixir" to "#6E4A7E",
    "haskell" to "#5E5086",
    "r" to "#198CE7",
    "jupyter notebook" to "#DA5B0B",
)

/** Returns the github-style color hex (with leading #) for [language], or null when unknown. */
fun languageColorHex(language: String?): String? = language?.let { languageHexMap[it.lowercase()] }

/** Color helper that turns the hex (#RRGGBB) into a fully opaque Compose Color, or null when the input is blank. */
fun parseColorHex(hex: String?): Color? = hex?.takeIf { it.isNotBlank() }?.let {
    val v = it.removePrefix("#").toLong(16)
    Color(v or 0xFF000000L)
}
