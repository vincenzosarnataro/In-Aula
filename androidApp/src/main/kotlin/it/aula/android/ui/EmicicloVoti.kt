package it.aula.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import it.aula.model.Disposizione
import it.aula.model.Seggio
import it.aula.model.VotoIndividuale

/**
 * Emiciclo a pallini, uno per parlamentare, colorati secondo il voto. Se è selezionato un
 * gruppo, gli altri seggi si attenuano. Il contenuto viene posato al centro dell'arco.
 *
 * Quando cambia l'ordinamento ogni pallino raggiunge il nuovo seggio con la molla spaziale
 * del motion scheme; alla prima apparizione i seggi si aprono a ventaglio dal centro.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmicicloVoti(
    seggi: List<VotoIndividuale>,
    disposizione: Disposizione,
    descrizione: String,
    modifier: Modifier = Modifier,
    gruppoEvidenziato: String? = null,
    centro: @Composable BoxScope.() -> Unit = {},
) {
    val destinazioni = remember(seggi, disposizione) {
        seggi.zip(disposizione.seggi).associate { (voto, seggio) -> voto.parlamentareUri to seggio }
    }
    var partenze by remember { mutableStateOf(emptyMap<String, Seggio>()) }
    var arrivi by remember { mutableStateOf(emptyMap<String, Seggio>()) }
    val progresso = remember { Animatable(1f) }
    val molla = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(destinazioni) {
        // Si riparte da dove i pallini si trovano adesso, anche a metà di un'animazione.
        val t = progresso.value
        partenze = arrivi.mapValues { (uri, a) -> interpola(partenze[uri] ?: a, a, t) }
        arrivi = destinazioni
        progresso.snapTo(0f)
        progresso.animateTo(1f, molla)
    }

    // L'ultimo gruppo evidenziato resta in memoria per poterlo dissolvere in uscita.
    var ultimoGruppo by remember { mutableStateOf(gruppoEvidenziato) }
    if (gruppoEvidenziato != null) ultimoGruppo = gruppoEvidenziato
    val evidenza by animateFloatAsState(
        if (gruppoEvidenziato != null) 1f else 0f,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "evidenza",
    )

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(disposizione.proporzione.toFloat())
            .semantics { contentDescription = descrizione },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val t = progresso.value
            seggi.forEach { voto ->
                val arrivo = arrivi[voto.parlamentareUri] ?: return@forEach
                val partenza = partenze[voto.parlamentareUri] ?: arrivo.copy(x = 0.5, y = 1.0)
                val s = interpola(partenza, arrivo, t)
                val colore = ColoriVoto.seggio(voto.voto)
                val attenuazione = if (voto.gruppo != ultimoGruppo) 1f - 0.85f * evidenza else 1f
                drawCircle(
                    color = colore.copy(alpha = colore.alpha * attenuazione),
                    radius = (s.raggio * size.width).toFloat(),
                    center = Offset((s.x * size.width).toFloat(), (s.y * size.height).toFloat()),
                )
            }
        }
        centro()
    }
}

private fun interpola(da: Seggio, a: Seggio, t: Float): Seggio =
    Seggio(x = da.x + (a.x - da.x) * t, y = da.y + (a.y - da.y) * t, raggio = a.raggio)
