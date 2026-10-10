package it.aula.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.aula.Testi
import it.aula.model.Legislatura
import it.aula.presentation.VersusState
import it.aula.presentation.VersusStore
import it.aula.presentation.VoceVersus

@Composable
fun VersusScreen(store: VersusStore, modifier: Modifier = Modifier) {
    val stato by store.state.collectAsStateWithLifecycle()
    VersusContenuto(
        stato,
        azioni = AzioniVersus(
            scegliA = store::scegliA,
            scegliB = store::scegliB,
            inverti = store::inverti,
            perAnno = store::mostraPerAnno,
            ricarica = store::ricarica,
        ),
        modifier = modifier,
    )
}

class AzioniVersus(
    val scegliA: (Int) -> Unit = {},
    val scegliB: (Int) -> Unit = {},
    val inverti: () -> Unit = {},
    val perAnno: (Boolean) -> Unit = {},
    val ricarica: () -> Unit = {},
)

/** I colori delle due parti, uguali nell'intestazione e nelle barre. */
@Composable
private fun coloreA(): Color = MaterialTheme.colorScheme.primary

@Composable
private fun coloreB(): Color = MaterialTheme.colorScheme.tertiary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VersusContenuto(stato: VersusState, azioni: AzioniVersus, modifier: Modifier = Modifier) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Column(modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        LargeFlexibleTopAppBar(
            title = { Text(Testi.versus) },
            subtitle = { Text(Testi.confrontoLegislature) },
            actions = { PulsanteTema() },
            scrollBehavior = scrollBehavior,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SceltaParte(stato.legislaturaA, stato.disponibili, coloreA(), azioni.scegliA, Modifier.weight(1f))
            IconButton(onClick = azioni.inverti) {
                Icon(Icons.Outlined.SwapHoriz, contentDescription = Testi.inverti)
            }
            SceltaParte(stato.legislaturaB, stato.disponibili, coloreB(), azioni.scegliB, Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = stato.perAnno,
                onClick = { azioni.perAnno(!stato.perAnno) },
                label = { Text(Testi.perAnno) },
                leadingIcon = if (stato.perAnno) {
                    { Icon(Icons.Outlined.Check, contentDescription = null, Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
            Spacer(Modifier.width(12.dp))
            when {
                stato.caricamento -> {
                    LoadingIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Testi.attesaVersus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                stato.errore -> {
                    Text(
                        Testi.erroreVersus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = azioni.ricarica) { Text(Testi.riprova) }
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(stato.sezioni, key = { it.titolo }) { sezione ->
                Sezione(titolo = sezione.titolo) {
                    sezione.voci.forEachIndexed { i, voce ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        RigaVersus(voce, stato.caricamento)
                    }
                }
            }
            item(key = "nota") {
                Text(
                    Testi.notaVersus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** Una delle due legislature: numero romano grande, periodo sotto, menu per cambiarla. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SceltaParte(
    legislatura: Legislatura?,
    disponibili: List<Legislatura>,
    colore: Color,
    onScegli: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var aperto by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedCard(onClick = { aperto = true }, modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(width = 24.dp, height = 4.dp).clip(CircleShape).background(colore))
                Spacer(Modifier.height(6.dp))
                Text(
                    legislatura?.romano ?: "…",
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = colore,
                )
                Text(
                    legislatura?.periodo ?: Testi.sceltaLegislatura,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        DropdownMenu(expanded = aperto, onDismissRequest = { aperto = false }) {
            disponibili.forEach { l ->
                DropdownMenuItem(
                    text = { Text(listOfNotNull(l.etichetta, l.periodo).joinToString(" · ")) },
                    trailingIcon = if (l.numero == legislatura?.numero) {
                        { Icon(Icons.Outlined.Check, contentDescription = Testi.selezionato) }
                    } else {
                        null
                    },
                    onClick = {
                        onScegli(l.numero)
                        aperto = false
                    },
                )
            }
        }
    }
}

/** Etichetta al centro, i due valori ai lati, sotto le barre che partono dal centro. */
@Composable
private fun RigaVersus(voce: VoceVersus, caricamento: Boolean) {
    val segnaposto = if (caricamento) "…" else "—"
    Column(Modifier.fillMaxWidth()) {
        Text(
            voce.etichetta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Valore(voce.a ?: segnaposto, voce.prevaleA, TextAlign.Start, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Valore(voce.b ?: segnaposto, voce.prevaleB, TextAlign.End, Modifier.weight(1f))
        }
        if (voce.quotaA != null && voce.quotaB != null) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Barra(voce.quotaA!!, coloreA(), daDestra = true, Modifier.weight(1f))
                Barra(voce.quotaB!!, coloreB(), daDestra = false, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Valore(testo: String, prevale: Boolean, allineamento: TextAlign, modifier: Modifier) {
    Text(
        testo,
        style = if (testo.length > 14) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
        fontWeight = if (prevale) FontWeight.Bold else FontWeight.Normal,
        color = if (prevale) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = allineamento,
        modifier = modifier,
    )
}

/** Mezza barra: quella di sinistra cresce verso sinistra, quella di destra verso destra. */
@Composable
private fun Barra(quota: Float, colore: Color, daDestra: Boolean, modifier: Modifier) {
    val animata by animateFloatAsState(quota.coerceIn(0f, 1f), label = "barra")
    val forma = RoundedCornerShape(50)
    Box(
        modifier.height(8.dp).clip(forma).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = if (daDestra) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth(animata).height(8.dp).clip(forma).background(colore))
    }
}
