package hk.coffee.atlas.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF173F35)
val Paper = Color(0xFFF7F5EF)
val Muted = Color(0xFF68716A)
val Peach = Color(0xFFEBC5A5)
val Line = Color(0xFFE3E5DC)
private val palette = lightColorScheme(primary = Ink, onPrimary = Color.White, secondary = Ink,
    background = Paper, surface = Paper, onSurface = Ink, onBackground = Ink,
    surfaceVariant = Color(0xFFEEEFE7), onSurfaceVariant = Muted, outline = Color(0xFFA8B1A7),
    primaryContainer = Color(0xFFDDE8DD), onPrimaryContainer = Ink)
@Composable fun CoffeeTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = palette, content = content) }
