package it.aula.android.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.aula.Testi
import it.aula.presentation.LegislaturaStore

/** Legislatura scelta dall'utente, salvata nelle SharedPreferences; assente = quella in corso. */
class PreferenzaLegislatura(context: Context) {
    private val prefs = context.getSharedPreferences("preferenze", Context.MODE_PRIVATE)

    val salvata: Int? get() = prefs.getInt(CHIAVE, 0).takeIf { it > 0 }

    fun salva(numero: Int?) {
        prefs.edit().apply { if (numero == null) remove(CHIAVE) else putInt(CHIAVE, numero) }.apply()
    }

    private companion object {
        const val CHIAVE = "legislatura"
    }
}

class SceltaLegislatura(val store: LegislaturaStore, val preferenza: PreferenzaLegislatura)

/** Null nelle preview: il pulsante non compare. */
val LocalSceltaLegislatura = staticCompositionLocalOf<SceltaLegislatura?> { null }

/** Azione della top bar: mostra la legislatura in numeri romani, il menu permette di cambiarla. */
@Composable
fun PulsanteLegislatura() {
    val scelta = LocalSceltaLegislatura.current ?: return
    val stato by scelta.store.state.collectAsStateWithLifecycle()
    val attiva = stato.attiva ?: return
    var aperto by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { aperto = true },
            modifier = Modifier.semantics { contentDescription = Testi.legislaturaAttuale(attiva.romano) },
        ) {
            Text(attiva.romano)
        }
        DropdownMenu(expanded = aperto, onDismissRequest = { aperto = false }) {
            stato.disponibili.forEach { l ->
                DropdownMenuItem(
                    text = { Text(listOfNotNull(l.etichetta, l.periodo).joinToString(" · ")) },
                    trailingIcon = if (l.numero == attiva.numero) {
                        { Icon(Icons.Outlined.Check, contentDescription = Testi.selezionato) }
                    } else {
                        null
                    },
                    onClick = {
                        scelta.preferenza.salva(scelta.store.scegli(l.numero))
                        aperto = false
                    },
                )
            }
        }
    }
}
