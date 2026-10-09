package it.aula.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.aula.Testi
import it.aula.model.Seduta
import it.aula.model.Votazione

/** Votazioni raggruppate per seduta, con l'intestazione della seduta che resta in alto. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.votazioniPerSeduta(
    sedute: List<Seduta>,
    mostraRamo: Boolean = false,
    onVotazione: (Votazione) -> Unit,
) {
    sedute.forEach { seduta ->
        stickyHeader(key = "h-${seduta.uri}") {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (mostraRamo) "${seduta.ramo.etichetta} · ${seduta.titolo}" else seduta.titolo,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
                )
            }
        }
        itemsIndexed(seduta.votazioni, key = { _, v -> v.uri }) { i, v ->
            RigaVotazione(v, i, seduta.votazioni.size) { onVotazione(v) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RigaVotazione(v: Votazione, indice: Int, totale: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(indice, totale),
        colors = coloriLista(),
        overlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                v.numero?.let { Text(Testi.numeroVotazione(it)) }
                ChipEsito(v.esito)
                v.etichette.forEach { Etichetta(it) }
            }
        },
        supportingContent = {
            Column {
                // Se in testa c'è il provvedimento, qui si dice cosa se ne votava.
                if (v.atto != null) Text(v.oggetto, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (v.haConteggi) {
                    Spacer(Modifier.height(10.dp))
                    BarraVoti(v.favorevoliN, v.contrariN, v.astenutiN, altezza = 8.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        Testi.conteggioVoti(v.favorevoliN, v.contrariN, v.astenutiN),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
    ) {
        Text(
            v.intestazione,
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
