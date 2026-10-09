package it.aula.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.aula.Testi
import it.aula.model.Adesione
import it.aula.model.Presenze
import it.aula.presentation.ParlamentareState
import it.aula.presentation.ParlamentareStore

@Composable
fun ParlamentareScreen(store: ParlamentareStore, onIndietro: () -> Unit) {
    val stato by store.state.collectAsStateWithLifecycle()
    ParlamentareContenuto(stato, onIndietro = onIndietro, onRiprova = store::ricarica)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ParlamentareContenuto(stato: ParlamentareState, onIndietro: () -> Unit = {}, onRiprova: () -> Unit = {}) {
    val p = stato.parlamentare

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p.ramo.etichetta) },
                navigationIcon = {
                    IconButton(onClick = onIndietro) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = Testi.indietro)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // Avatar ritagliato in una forma Expressive (cookie a 9 lobi) invece del cerchio.
                Box(Modifier.size(112.dp).clip(MaterialShapes.Cookie9Sided.toShape())) {
                    Avatar(p.fotoUrl, p.iniziali, 112.dp)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    p.nomeCompleto,
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    textAlign = TextAlign.Center,
                )
                if (p.gruppo.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        p.gruppo,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            stato.storiaGruppi?.takeIf { it.isNotEmpty() }?.let { storia ->
                StoriaGruppi(storia)
                Spacer(Modifier.height(16.dp))
            }

            val presenze = stato.presenze
            when {
                stato.caricamento && presenze == null ->
                    StatoCaricamento(Testi.attesaPresenze(p.ramo), Modifier.height(240.dp))
                stato.errore != null && presenze == null ->
                    StatoErrore(stato.errore!!, onRiprova, Modifier.height(280.dp))
                presenze != null -> SchedaPresenze(presenze)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SchedaPresenze(pr: Presenze) {
    Sezione(titolo = Testi.partecipazioneAlVoto) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(pr.presenzeLabel, style = MaterialTheme.typography.displayMediumEmphasized)
                Spacer(Modifier.width(8.dp))
                Text(
                    Testi.presenzeSu(pr.votazioniTotali),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            BarraSegmenti(
                listOf(
                    pr.presenze to MaterialTheme.colorScheme.primary,
                    pr.missioni to ColoriVoto.astenuto,
                    pr.assenze to ColoriVoto.contrario,
                ),
                altezza = 14.dp,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                VoceLegenda(MaterialTheme.colorScheme.primary, Testi.presenze, pr.presenzeLabel)
                VoceLegenda(ColoriVoto.astenuto, Testi.missioni, pr.missioniLabel)
                VoceLegenda(ColoriVoto.contrario, Testi.assenze, pr.assenzeLabel)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Sezione(titolo = Testi.votiEspressi) {
        Column {
            BarraVoti(pr.favorevoli, pr.contrari, pr.astenuti, altezza = 10.dp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                VoceLegenda(ColoriVoto.favorevole, Testi.favorevole, "${pr.favorevoli}")
                VoceLegenda(ColoriVoto.contrario, Testi.contrario, "${pr.contrari}")
                VoceLegenda(ColoriVoto.astenuto, Testi.astenuto, "${pr.astenuti}")
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        listOfNotNull(
            Testi.formulaPresenze,
            pr.nota,
        ).joinToString("\n"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** I gruppi della legislatura, dal primo all'attuale, con le date di adesione. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StoriaGruppi(storia: List<Adesione>) {
    val cambi = storia.size - 1
    Sezione(titolo = Testi.storiaGruppi(cambi)) {
        storia.forEachIndexed { i, adesione ->
            val attuale = adesione.al == null
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(
                            if (attuale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    if (i < storia.lastIndex) {
                        Box(Modifier.width(2.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(
                        adesione.gruppo,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = if (attuale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        adesione.periodo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

