package it.aula.android.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import it.aula.Testi

enum class SceltaTema(val etichetta: String, val icona: ImageVector) {
    SISTEMA(Testi.temaSistema, Icons.Outlined.BrightnessAuto),
    CHIARO(Testi.temaChiaro, Icons.Outlined.LightMode),
    SCURO(Testi.temaScuro, Icons.Outlined.DarkMode),
    ;

    fun scuro(sistemaScuro: Boolean): Boolean = when (this) {
        SISTEMA -> sistemaScuro
        CHIARO -> false
        SCURO -> true
    }
}

/** Tema scelto dall'utente, salvato nelle SharedPreferences e osservabile da Compose. */
class PreferenzaTema(context: Context) {
    private val prefs = context.getSharedPreferences("preferenze", Context.MODE_PRIVATE)

    var scelta by mutableStateOf(
        SceltaTema.entries.firstOrNull { it.name == prefs.getString(CHIAVE, null) } ?: SceltaTema.SISTEMA,
    )
        private set

    fun scegli(nuova: SceltaTema) {
        scelta = nuova
        prefs.edit().putString(CHIAVE, nuova.name).apply()
    }

    private companion object {
        const val CHIAVE = "tema"
    }
}

val LocalPreferenzaTema = staticCompositionLocalOf<PreferenzaTema> { error("PreferenzaTema non fornita") }

/** Azione della top bar: l'icona mostra il tema attuale, il menu permette di cambiarlo. */
@Composable
fun PulsanteTema() {
    val preferenza = LocalPreferenzaTema.current
    var aperto by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { aperto = true }) {
            Icon(preferenza.scelta.icona, contentDescription = Testi.temaAttuale(preferenza.scelta.etichetta))
        }
        DropdownMenu(expanded = aperto, onDismissRequest = { aperto = false }) {
            SceltaTema.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.etichetta) },
                    leadingIcon = { Icon(s.icona, contentDescription = null) },
                    trailingIcon = if (s == preferenza.scelta) {
                        { Icon(Icons.Outlined.Check, contentDescription = Testi.selezionato) }
                    } else {
                        null
                    },
                    onClick = {
                        preferenza.scegli(s)
                        aperto = false
                    },
                )
            }
        }
    }
}
