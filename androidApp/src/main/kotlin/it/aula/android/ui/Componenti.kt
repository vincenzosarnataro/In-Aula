package it.aula.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import it.aula.Testi
import it.aula.model.Esito
import it.aula.model.Ramo

/** Barra impilata a segmenti proporzionali, con 2dp di stacco solo tra un segmento e l'altro. */
@Composable
fun BarraSegmenti(
    valori: List<Pair<Int, Color>>,
    modifier: Modifier = Modifier,
    altezza: Dp = 10.dp,
) {
    val totale = valori.sumOf { it.first }.coerceAtLeast(1)
    val vuoto = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().height(altezza).clip(RoundedCornerShape(altezza / 2))) {
        drawRect(vuoto)
        // Lo stacco va solo tra un segmento e l'altro: l'ultimo arriva fino al bordo.
        val segmenti = valori.filter { it.first > 0 }
        val stacco = 2.dp.toPx()
        val utile = size.width - stacco * (segmenti.size - 1).coerceAtLeast(0)
        var x = 0f
        segmenti.forEach { (n, colore) ->
            val w = (utile * n / totale).coerceAtLeast(1f)
            drawRect(color = colore, topLeft = Offset(x, 0f), size = Size(w, size.height))
            x += w + stacco
        }
    }
}

@Composable
fun BarraVoti(favorevoli: Int, contrari: Int, astenuti: Int, modifier: Modifier = Modifier, altezza: Dp = 10.dp) =
    BarraSegmenti(
        listOf(
            favorevoli to ColoriVoto.favorevole,
            contrari to ColoriVoto.contrario,
            astenuti to ColoriVoto.astenuto,
        ),
        modifier,
        altezza,
    )

@Composable
fun VoceLegenda(colore: Color, etichetta: String, valore: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(colore))
        Spacer(Modifier.width(6.dp))
        Text(etichetta, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(4.dp))
        Text(valore, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ChipEsito(esito: Esito) {
    if (esito == Esito.SCONOSCIUTO) return
    val colore = if (esito == Esito.APPROVATA) ColoriVoto.favorevole else ColoriVoto.contrario
    Surface(color = colore.copy(alpha = 0.14f), shape = CircleShape) {
        Text(
            esito.etichetta,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colore,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun Etichetta(testo: String) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = CircleShape) {
        Text(
            testo,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
fun SelettoreRamo(ramo: Ramo, onCambia: (Ramo) -> Unit, modifier: Modifier = Modifier) =
    GruppoPulsanti(Ramo.entries, ramo, { it.etichetta }, onCambia, modifier)

/**
 * Scelta singola con pulsanti connessi (M3 Expressive): sostituisce i segmented button.
 * Il pulsante selezionato si arrotonda del tutto, con la molla del motion scheme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> GruppoPulsanti(
    opzioni: List<T>,
    selezionata: T,
    etichetta: (T) -> String,
    onSeleziona: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        opzioni.forEachIndexed { i, opzione ->
            ToggleButton(
                checked = opzione == selezionata,
                onCheckedChange = { onSeleziona(opzione) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    opzioni.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(etichetta(opzione), maxLines = 1)
            }
        }
    }
}

/** Righe di lista a segmenti su superficie tonale, distinguibili dallo sfondo. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun coloriLista(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
)

/** Contenitore di sezione: superficie tonale con angoli ampi, come nelle schede Expressive. */
@Composable
fun Sezione(
    modifier: Modifier = Modifier,
    titolo: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(20.dp)) {
            if (titolo != null) {
                TitoloSezione(titolo)
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TitoloSezione(testo: String, modifier: Modifier = Modifier) {
    Text(
        testo,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@Composable
fun Avatar(fotoUrl: String?, iniziali: String, dimensione: Dp = 44.dp) {
    Box(
        Modifier.size(dimensione).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            iniziali,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelLarge,
        )
        if (fotoUrl != null) {
            AsyncImage(
                // Le foto sono pubblicate su http: forziamo https (il cleartext è bloccato).
                model = fotoUrl.replaceFirst("http://", "https://"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatoCaricamento(messaggio: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator(Modifier.size(64.dp))
        if (messaggio != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                messaggio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatoErrore(messaggio: String, onRiprova: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Testi.erroreTitolo, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            messaggio,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRiprova, shapes = ButtonDefaults.shapes()) { Text(Testi.riprova) }
    }
}

/** Campo di ricerca a pillola su superficie tonale, come la search bar Expressive. */
@Composable
fun CampoRicerca(
    valore: String,
    onCambia: (String) -> Unit,
    segnaposto: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = valore,
        onValueChange = onCambia,
        placeholder = { Text(segnaposto) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = if (valore.isNotEmpty()) {
            {
                IconButton(onClick = { onCambia("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = Testi.cancellaRicerca)
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

