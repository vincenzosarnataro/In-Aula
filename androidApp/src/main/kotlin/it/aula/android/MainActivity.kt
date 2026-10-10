package it.aula.android

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import it.aula.Testi
import it.aula.android.ui.LocalPreferenzaTema
import it.aula.android.ui.LocalSceltaLegislatura
import it.aula.android.ui.PreferenzaLegislatura
import it.aula.android.ui.PreferenzaTema
import it.aula.android.ui.SceltaLegislatura
import it.aula.android.ui.sfondoTema
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import it.aula.AppGraph
import it.aula.android.ui.AulaScreen
import it.aula.android.ui.AulaTheme
import it.aula.android.ui.GovernoScreen
import it.aula.android.ui.GruppiScreen
import it.aula.android.ui.GruppoScreen
import it.aula.android.ui.ParlamentareScreen
import it.aula.android.ui.ParlamentariScreen
import it.aula.android.ui.SchedaAttoScreen
import it.aula.android.ui.VotazioneScreen
import it.aula.android.ui.rememberEntryStore
import it.aula.android.ui.rememberTabStore
import it.aula.model.Atto
import it.aula.model.Parlamentare
import it.aula.model.Ramo
import it.aula.model.Votazione
import it.aula.model.VotoIndividuale
import it.aula.presentation.AulaStore
import it.aula.presentation.GovernoStore
import it.aula.presentation.GruppiStore
import it.aula.presentation.ParlamentariStore
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val preferenzaTema by lazy { PreferenzaTema(this) }
    private val preferenzaLegislatura by lazy { PreferenzaLegislatura(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.logChiamate = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        AppGraph.ripristinaLegislatura(preferenzaLegislatura.salvata)
        enableEdgeToEdge()
        setContent {
            val scuro = preferenzaTema.scelta.scuro(isSystemInDarkTheme())
            DisposableEffect(scuro) {
                // Icone delle barre di sistema e sfondo della finestra seguono il tema scelto,
                // non quello del sistema.
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { scuro },
                    navigationBarStyle = SystemBarStyle.auto(ScrimChiaro, ScrimScuro) { scuro },
                )
                window.decorView.setBackgroundColor(sfondoTema(scuro).toArgb())
                onDispose {}
            }
            CompositionLocalProvider(LocalPreferenzaTema provides preferenzaTema) {
                AulaTheme(scuro) { AulaApp(preferenzaLegislatura) }
            }
        }
    }

    private companion object {
        // Gli stessi scrim che enableEdgeToEdge usa di default per la navigazione a tre pulsanti.
        val ScrimChiaro = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val ScrimScuro = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

// ---------------------------------------------------------------- Destinazioni

/** Schermata principale con le tab Aula, Parlamentari, Gruppi e Governo. */
@Serializable
private data object Home : NavKey

@Serializable
private data class DettaglioVotazione(val votazione: Votazione) : NavKey

@Serializable
private data class SchedaParlamentare(val parlamentare: Parlamentare) : NavKey

/** Scheda di un provvedimento: iter nei due rami e tutte le sue votazioni. */
@Serializable
private data class SchedaProvvedimento(val atto: Atto) : NavKey

/** Scheda di un gruppo: solo la chiave, i membri si leggono dallo store della tab Gruppi. */
@Serializable
private data class SchedaGruppo(val ramo: Ramo, val nome: String) : NavKey

private enum class Tab(val titolo: String, val icona: ImageVector) {
    AULA(Testi.aula, Icons.Outlined.HowToVote),
    PARLAMENTARI(Testi.parlamentari, Icons.Outlined.Groups),
    GRUPPI(Testi.gruppi, Icons.Outlined.PieChart),
    GOVERNO(Testi.governo, Icons.Outlined.AccountBalance),
}

// ---------------------------------------------------------------- Navigazione
fun createMaterialTransition(forward: Boolean, motionScheme: MotionScheme): ContentTransform {
    val direction = if (forward) 1 else -1
    val animationDuration = 400
    val exit = (animationDuration * .35f).roundToInt()
    val enter = animationDuration - exit
    return (slideInHorizontally(
        initialOffsetX = { (it * 0.1f * direction).toInt() },
        animationSpec = motionScheme.slowSpatialSpec()
    ) + fadeIn(
        tween(
            delayMillis = exit,
            durationMillis = enter,
            easing = LinearOutSlowInEasing
        )
    )) togetherWith (slideOutHorizontally(
        targetOffsetX = { (it * -0.1f * direction).toInt() },
        animationSpec = motionScheme.slowSpatialSpec()
    ) + fadeOut(
        tween(durationMillis = exit, easing = FastOutLinearInEasing)
    ))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AulaApp(preferenzaLegislatura: PreferenzaLegislatura) {
    // Back stack di Navigation 3: serializzabile, sopravvive a rotazioni e process death.
    val backStack = rememberNavBackStack(Home)
    val indietro: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }

    // Gli store delle tab sono dell'Activity (fuori da NavDisplay): sopravvivono a rotazioni e
    // cambi di tab, e il dettaglio votazione può leggere l'elenco dei parlamentari già caricato.
    val aulaStore = rememberTabStore("aula") { AppGraph.aulaStore() }
    val parlamentariStore = rememberTabStore("parlamentari") { AppGraph.parlamentariStore() }
    val gruppiStore = rememberTabStore("gruppi") { AppGraph.gruppiStore() }
    val governoStore = rememberTabStore("governo") { AppGraph.governoStore() }
    val legislaturaStore = rememberTabStore("legislatura") { AppGraph.legislaturaStore() }
    val sceltaLegislatura = remember(legislaturaStore) { SceltaLegislatura(legislaturaStore, preferenzaLegislatura) }
    val motion = MaterialTheme.motionScheme
    NavDisplay(
        backStack = backStack,
        onBack = indietro,
        // Durante le transizioni le due schermate sono in dissolvenza: senza uno sfondo qui sotto
        // traspare quello della finestra, bianco anche in dark mode.
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        entryDecorators = listOf(
            // Stato salvabile (scroll, tab scelta…) per ogni voce del back stack.
            rememberSaveableStateHolderNavEntryDecorator(),
            // Un ViewModelStore per voce: gli store delle schermate vivono finché la voce è nella pila.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { createMaterialTransition(forward = true, motionScheme = motion) },
        popTransitionSpec = { createMaterialTransition(forward = false, motionScheme = motion) },
        predictivePopTransitionSpec = { createMaterialTransition(forward = false, motionScheme = motion) },
        entryProvider = entryProvider {
            entry<Home> {
                CompositionLocalProvider(LocalSceltaLegislatura provides sceltaLegislatura) {
                    Home(aulaStore, parlamentariStore, gruppiStore, governoStore, apri = { backStack.add(it) })
                }
            }
            entry<DettaglioVotazione> { chiave ->
                val store = rememberEntryStore { AppGraph.votazioneStore(chiave.votazione) }
                VotazioneScreen(store, onIndietro = indietro, onParlamentare = { voto ->
                    backStack.add(
                        SchedaParlamentare(
                            schedaDa(
                                voto,
                                chiave.votazione,
                                parlamentariStore
                            )
                        )
                    )
                }, onAtto = { backStack.add(SchedaProvvedimento(it)) })
            }
            entry<SchedaProvvedimento> { chiave ->
                val store = rememberEntryStore { AppGraph.schedaAttoStore(chiave.atto) }
                SchedaAttoScreen(
                    store,
                    onIndietro = indietro,
                    onVotazione = { backStack.add(DettaglioVotazione(it)) })
            }
            entry<SchedaGruppo> { chiave ->
                GruppoScreen(
                    gruppiStore,
                    ramo = chiave.ramo,
                    nome = chiave.nome,
                    onIndietro = indietro,
                    onParlamentare = { backStack.add(SchedaParlamentare(it)) },
                )
            }
            entry<SchedaParlamentare> { chiave ->
                val store = rememberEntryStore { AppGraph.parlamentareStore(chiave.parlamentare) }
                ParlamentareScreen(store, onIndietro = indietro)
            }
        },
    )
}


// ---------------------------------------------------------------- Home con le tab

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Home(
    aulaStore: AulaStore,
    parlamentariStore: ParlamentariStore,
    gruppiStore: GruppiStore,
    governoStore: GovernoStore,
    apri: (NavKey) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.AULA) }
    Scaffold(
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach { t ->
                    ShortNavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icona, contentDescription = null) },
                        label = { Text(t.titolo) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        val motion = MaterialTheme.motionScheme
        // Cambio di tab: dissolvenza incrociata con un leggero ingrandimento (fade through).
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(
                    motion.defaultSpatialSpec(),
                    initialScale = 0.96f
                )) togetherWith
                        fadeOut(motion.fastEffectsSpec())
            },
            label = "tab",
        ) { t ->
            when (t) {
                Tab.AULA -> AulaScreen(aulaStore, modifier) { apri(DettaglioVotazione(it)) }
                Tab.PARLAMENTARI -> ParlamentariScreen(parlamentariStore, modifier) {
                    apri(
                        SchedaParlamentare(it)
                    )
                }
                Tab.GRUPPI -> GruppiScreen(gruppiStore, modifier) { apri(SchedaGruppo(it.ramo, it.nome)) }
                Tab.GOVERNO -> GovernoScreen(governoStore, modifier) { apri(SchedaParlamentare(it)) }
            }
        }
    }
}

/** Se l'elenco è già in cache prendiamo la scheda completa (con foto). */
private fun schedaDa(
    voto: VotoIndividuale,
    votazione: Votazione,
    parlamentariStore: ParlamentariStore
): Parlamentare =
    parlamentariStore.current().tutti.firstOrNull { it.uri == voto.parlamentareUri }
        ?: Parlamentare(
            uri = voto.parlamentareUri,
            ramo = votazione.ramo,
            nome = voto.nome,
            cognome = "",
            gruppo = voto.gruppo,
            fotoUrl = null,
        )
