package it.aula.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import it.aula.model.TipoVoto

// Blu istituzionale con accento bronzo: nessun ruolo cade sui verdi, rossi e gialli
// riservati ai voti. Schemi generati con material-color-utilities (spec 2021, tonal spot)
// a partire da #1F4F8F (primario/neutri) e #8A5A2B (terziario).
private val Chiaro = lightColorScheme(
    primary = Color(0xFF285EA7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF00468C),
    inversePrimary = Color(0xFFA9C7FF),
    secondary = Color(0xFF555F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E3F9),
    onSecondaryContainer = Color(0xFF3E4758),
    tertiary = Color(0xFF835424),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC0),
    onTertiaryContainer = Color(0xFF683D0E),
    background = Color(0xFFFAF9FD),
    onBackground = Color(0xFF1A1B1E),
    surface = Color(0xFFFAF9FD),
    onSurface = Color(0xFF1A1B1E),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceTint = Color(0xFF285EA7),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF9FD),
    surfaceContainer = Color(0xFFEFEDF1),
    surfaceContainerHigh = Color(0xFFE9E7EC),
    surfaceContainerHighest = Color(0xFFE3E2E6),
    surfaceContainerLow = Color(0xFFF4F3F7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDAD9DD),
)
private val Scuro = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468C),
    onPrimaryContainer = Color(0xFFD6E3FF),
    inversePrimary = Color(0xFF285EA7),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4758),
    onSecondaryContainer = Color(0xFFD9E3F9),
    tertiary = Color(0xFFF9BA81),
    onTertiary = Color(0xFF4B2800),
    tertiaryContainer = Color(0xFF683D0E),
    onTertiaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6CF),
    surfaceTint = Color(0xFFA9C7FF),
    inverseSurface = Color(0xFFE3E2E6),
    inverseOnSurface = Color(0xFF2F3033),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF38393C),
    surfaceContainer = Color(0xFF1E2023),
    surfaceContainerHigh = Color(0xFF292A2D),
    surfaceContainerHighest = Color(0xFF343538),
    surfaceContainerLow = Color(0xFF1A1B1E),
    surfaceContainerLowest = Color(0xFF0D0E11),
    surfaceDim = Color(0xFF121316),
)

/** Sfondo del tema, per la finestra: Compose non è ancora disegnato quando serve. */
fun sfondoTema(scuro: Boolean): Color = if (scuro) Scuro.background else Chiaro.background

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AulaTheme(scuro: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (scuro) Scuro else Chiaro,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** Colori dei voti: coerenti in tutta l'app, mai usati da soli per comunicare il significato. */
object ColoriVoto {
    val favorevole = Color(0xFF2E8B57)
    val contrario = Color(0xFFC0392B)
    val astenuto = Color(0xFFD4A017)
    val altro = Color(0xFF9A9EA8)

    fun per(tipo: TipoVoto): Color = when (tipo) {
        TipoVoto.FAVOREVOLE -> favorevole
        TipoVoto.CONTRARIO -> contrario
        TipoVoto.ASTENUTO -> astenuto
        else -> altro
    }

    /** Nell'emiciclo chi non era in Aula resta visibile ma attenuato. */
    fun seggio(tipo: TipoVoto): Color = if (tipo.presente) per(tipo) else altro.copy(alpha = 0.3f)
}
