package it.aula.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.aula.Testi
import it.aula.model.Ramo
import it.aula.model.Votazione
import it.aula.presentation.AulaState
import it.aula.presentation.AulaStore
import it.aula.presentation.FiltroVotazioni

@Composable
fun AulaScreen(store: AulaStore, modifier: Modifier = Modifier, onVotazione: (Votazione) -> Unit) {
    val stato by store.state.collectAsStateWithLifecycle()
    AulaContenuto(
        stato = stato,
        azioni = AzioniAula(
            selezionaRamo = store::selezionaRamo,
            ricarica = store::ricarica,
            caricaAltre = store::caricaAltre,
            cerca = store::cerca,
            filtra = store::filtra,
            scegliTema = store::scegliTema,
        ),
        modifier = modifier,
        onVotazione = onVotazione,
    )
}

/** Ciò che la schermata può chiedere allo store: separato perché le preview non ne hanno uno. */
class AzioniAula(
    val selezionaRamo: (Ramo) -> Unit = {},
    val ricarica: () -> Unit = {},
    val caricaAltre: () -> Unit = {},
    val cerca: (String) -> Unit = {},
    val filtra: (FiltroVotazioni) -> Unit = {},
    val scegliTema: (String?) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun AulaContenuto(
    stato: AulaState,
    azioni: AzioniAula,
    modifier: Modifier = Modifier,
    onVotazione: (Votazione) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Column(modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        LargeFlexibleTopAppBar(
            title = { Text(Testi.inAula) },
            subtitle = { Text(Testi.sottotitoloAula(stato.ramo)) },
            actions = { PulsanteTema() },
            scrollBehavior = scrollBehavior,
        )
        SelettoreRamo(
            stato.ramo,
            azioni.selezionaRamo,
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            stato.caricamento && stato.sedute.isEmpty() -> StatoCaricamento(Testi.attesaVotazioni(stato.ramo))
            stato.errore != null && stato.sedute.isEmpty() -> StatoErrore(stato.errore!!, azioni.ricarica)
            else -> {
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = stato.caricamento,
                    onRefresh = azioni.ricarica,
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullState,
                            isRefreshing = stato.caricamento,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    },
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        item(key = "filtri") { FiltriAula(stato, azioni) }
                        if (stato.filtriAttivi && stato.seduteVisibili.isEmpty()) {
                            item(key = "vuoto") {
                                Text(
                                    Testi.nessunaVotazione(stato.sedute.sumOf { it.votazioni.size }),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                                )
                            }
                        }
                        votazioniPerSeduta(stato.seduteVisibili, onVotazione = onVotazione)
                        if (stato.altreDisponibili && stato.sedute.isNotEmpty()) {
                            item(key = "altre") {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    when {
                                        stato.caricamentoAltre -> LoadingIndicator(Modifier.size(48.dp))
                                        // Con i filtri la lista può restare corta: niente caricamento a catena
                                        // (al Senato una pagina costa secondi), si chiede a chi legge.
                                        stato.filtriAttivi -> OutlinedButton(onClick = azioni.caricaAltre, shapes = ButtonDefaults.shapes()) {
                                            Text(Testi.votazioniPrecedenti)
                                        }
                                        // Arrivati in fondo, carichiamo la pagina successiva.
                                        else -> LaunchedEffect(stato.sedute.size) { azioni.caricaAltre() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Ricerca, filtri rapidi e (al Senato) temi. Scorrono via con la lista. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FiltriAula(stato: AulaState, azioni: AzioniAula) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        CampoRicerca(
            valore = stato.ricerca,
            onCambia = azioni.cerca,
            segnaposto = Testi.cercaVotazioni,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FiltroVotazioni.entries.drop(1)) { filtro ->
                FilterChip(
                    selected = stato.filtro == filtro,
                    onClick = { azioni.filtra(if (stato.filtro == filtro) FiltroVotazioni.TUTTE else filtro) },
                    label = { Text(filtro.etichetta) },
                )
            }
        }
        if (stato.temiDisponibili.isNotEmpty()) {
            Text(
                Testi.temi,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(stato.temiDisponibili) { tema ->
                    FilterChip(
                        selected = stato.tema == tema,
                        onClick = { azioni.scegliTema(tema) },
                        label = { Text(tema) },
                    )
                }
            }
        }
        if (stato.filtriAttivi && stato.votazioniVisibili > 0) {
            Text(
                Testi.votazioniFiltrate(stato.votazioniVisibili, stato.sedute.sumOf { it.votazioni.size }),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
    }
}
