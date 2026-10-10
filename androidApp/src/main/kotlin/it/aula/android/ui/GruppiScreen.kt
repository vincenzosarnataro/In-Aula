package it.aula.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.aula.Testi
import it.aula.model.GruppoParlamentare
import it.aula.model.Parlamentare
import it.aula.model.Ramo
import it.aula.presentation.GruppiState
import it.aula.presentation.GruppiStore

/** Il colore fisso del gruppo, definito nel modulo condiviso ([it.aula.model.ColoriGruppi]). */
fun coloreGruppo(gruppo: GruppoParlamentare): Color = Color(0xFF000000 or gruppo.colore)

// ---------------------------------------------------------------- Elenco

@Composable
fun GruppiScreen(
    store: GruppiStore,
    modifier: Modifier = Modifier,
    onGruppo: (GruppoParlamentare) -> Unit,
) {
    val stato by store.state.collectAsStateWithLifecycle()
    GruppiContenuto(
        stato,
        onRamo = store::selezionaRamo,
        onRiprova = store::ricarica,
        modifier = modifier,
        onGruppo = onGruppo,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GruppiContenuto(
    stato: GruppiState,
    onRamo: (Ramo) -> Unit = {},
    onRiprova: () -> Unit = {},
    modifier: Modifier = Modifier,
    onGruppo: (GruppoParlamentare) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Column(modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        LargeFlexibleTopAppBar(
            title = { Text(Testi.gruppiParlamentari) },
            subtitle = if (stato.gruppi.isNotEmpty()) {
                { Text(Testi.numeroGruppi(stato.gruppi.size) + " · " + Testi.seggi(stato.seggiTotali)) }
            } else {
                null
            },
            actions = {
                PulsanteLegislatura()
                PulsanteTema()
            },
            scrollBehavior = scrollBehavior,
        )
        SelettoreRamo(stato.ramo, onRamo, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        when {
            stato.caricamento && stato.gruppi.isEmpty() -> StatoCaricamento(Testi.attesaParlamentari(stato.ramo))
            stato.errore != null && stato.gruppi.isEmpty() -> StatoErrore(stato.errore!!, onRiprova)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                item {
                    Sezione(Modifier.padding(bottom = 12.dp), titolo = Testi.composizione) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TortaComposizione(stato.gruppi, stato.seggiTotali)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stato.nota,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(stato.gruppi, key = { _, g -> g.nome }) { i, g ->
                    SegmentedListItem(
                        onClick = { onGruppo(g) },
                        shapes = ListItemDefaults.segmentedShapes(i, stato.gruppi.size),
                        colors = coloriLista(),
                        // Con nomi su due righe l'elemento diventa a tre righe e Material allinea
                        // il contenuto iniziale in alto: il pallino resta invece sempre centrato.
                        verticalAlignment = Alignment.CenterVertically,
                        leadingContent = { Pallino(coloreGruppo(g)) },
                        supportingContent = { Text(Testi.seggi(g.seggi) + " · " + g.quotaLabel) },
                    ) {
                        Text(g.nome, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * Ciambella dei seggi per gruppo, dal più numeroso in senso orario a partire dalle 12, con il
 * totale al centro. Uno stacco di 2dp separa le fette; la legenda è l'elenco dei gruppi sotto.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TortaComposizione(gruppi: List<GruppoParlamentare>, totale: Int) {
    val descrizione = Testi.descrizioneComposizione(gruppi.map { it.nome to it.seggi })
    Box(
        Modifier.size(200.dp).semantics { contentDescription = descrizione },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (totale <= 0) return@Canvas
            val spessore = size.minDimension * 0.2f
            val diametro = size.minDimension - spessore
            val stacco = if (gruppi.size > 1) Math.toDegrees(2.dp.toPx() / (diametro / 2.0)).toFloat() else 0f
            var inizio = -90f
            gruppi.forEach { g ->
                val ampiezza = 360f * g.seggi / totale
                drawArc(
                    color = coloreGruppo(g),
                    startAngle = inizio + stacco / 2,
                    // Anche il gruppo più piccolo resta visibile come una tacca.
                    sweepAngle = (ampiezza - stacco).coerceAtLeast(0.6f),
                    useCenter = false,
                    topLeft = Offset(spessore / 2, spessore / 2),
                    size = Size(diametro, diametro),
                    style = Stroke(width = spessore),
                )
                inizio += ampiezza
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$totale", style = MaterialTheme.typography.displaySmallEmphasized)
            Text(
                Testi.seggiEtichetta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Pallino(colore: Color) {
    Box(Modifier.size(14.dp).clip(CircleShape).background(colore))
}

// ---------------------------------------------------------------- Scheda di un gruppo

/**
 * Il gruppo si legge dallo store dell'elenco: dopo la ricreazione del processo lo store riparte
 * dalla Camera, quindi se serve si riseleziona il ramo del gruppo aperto.
 */
@Composable
fun GruppoScreen(
    store: GruppiStore,
    ramo: Ramo,
    nome: String,
    onIndietro: () -> Unit,
    onParlamentare: (Parlamentare) -> Unit,
) {
    val stato by store.state.collectAsStateWithLifecycle()
    LaunchedEffect(ramo) { store.selezionaRamo(ramo) }
    GruppoContenuto(
        gruppo = stato.gruppo(nome).takeIf { stato.ramo == ramo },
        nome = nome,
        stato = stato,
        onIndietro = onIndietro,
        onRiprova = store::ricarica,
        onParlamentare = onParlamentare,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GruppoContenuto(
    gruppo: GruppoParlamentare?,
    nome: String,
    stato: GruppiState,
    onIndietro: () -> Unit = {},
    onRiprova: () -> Unit = {},
    onParlamentare: (Parlamentare) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(nome, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text(stato.ramo.etichetta) },
                navigationIcon = {
                    IconButton(onClick = onIndietro) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = Testi.indietro)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            gruppo == null && stato.errore != null ->
                StatoErrore(stato.errore!!, onRiprova, Modifier.padding(padding))
            gruppo == null ->
                StatoCaricamento(Testi.attesaParlamentari(stato.ramo), Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                item { Riepilogo(gruppo, Modifier.padding(bottom = 16.dp)) }
                item {
                    TitoloSezione(Testi.membri(gruppo.seggi), Modifier.padding(start = 4.dp, bottom = 8.dp))
                }
                itemsIndexed(gruppo.membri, key = { _, p -> p.uri }) { i, p ->
                    SegmentedListItem(
                        onClick = { onParlamentare(p) },
                        shapes = ListItemDefaults.segmentedShapes(i, gruppo.membri.size),
                        colors = coloriLista(),
                        leadingContent = { Avatar(p.fotoUrl, p.iniziali) },
                        supportingContent = if (p.cambiDiGruppo > 0) {
                            { Text(Testi.cambiDiGruppo(p.cambiDiGruppo), color = MaterialTheme.colorScheme.tertiary) }
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

/** Seggi, quota sul totale del ramo e quanti membri arrivano da un altro gruppo. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Riepilogo(gruppo: GruppoParlamentare, modifier: Modifier = Modifier) {
    val colore = coloreGruppo(gruppo)
    Sezione(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${gruppo.seggi}", style = MaterialTheme.typography.displayMediumEmphasized)
            Spacer(Modifier.width(8.dp))
            Text(
                Testi.quotaSeggi(gruppo.quotaLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        BarraSegmenti(
            listOf(
                gruppo.seggi to colore,
                (gruppo.seggiTotali - gruppo.seggi) to MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            altezza = 14.dp,
        )
        if (gruppo.conCambi > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                Testi.membriConCambi(gruppo.conCambi),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
