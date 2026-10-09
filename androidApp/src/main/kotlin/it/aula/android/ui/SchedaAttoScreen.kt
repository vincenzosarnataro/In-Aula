package it.aula.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.aula.Testi
import it.aula.model.Esito
import it.aula.model.FaseIter
import it.aula.model.SchedaAtto
import it.aula.model.Votazione
import it.aula.presentation.SchedaAttoState
import it.aula.presentation.SchedaAttoStore

/** Un provvedimento: iter nei due rami, chi l'ha presentato, temi e tutte le votazioni d'Aula. */
@Composable
fun SchedaAttoScreen(
    store: SchedaAttoStore,
    onIndietro: () -> Unit,
    onVotazione: (Votazione) -> Unit,
) {
    val stato by store.state.collectAsStateWithLifecycle()
    SchedaAttoContenuto(stato, onIndietro = onIndietro, onVotazione = onVotazione, onRiprova = store::ricarica)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SchedaAttoContenuto(
    stato: SchedaAttoState,
    onIndietro: () -> Unit = {},
    onVotazione: (Votazione) -> Unit = {},
    onRiprova: () -> Unit = {},
) {
    val atto = stato.scheda?.atto ?: stato.atto
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(atto.numero) },
                subtitle = { Text(atto.natura ?: stato.scheda?.tipo ?: Testi.provvedimento) },
                navigationIcon = {
                    IconButton(onClick = onIndietro) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = Testi.indietro)
                    }
                },
                actions = {
                    stato.scheda?.sito?.let { sito ->
                        IconButton(onClick = { runCatching { uriHandler.openUri(sito) } }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = Testi.apriSulSito)
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
                    Text(atto.titoloBreve ?: atto.titolo, style = MaterialTheme.typography.headlineSmallEmphasized)
                    if (atto.titoloBreve != null && atto.titolo != atto.titoloBreve) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            atto.titolo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val scheda = stato.scheda
            when {
                stato.caricamento && scheda == null -> item {
                    StatoCaricamento(stato.messaggioAttesa, Modifier.height(260.dp))
                }
                stato.errore != null && scheda == null -> item {
                    StatoErrore(stato.errore!!, onRiprova, Modifier.height(280.dp))
                }
                scheda != null -> {
                    if (scheda.iter.isNotEmpty()) {
                        item { Iter(scheda.iter, attuale = atto.numero, Modifier.padding(top = 8.dp)) }
                    }
                    item { Dettagli(scheda, Modifier.padding(top = 12.dp)) }
                    item {
                        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 4.dp)) {
                            TitoloSezione(Testi.votazioniInAula(scheda.votazioni.size))
                            val sulFilo = scheda.votazioni.count { it.sulFilo }
                            val respinte = scheda.votazioni.count { it.esito == Esito.RESPINTA }
                            if (scheda.votazioni.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    Testi.respinteSulFilo(respinte, sulFilo),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            scheda.nota?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    votazioniPerSeduta(stato.sedute, mostraRamo = true, onVotazione = onVotazione)
                }
            }
        }
    }
}

/** Le letture del provvedimento, in ordine: un pallino per fase, pieno per quella corrente. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Iter(iter: List<FaseIter>, attuale: String, modifier: Modifier = Modifier) {
    Sezione(modifier, titolo = Testi.iter) {
        iter.forEachIndexed { i, fase ->
            val corrente = fase.numero.replace(" ", "") == attuale.replace(" ", "")
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(
                            if (corrente) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    if (i < iter.lastIndex) {
                        Box(Modifier.width(2.dp).height(36.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(
                        "${fase.numero} · ${fase.ramo.etichetta}",
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = if (corrente) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        listOfNotNull(fase.statoEsteso, fase.dataEstesa).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Dettagli(scheda: SchedaAtto, modifier: Modifier = Modifier) {
    var tuttiIFirmatari by rememberSaveable { mutableStateOf(false) }
    Sezione(modifier, titolo = Testi.dettagli) {
        Voce(Testi.iniziativa, scheda.iniziativa)
        Voce(Testi.presentatoIl, scheda.presentatoIlEsteso)
        Voce(Testi.primoFirmatario, scheda.primoFirmatario)
        if (scheda.altriFirmatari.isNotEmpty()) {
            val mostrati = if (tuttiIFirmatari) scheda.altriFirmatari else scheda.altriFirmatari.take(MAX_FIRMATARI)
            val resto = scheda.altriFirmatari.size - mostrati.size
            Voce(Testi.altriFirmatari(scheda.altriFirmatari.size), mostrati.joinToString(", ") + if (resto > 0) "…" else "")
            if (resto > 0) {
                TextButton(onClick = { tuttiIFirmatari = true }) { Text(Testi.mostraTutti) }
            }
        }
        if (scheda.relatori.isNotEmpty()) {
            Voce(
                Testi.relatori,
                scheda.relatori.joinToString("\n") { r ->
                    r.nome + listOfNotNull(r.organo.takeIf { it.isNotBlank() }, r.tipo.takeIf { it.isNotBlank() && it != "relatore" })
                        .joinToString(", ").let { if (it.isBlank()) "" else " ($it)" }
                },
            )
        }
        if (scheda.temi.isNotEmpty()) {
            Text(
                Testi.temi,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                scheda.temi.forEach { Etichetta(it) }
            }
        }
    }
}

@Composable
private fun Voce(etichetta: String, valore: String?) {
    if (valore.isNullOrBlank()) return
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(etichetta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valore, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val MAX_FIRMATARI = 6
