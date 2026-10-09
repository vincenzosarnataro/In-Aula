package it.aula.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.aula.Testi
import it.aula.model.Parlamentare
import it.aula.model.Ramo
import it.aula.presentation.ParlamentariState
import it.aula.presentation.ParlamentariStore

@Composable
fun ParlamentariScreen(
    store: ParlamentariStore,
    modifier: Modifier = Modifier,
    onParlamentare: (Parlamentare) -> Unit,
) {
    val stato by store.state.collectAsStateWithLifecycle()
    ParlamentariContenuto(
        stato = stato,
        azioni = AzioniParlamentari(
            selezionaRamo = store::selezionaRamo,
            ricarica = store::ricarica,
            cerca = store::cerca,
            mostraSoloCambi = store::mostraSoloCambi,
        ),
        modifier = modifier,
        onParlamentare = onParlamentare,
    )
}

class AzioniParlamentari(
    val selezionaRamo: (Ramo) -> Unit = {},
    val ricarica: () -> Unit = {},
    val cerca: (String) -> Unit = {},
    val mostraSoloCambi: (Boolean) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ParlamentariContenuto(
    stato: ParlamentariState,
    azioni: AzioniParlamentari,
    modifier: Modifier = Modifier,
    onParlamentare: (Parlamentare) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Column(modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        LargeFlexibleTopAppBar(
            title = { Text(Testi.parlamentari) },
            subtitle = if (stato.tutti.isNotEmpty()) {
                { Text(Testi.inCarica(stato.visibili.size)) }
            } else {
                null
            },
            actions = { PulsanteTema() },
            scrollBehavior = scrollBehavior,
        )
        SelettoreRamo(stato.ramo, azioni.selezionaRamo, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        CampoRicerca(
            valore = stato.ricerca,
            onCambia = azioni.cerca,
            segnaposto = Testi.cercaParlamentari,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (stato.conCambi > 0) {
            FilterChip(
                selected = stato.soloCambi,
                onClick = { azioni.mostraSoloCambi(!stato.soloCambi) },
                label = { Text(Testi.hannoCambiatoGruppo(stato.conCambi)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        when {
            stato.caricamento && stato.tutti.isEmpty() -> StatoCaricamento(Testi.attesaParlamentari(stato.ramo))
            stato.errore != null && stato.tutti.isEmpty() -> StatoErrore(stato.errore!!, azioni.ricarica)
            else -> {
                val visibili = stato.visibili
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    itemsIndexed(visibili, key = { _, p -> p.uri }) { i, p ->
                        SegmentedListItem(
                            onClick = { onParlamentare(p) },
                            shapes = ListItemDefaults.segmentedShapes(i, visibili.size),
                            colors = coloriLista(),
                            leadingContent = { Avatar(p.fotoUrl, p.iniziali) },
                            supportingContent = if (p.gruppo.isNotBlank() || p.cambiDiGruppo > 0) {
                                {
                                    Column {
                                        if (p.gruppo.isNotBlank()) Text(p.gruppo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (p.cambiDiGruppo > 0) {
                                            Text(
                                                Testi.cambiDiGruppo(p.cambiDiGruppo),
                                                color = MaterialTheme.colorScheme.tertiary,
                                            )
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                        ) {
                            Text(p.nomeCompleto)
                        }
                    }
                }
            }
        }
    }
}
