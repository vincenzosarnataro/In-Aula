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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.aula.Testi
import it.aula.model.AssenzeDecisive
import it.aula.model.Atto
import it.aula.model.Esito
import it.aula.model.Ramo
import it.aula.model.RipartizioneGruppo
import it.aula.model.TipoVoto
import it.aula.model.Votazione
import it.aula.model.VotoIndividuale
import it.aula.presentation.VistaEmiciclo
import it.aula.presentation.VotazioneState
import it.aula.presentation.VotazioneStore

@Composable
fun VotazioneScreen(
    store: VotazioneStore,
    onIndietro: () -> Unit,
    onParlamentare: (VotoIndividuale) -> Unit,
    onAtto: (Atto) -> Unit,
) {
    val stato by store.state.collectAsStateWithLifecycle()
    VotazioneContenuto(
        stato = stato,
        azioni = AzioniVotazione(
            ricarica = store::ricarica,
            filtra = store::filtra,
            filtraGruppo = store::filtraGruppo,
            mostraSoloRibelli = store::mostraSoloRibelli,
            cambiaVista = store::cambiaVista,
        ),
        onIndietro = onIndietro,
        onParlamentare = onParlamentare,
        onAtto = onAtto,
    )
}

class AzioniVotazione(
    val ricarica: () -> Unit = {},
    val filtra: (TipoVoto?) -> Unit = {},
    val filtraGruppo: (String?) -> Unit = {},
    val mostraSoloRibelli: (Boolean) -> Unit = {},
    val cambiaVista: (VistaEmiciclo) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VotazioneContenuto(
    stato: VotazioneState,
    azioni: AzioniVotazione,
    onIndietro: () -> Unit = {},
    onParlamentare: (VotoIndividuale) -> Unit = {},
    onAtto: (Atto) -> Unit = {},
) {
    val v = stato.votazione
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(Testi.titoloVotazione(v.numero)) },
                subtitle = { Text(Testi.sottotitoloVotazione(v)) },
                navigationIcon = {
                    IconButton(onClick = onIndietro) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = Testi.indietro)
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
            item { Intestazione(v, onAtto) }

            val dettaglio = stato.dettaglio
            when {
                stato.caricamento && dettaglio == null -> item {
                    StatoCaricamento(stato.messaggioAttesa, Modifier.height(240.dp))
                }
                stato.errore != null && dettaglio == null -> item {
                    StatoErrore(stato.errore!!, azioni.ricarica, Modifier.height(280.dp))
                }
                dettaglio != null -> {
                    stato.assenzeDecisive?.let { assenze ->
                        item { RiquadroAssenzeDecisive(assenze, v.ramo, Modifier.padding(top = 12.dp)) }
                    }
                    if (dettaglio.voti.isNotEmpty()) {
                        item { Distribuzione(stato, azioni, Modifier.padding(top = 12.dp)) }
                    }
                    dettaglio.nota?.let { nota ->
                        item {
                            Text(
                                nota,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (dettaglio.ripartizione.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 8.dp)) {
                                TitoloSezione(Testi.comeHannoVotatoIGruppi)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    Testi.spiegazioneCompattezza,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        itemsIndexed(dettaglio.ripartizione, key = { _, g -> "g-${g.gruppo}" }) { i, g ->
                            RigaGruppo(
                                g,
                                indice = i,
                                totale = dettaglio.ripartizione.size,
                                selezionato = stato.filtroGruppo == g.gruppo,
                            ) { azioni.filtraGruppo(g.gruppo) }
                        }
                    }
                    item {
                        Column(Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                            TitoloSezione(
                                Testi.comeHannoVotato(stato.votiFiltrati.size),
                                Modifier.padding(horizontal = 4.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            FiltriVoti(stato, azioni)
                        }
                    }
                    val voti = stato.votiFiltrati
                    itemsIndexed(voti, key = { _, voto -> "v-${voto.parlamentareUri}" }) { i, voto ->
                        RigaVoto(voto, i, voti.size, ribelle = voto.parlamentareUri in stato.ribelli) {
                            onParlamentare(voto)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Intestazione(v: Votazione, onAtto: (Atto) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(v.oggetto, style = MaterialTheme.typography.headlineSmallEmphasized)
        v.atto?.let {
            Spacer(Modifier.height(12.dp))
            SchedaAtto(it, onClick = { onAtto(it) })
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipEsito(v.esito)
            v.etichette.forEach { Etichetta(it) }
        }
        if (v.haConteggi) {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                v.presenti?.let { Numero("$it", Testi.presenti, Modifier.weight(1f)) }
                Numero("${v.favorevoliN}", Testi.favorevoli, Modifier.weight(1f), ColoriVoto.favorevole)
                Numero("${v.contrariN}", Testi.contrari, Modifier.weight(1f), ColoriVoto.contrario)
                Numero("${v.astenutiN}", Testi.astenuti, Modifier.weight(1f), ColoriVoto.astenuto)
            }
            Spacer(Modifier.height(12.dp))
            BarraVoti(v.favorevoliN, v.contrariN, v.astenutiN, altezza = 12.dp)
        }
    }
}

/** Il provvedimento su cui si vota, con l'avviso se l'abbiamo dedotto noi. Apre la scheda dell'atto. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SchedaAtto(atto: Atto, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                listOfNotNull(atto.numero, atto.natura).joinToString(" · "),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(atto.titoloBreve ?: atto.titolo, style = MaterialTheme.typography.titleMediumEmphasized)
            if (atto.titoloBreve != null && atto.natura == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    atto.titolo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            atto.proponenti?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    Testi.presentatoDa(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (atto.dedotto) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        Testi.attoDedotto,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                Testi.apriAtto,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Le assenze tra i gruppi schierati con chi ha perso superano i voti che mancavano: se quei
 * parlamentari fossero stati in Aula, votando col proprio gruppo, l'esito si sarebbe ribaltato.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RiquadroAssenzeDecisive(assenze: AssenzeDecisive, ramo: Ramo, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(Testi.assenzeDecisive, style = MaterialTheme.typography.titleMediumEmphasized)
            Spacer(Modifier.height(4.dp))
            Text(
                Testi.spiegazioneAssenze(assenze, ramo),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                Testi.assentiPerGruppo(assenze),
                style = MaterialTheme.typography.labelLarge,
            )
            if (assenze.decisiviDaSoli.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    Testi.decisiviDaSoli(assenze.decisiviDaSoli),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Numero(valore: String, etichetta: String, modifier: Modifier, colore: Color? = null) {
    Column(modifier) {
        Text(
            valore,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = colore ?: MaterialTheme.colorScheme.onSurface,
        )
        Text(etichetta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Distribuzione(stato: VotazioneState, azioni: AzioniVotazione, modifier: Modifier = Modifier) {
    val v = stato.votazione
    Sezione(modifier, titolo = Testi.distribuzioneVoto) {
        GruppoPulsanti(
            opzioni = VistaEmiciclo.entries,
            selezionata = stato.vista,
            etichetta = { it.etichetta },
            onSeleziona = azioni.cambiaVista,
        )
        Spacer(Modifier.height(20.dp))
        EmicicloVoti(
            seggi = stato.seggi,
            disposizione = stato.disposizione,
            descrizione = Testi.descrizioneEmiciclo(stato.conteggi),
            gruppoEvidenziato = stato.filtroGruppo,
        ) {
            Column(
                Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (v.esito != Esito.SCONOSCIUTO) {
                    Text(
                        v.esito.etichetta,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = if (v.esito == Esito.APPROVATA) ColoriVoto.favorevole else ColoriVoto.contrario,
                    )
                }
                v.scartoLabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        stato.filtroGruppo?.let { gruppo ->
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Etichetta(Testi.inEvidenza(gruppo))
            }
        }
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            stato.conteggi.forEach { VoceLegenda(ColoriVoto.seggio(it.tipo), it.tipo.etichetta, "${it.numero}") }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RigaGruppo(
    g: RipartizioneGruppo,
    indice: Int,
    totale: Int,
    selezionato: Boolean,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        selected = selezionato,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(indice, totale),
        colors = coloriLista(),
        supportingContent = {
            Column {
                Spacer(Modifier.height(8.dp))
                BarraSegmenti(
                    listOf(
                        g.favorevoli to ColoriVoto.favorevole,
                        g.astenuti to ColoriVoto.astenuto,
                        g.contrari to ColoriVoto.contrario,
                        g.altri to ColoriVoto.altro,
                    ),
                    altezza = 8.dp,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Statistica(Testi.ribelli, g.ribelliLabel, Modifier.weight(1f))
                    Statistica(Testi.nonPartecipanti, g.nonPartecipantiLabel, Modifier.weight(1.3f))
                    Statistica(Testi.compattezza, g.compattezzaLabel, Modifier.weight(1f))
                }
            }
        },
    ) {
        // Il voto del gruppo sta sulla riga del nome, così barra e statistiche usano tutta la larghezza.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                g.gruppo,
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            g.votoGruppo?.let { linea ->
                Text(
                    linea.etichetta,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = ColoriVoto.per(linea),
                )
            }
        }
    }
}

@Composable
private fun Statistica(etichetta: String, valore: String, modifier: Modifier) {
    Column(modifier) {
        Text(etichetta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valore, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun FiltriVoti(stato: VotazioneState, azioni: AzioniVotazione) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stato.filtroGruppo?.let { gruppo ->
            item {
                FilterChip(
                    selected = true,
                    onClick = { azioni.filtraGruppo(null) },
                    label = { Text(gruppo) },
                    trailingIcon = {
                        Icon(Icons.Outlined.Close, Testi.rimuoviFiltro, Modifier.size(FilterChipDefaults.IconSize))
                    },
                )
            }
        }
        if (stato.ribelli.isNotEmpty()) {
            item {
                FilterChip(
                    selected = stato.soloRibelli,
                    onClick = { azioni.mostraSoloRibelli(!stato.soloRibelli) },
                    label = { Text(Testi.soloRibelli(stato.ribelli.size)) },
                )
            }
        }
        items(stato.tipiPresenti) { tipo ->
            FilterChip(
                selected = stato.filtro == tipo,
                onClick = { azioni.filtra(tipo) },
                label = { Text(tipo.etichetta) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RigaVoto(voto: VotoIndividuale, indice: Int, totale: Int, ribelle: Boolean, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(indice, totale),
        colors = coloriLista(),
        leadingContent = {
            Box(Modifier.size(12.dp).clip(CircleShape).background(ColoriVoto.seggio(voto.voto)))
        },
        supportingContent = { Text(voto.gruppo) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ribelle) Etichetta(Testi.ribelle)
                Text(
                    voto.voto.etichetta,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = ColoriVoto.per(voto.voto),
                )
            }
        },
    ) {
        Text(voto.nome)
    }
}
