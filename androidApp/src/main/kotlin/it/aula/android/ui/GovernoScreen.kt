package it.aula.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.aula.Testi
import it.aula.model.Governo
import it.aula.model.MembroGoverno
import it.aula.model.Parlamentare
import it.aula.presentation.GovernoState
import it.aula.presentation.GovernoStore

@Composable
fun GovernoScreen(
    store: GovernoStore,
    modifier: Modifier = Modifier,
    onParlamentare: (Parlamentare) -> Unit,
) {
    val stato by store.state.collectAsStateWithLifecycle()
    GovernoContenuto(
        stato,
        onGoverno = store::scegli,
        onRiprova = store::ricarica,
        modifier = modifier,
        onParlamentare = onParlamentare,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GovernoContenuto(
    stato: GovernoState,
    onGoverno: (Governo) -> Unit = {},
    onRiprova: () -> Unit = {},
    modifier: Modifier = Modifier,
    onParlamentare: (Parlamentare) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val composizione = stato.composizioneScelta

    Column(modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        LargeFlexibleTopAppBar(
            title = { Text(stato.scelto?.nome ?: Testi.governo) },
            subtitle = stato.scelto?.periodo?.let { { Text(it) } },
            actions = {
                PulsanteLegislatura()
                PulsanteTema()
            },
            scrollBehavior = scrollBehavior,
        )
        // Più governi nella legislatura: si sceglie quale mostrare, dal più recente.
        if (stato.governi.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(stato.governi, key = { it.uri }) { g ->
                    FilterChip(
                        selected = g.uri == stato.scelto?.uri,
                        onClick = { onGoverno(g) },
                        label = { Text(g.nome) },
                    )
                }
            }
        }
        when {
            stato.caricamento && composizione == null -> StatoCaricamento(Testi.attesaGoverno)
            stato.errore != null && composizione == null -> StatoErrore(stato.errore!!, onRiprova)
            composizione == null -> if (!stato.caricamento && stato.governi.isEmpty()) {
                Text(
                    Testi.nessunGoverno,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                composizione.presidente?.let { p ->
                    item(key = "presidente") { Presidente(p, onParlamentare, Modifier.padding(bottom = 16.dp)) }
                }
                elenco("vice", Testi.vicepresidenti, composizione.vicepresidenti, onParlamentare)
                elenco("ministri", Testi.ministri(composizione.ministri.size), composizione.ministri, onParlamentare)
                elenco(
                    "senza",
                    Testi.ministriSenzaPortafoglio(composizione.senzaPortafoglio.size),
                    composizione.senzaPortafoglio,
                    onParlamentare,
                )
                elenco(
                    "usciti",
                    Testi.avvicendamenti(composizione.avvicendamenti.size),
                    composizione.avvicendamenti,
                    onParlamentare,
                    conPeriodo = true,
                )
                item(key = "nota") {
                    Text(
                        Testi.notaGoverno,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 16.dp),
                    )
                }
            }
        }
    }
}

/** Il presidente del Consiglio in evidenza, come l'intestazione della scheda di un parlamentare. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Presidente(
    p: MembroGoverno,
    onParlamentare: (Parlamentare) -> Unit,
    modifier: Modifier = Modifier,
) {
    val deputato = p.deputato
    val contenuto: @Composable () -> Unit = {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).clip(MaterialShapes.Cookie9Sided.toShape())) {
                Avatar(p.fotoUrl, p.iniziali, 72.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    Testi.presidenteDelConsiglio,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(p.nomeCompleto, style = MaterialTheme.typography.headlineSmallEmphasized)
                p.periodo?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    val forma = MaterialTheme.shapes.extraLarge
    val colore = MaterialTheme.colorScheme.surfaceContainerLow
    val larghezza = modifier.fillMaxWidth()
    if (deputato != null) {
        Surface(onClick = { onParlamentare(deputato) }, modifier = larghezza, shape = forma, color = colore, content = contenuto)
    } else {
        Surface(modifier = larghezza, shape = forma, color = colore, content = contenuto)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.elenco(
    chiave: String,
    titolo: String,
    membri: List<MembroGoverno>,
    onParlamentare: (Parlamentare) -> Unit,
    conPeriodo: Boolean = false,
) {
    if (membri.isEmpty()) return
    item(key = "titolo-$chiave") {
        TitoloSezione(titolo, Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp))
    }
    itemsIndexed(membri, key = { i, m -> "$chiave-$i-${m.cognome}-${m.incarico}" }) { i, m ->
        RigaMembro(m, ListItemDefaults.segmentedShapes(i, membri.size), onParlamentare, conPeriodo)
    }
    item(key = "spazio-$chiave") { Spacer(Modifier.height(12.dp)) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RigaMembro(
    m: MembroGoverno,
    forme: ListItemShapes,
    onParlamentare: (Parlamentare) -> Unit,
    conPeriodo: Boolean,
) {
    val supporto: @Composable () -> Unit = {
        Column {
            Text(m.incarico)
            m.delega?.let { Text(it) }
            if (conPeriodo) m.periodo?.let { Text(it) }
        }
    }
    val interim: (@Composable () -> Unit)? = if (m.interim) {
        { Etichetta(Testi.adInterim) }
    } else {
        null
    }
    val deputato = m.deputato
    if (deputato != null) {
        SegmentedListItem(
            onClick = { onParlamentare(deputato) },
            shapes = forme,
            colors = coloriLista(),
            leadingContent = { Avatar(m.fotoUrl, m.iniziali) },
            trailingContent = interim,
            supportingContent = supporto,
        ) { Text(m.nomeCompleto) }
    } else {
        SegmentedListItem(
            shapes = forme,
            colors = coloriLista(),
            leadingContent = { Avatar(m.fotoUrl, m.iniziali) },
            trailingContent = interim,
            supportingContent = supporto,
        ) { Text(m.nomeCompleto) }
    }
}
