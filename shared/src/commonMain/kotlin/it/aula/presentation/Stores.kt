package it.aula.presentation

import it.aula.Testi
import it.aula.data.ParlamentoRepository
import it.aula.data.inSedute
import it.aula.model.Adesione
import it.aula.model.AssentiGruppo
import it.aula.model.AssenzeDecisive
import it.aula.model.Atto
import it.aula.model.ComposizioneGoverno
import it.aula.model.ConteggioVoto
import it.aula.model.DettaglioVotazione
import it.aula.model.Disposizione
import it.aula.model.Emiciclo
import it.aula.model.Esito
import it.aula.model.Formati
import it.aula.model.Governo
import it.aula.model.GruppoParlamentare
import it.aula.model.Legislatura
import it.aula.model.Parlamentare
import it.aula.model.Presenze
import it.aula.model.ProfiloParlamentare
import it.aula.model.Ramo
import it.aula.model.SchedaAtto
import it.aula.model.Seduta
import it.aula.model.StatisticheAula
import it.aula.model.StatisticheLeggi
import it.aula.model.TipoVoto
import it.aula.model.Votazione
import it.aula.model.VotoIndividuale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Legislatura

data class LegislaturaState(
    /** Dalla più recente; vuota finché non è caricata. */
    val disponibili: List<Legislatura> = emptyList(),
    /** null = quella in corso. */
    val scelta: Int? = null,
) {
    val attiva: Legislatura?
        get() = disponibili.firstOrNull { it.numero == scelta } ?: disponibili.firstOrNull()
}

/** La legislatura su cui lavora tutta l'app. Le tab osservano il repository e ricaricano. */
class LegislaturaStore(private val repo: ParlamentoRepository) :
    Store<LegislaturaState>(LegislaturaState(scelta = repo.legislaturaScelta.value)) {

    init {
        scope.launch {
            val disponibili = repo.legislature()
            aggiorna { it.copy(disponibili = disponibili) }
        }
        scope.launch { repo.legislaturaScelta.collect { n -> aggiorna { it.copy(scelta = n) } } }
    }

    /**
     * Sceglie una legislatura. Restituisce il valore da salvare tra le preferenze: null se è
     * quella in corso, così all'inizio della prossima l'app la segue da sola.
     */
    fun scegli(numero: Int): Int? {
        val valore = numero.takeIf { it != current().disponibili.firstOrNull()?.numero }
        repo.scegliLegislatura(valore)
        return valore
    }
}

/** Esegue [azione] a ogni cambio di legislatura (non per quella iniziale). */
private fun <S : Any> Store<S>.alCambioDiLegislatura(repo: ParlamentoRepository, azione: () -> Unit) {
    scope.launch { repo.legislaturaScelta.drop(1).collect { azione() } }
}

// ------------------------------------------------------------------ Aula (sedute e votazioni)

/** Filtri rapidi sulla lista delle votazioni. */
enum class FiltroVotazioni(val etichetta: String) {
    TUTTE(Testi.filtroTutte),
    FIDUCIA(Testi.filtroFiducia),
    FINALI(Testi.filtroFinali),
    RESPINTE(Testi.filtroRespinte),
    SUL_FILO(Testi.filtroSulFilo),
    ;

    fun accetta(v: Votazione): Boolean = when (this) {
        TUTTE -> true
        FIDUCIA -> v.fiducia
        FINALI -> v.finale
        RESPINTE -> v.esito == Esito.RESPINTA
        SUL_FILO -> v.sulFilo
    }
}

data class AulaState(
    val ramo: Ramo = Ramo.CAMERA,
    val sedute: List<Seduta> = emptyList(),
    val caricamento: Boolean = false,
    val caricamentoAltre: Boolean = false,
    val altreDisponibili: Boolean = true,
    val errore: String? = null,
    val filtro: FiltroVotazioni = FiltroVotazioni.TUTTE,
    val ricerca: String = "",
    /** Tema TESEO scelto (solo Senato). */
    val tema: String? = null,
) {
    val filtriAttivi: Boolean get() = filtro != FiltroVotazioni.TUTTE || ricerca.isNotBlank() || tema != null

    /** Le sedute con le sole votazioni che passano filtri e ricerca; quelle vuote spariscono. */
    val seduteVisibili: List<Seduta> by lazy {
        if (!filtriAttivi) return@lazy sedute
        val parole = ricerca.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        sedute.mapNotNull { seduta ->
            val voti = seduta.votazioni.filter { v ->
                filtro.accetta(v) &&
                    (tema == null || tema in v.atto?.temi.orEmpty()) &&
                    (parole.isEmpty() || testoRicercabile(v).let { testo -> parole.all { it in testo } })
            }
            if (voti.isEmpty()) null else seduta.copy(votazioni = voti)
        }
    }

    val votazioniVisibili: Int get() = seduteVisibili.sumOf { it.votazioni.size }

    /** Temi presenti tra le votazioni caricate, dal più frequente. */
    val temiDisponibili: List<String> by lazy {
        sedute.flatMap { it.votazioni }
            .flatMap { v -> v.atto?.temi.orEmpty().distinct() }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(MAX_TEMI)
    }

    private companion object {
        const val MAX_TEMI = 15

        fun testoRicercabile(v: Votazione): String = listOfNotNull(
            v.oggetto,
            v.descrizione,
            v.atto?.numero,
            v.atto?.numero?.replace(" ", ""),
            v.atto?.titolo,
            v.atto?.titoloBreve,
            v.atto?.natura,
        ).joinToString(" ").lowercase()
    }
}

class AulaStore(private val repo: ParlamentoRepository) : Store<AulaState>(AulaState()) {
    private val votazioni = mutableMapOf<Ramo, List<Votazione>>()
    private var job: Job? = null

    init {
        carica(azzera = true)
        alCambioDiLegislatura(repo) {
            votazioni.clear()
            aggiorna { it.copy(sedute = emptyList(), errore = null, tema = null, altreDisponibili = true) }
            carica(azzera = true)
        }
    }

    fun selezionaRamo(ramo: Ramo) {
        if (ramo == current().ramo) return
        val giaCaricate = votazioni[ramo]
        aggiorna {
            it.copy(
                ramo = ramo,
                sedute = giaCaricate?.inSedute().orEmpty(),
                errore = null,
                // I temi esistono solo al Senato e sono diversi tra i rami.
                tema = null,
                altreDisponibili = true,
            )
        }
        if (giaCaricate == null) carica(azzera = true)
    }

    fun ricarica() = carica(azzera = true)

    fun filtra(filtro: FiltroVotazioni) = aggiorna { it.copy(filtro = filtro) }

    fun cerca(testo: String) = aggiorna { it.copy(ricerca = testo) }

    fun scegliTema(tema: String?) = aggiorna { it.copy(tema = if (it.tema == tema) null else tema) }

    fun caricaAltre() {
        val s = current()
        if (s.caricamento || s.caricamentoAltre || !s.altreDisponibili) return
        carica(azzera = false)
    }

    private fun carica(azzera: Boolean) {
        val ramo = current().ramo
        job?.cancel()
        job = scope.launch {
            aggiorna { it.copy(caricamento = azzera, caricamentoAltre = !azzera, errore = null) }
            try {
                val esistenti = if (azzera) emptyList() else votazioni[ramo].orEmpty()
                val nuove = repo.votazioni(ramo, PAGINA, esistenti.size)
                val tutte = (esistenti + nuove).distinctBy { it.uri }
                votazioni[ramo] = tutte
                aggiorna {
                    if (it.ramo != ramo) it else it.copy(
                        sedute = tutte.inSedute(),
                        caricamento = false,
                        caricamentoAltre = false,
                        altreDisponibili = nuove.size == PAGINA,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                aggiorna { it.copy(caricamento = false, caricamentoAltre = false, errore = e.messaggio()) }
            }
        }
    }

    private companion object {
        const val PAGINA = 120
    }
}

// ------------------------------------------------------------------ Dettaglio votazione

/** Come ordinare i seggi nell'emiciclo. */
enum class VistaEmiciclo(val etichetta: String) {
    VOTO(Testi.perVoto),
    GRUPPO(Testi.perGruppo),
}

data class VotazioneState(
    val votazione: Votazione,
    val dettaglio: DettaglioVotazione? = null,
    val filtro: TipoVoto? = null,
    val filtroGruppo: String? = null,
    val soloRibelli: Boolean = false,
    val vista: VistaEmiciclo = VistaEmiciclo.GRUPPO,
    val caricamento: Boolean = true,
    val errore: String? = null,
) {
    /** Parlamentari che hanno votato diversamente dalla linea prevalente del proprio gruppo. */
    val ribelli: Set<String> by lazy {
        val linee = dettaglio?.ripartizione.orEmpty().associate { it.gruppo to it.votoGruppo }
        dettaglio?.voti.orEmpty()
            .filter { v ->
                val linea = linee[v.gruppo]
                linea != null && v.voto.palese && v.voto != linea
            }
            .mapTo(mutableSetOf()) { it.parlamentareUri }
    }

    val votiFiltrati: List<VotoIndividuale>
        get() = dettaglio?.voti.orEmpty().filter {
            (filtro == null || it.voto == filtro) &&
                (filtroGruppo == null || it.gruppo == filtroGruppo) &&
                (!soloRibelli || it.parlamentareUri in ribelli)
        }

    /** Tipi di voto presenti in questa votazione, per i chip di filtro. */
    val tipiPresenti: List<TipoVoto>
        get() = dettaglio?.voti.orEmpty().map { it.voto }.distinct().sortedBy { it.ordinal }

    /** I voti nell'ordine in cui occupano l'emiciclo, da sinistra a destra. */
    val seggi: List<VotoIndividuale> by lazy {
        val voti = dettaglio?.voti.orEmpty()
        when (vista) {
            VistaEmiciclo.VOTO -> voti.sortedWith(compareBy({ ordineVoto(it.voto) }, { it.gruppo }))
            VistaEmiciclo.GRUPPO -> {
                // Gruppi che votano a favore a sinistra, contrari a destra: approssima
                // la divisione tra maggioranza e opposizione senza doverla conoscere.
                val gruppi = dettaglio?.ripartizione.orEmpty()
                    .sortedWith(compareBy({ it.votoGruppo?.let(::ordineVoto) ?: Int.MAX_VALUE }, { -it.totale }))
                    .mapIndexed { i, g -> g.gruppo to i }
                    .toMap()
                voti.sortedWith(compareBy({ gruppi[it.gruppo] ?: Int.MAX_VALUE }, { ordineVoto(it.voto) }))
            }
        }
    }

    val disposizione: Disposizione by lazy { Emiciclo.disponi(seggi.size) }

    /**
     * Se gli assenti dei gruppi schierati con la parte perdente avrebbero potuto ribaltare
     * l'esito votando con il proprio gruppo. Le missioni non contano: sono assenze giustificate.
     * Al Senato l'assente semplice non è registrato, quindi il calcolo vale solo per la Camera.
     */
    val assenzeDecisive: AssenzeDecisive? by lazy {
        val servivano = votazione.votiPerRibaltare ?: return@lazy null
        if (votazione.segreta) return@lazy null
        val perdenti = when (votazione.esito) {
            Esito.APPROVATA -> buildSet {
                add(TipoVoto.CONTRARIO)
                if (votazione.ramo == Ramo.SENATO) add(TipoVoto.ASTENUTO)
            }
            Esito.RESPINTA -> setOf(TipoVoto.FAVOREVOLE)
            Esito.SCONOSCIUTO -> return@lazy null
        }
        val gruppi = dettaglio?.ripartizione.orEmpty()
            .filter { it.votoGruppo in perdenti && it.nonPartecipanti > 0 }
            .sortedByDescending { it.nonPartecipanti }
        val assenti = gruppi.sumOf { it.nonPartecipanti }
        if (assenti < servivano) return@lazy null
        AssenzeDecisive(
            servivano = servivano,
            assenti = assenti,
            perGruppo = gruppi.map { AssentiGruppo(it.gruppo, it.nonPartecipanti) },
            decisiviDaSoli = gruppi.filter { it.nonPartecipanti >= servivano }.map { it.gruppo },
        )
    }

    /** Quanti seggi per ciascun tipo di voto, nell'ordine dell'emiciclo: per la legenda. */
    val conteggi: List<ConteggioVoto> by lazy {
        dettaglio?.voti.orEmpty().groupingBy { it.voto }.eachCount()
            .map { (tipo, n) -> ConteggioVoto(tipo, n) }
            .sortedBy { ordineVoto(it.tipo) }
    }

    val messaggioAttesa: String
        get() = Testi.attesaDettaglio(votazione.ramo)
}

/** Favorevoli a sinistra, poi astenuti e contrari; chi non ha votato chiude a destra. */
private fun ordineVoto(tipo: TipoVoto): Int = when (tipo) {
    TipoVoto.FAVOREVOLE -> 0
    TipoVoto.ASTENUTO -> 1
    TipoVoto.CONTRARIO -> 2
    TipoVoto.HA_VOTATO -> 3
    TipoVoto.PRESENTE_NON_VOTANTE -> 4
    TipoVoto.PRESIDENTE_DI_TURNO -> 5
    TipoVoto.MISSIONE -> 6
    TipoVoto.NON_HA_VOTATO -> 7
    TipoVoto.ASSENTE -> 8
}

class VotazioneStore(
    private val repo: ParlamentoRepository,
    votazione: Votazione,
) : Store<VotazioneState>(VotazioneState(votazione)) {

    init {
        ricarica()
    }

    fun filtra(tipo: TipoVoto?) = aggiorna { it.copy(filtro = if (it.filtro == tipo) null else tipo) }

    fun filtraGruppo(gruppo: String?) =
        aggiorna { it.copy(filtroGruppo = if (it.filtroGruppo == gruppo) null else gruppo) }

    fun mostraSoloRibelli(attivo: Boolean) = aggiorna { it.copy(soloRibelli = attivo) }

    fun cambiaVista(vista: VistaEmiciclo) = aggiorna { it.copy(vista = vista) }

    fun ricarica() {
        scope.launch {
            aggiorna { it.copy(caricamento = true, errore = null) }
            try {
                val dettaglio = repo.dettaglio(current().votazione)
                aggiorna { it.copy(dettaglio = dettaglio, caricamento = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                aggiorna { it.copy(caricamento = false, errore = e.messaggio()) }
            }
        }
    }
}

// ------------------------------------------------------------------ Elenco parlamentari

data class ParlamentariState(
    val ramo: Ramo = Ramo.CAMERA,
    val tutti: List<Parlamentare> = emptyList(),
    val ricerca: String = "",
    val soloCambi: Boolean = false,
    /** Legislatura conclusa: l'elenco è dei parlamentari a fine legislatura. */
    val conclusa: Boolean = false,
    val caricamento: Boolean = false,
    val errore: String? = null,
) {
    val visibili: List<Parlamentare>
        get() {
            val q = ricerca.trim()
            return tutti.filter {
                (!soloCambi || it.cambiDiGruppo > 0) &&
                    (q.isEmpty() || it.nomeCompleto.contains(q, ignoreCase = true) || it.gruppo.contains(q, ignoreCase = true))
            }
        }

    /** Quanti parlamentari in carica hanno cambiato gruppo in questa legislatura. */
    val conCambi: Int get() = tutti.count { it.cambiDiGruppo > 0 }

    val sottotitolo: String get() = Testi.membriInElenco(visibili.size, conclusa)
}

class ParlamentariStore(private val repo: ParlamentoRepository) :
    Store<ParlamentariState>(ParlamentariState()) {

    private var job: Job? = null

    init {
        carica(forza = false)
        alCambioDiLegislatura(repo) {
            aggiorna { it.copy(tutti = emptyList(), soloCambi = false, errore = null) }
            carica(forza = false)
        }
    }

    fun selezionaRamo(ramo: Ramo) {
        if (ramo == current().ramo) return
        aggiorna { it.copy(ramo = ramo, tutti = emptyList(), errore = null) }
        carica(forza = false)
    }

    fun cerca(testo: String) = aggiorna { it.copy(ricerca = testo) }

    fun mostraSoloCambi(attivo: Boolean) = aggiorna { it.copy(soloCambi = attivo) }

    fun ricarica() = carica(forza = true)

    private fun carica(forza: Boolean) {
        val ramo = current().ramo
        job?.cancel()
        job = scope.launch {
            aggiorna { it.copy(caricamento = true, errore = null) }
            try {
                val elenco = repo.parlamentari(ramo, forza)
                val conclusa = repo.legislatura().conclusa
                aggiorna { if (it.ramo != ramo) it else it.copy(tutti = elenco, conclusa = conclusa, caricamento = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                aggiorna { it.copy(caricamento = false, errore = e.messaggio()) }
            }
        }
    }
}

// ------------------------------------------------------------------ Gruppi parlamentari

data class GruppiState(
    val ramo: Ramo = Ramo.CAMERA,
    /** Dal gruppo con più seggi. */
    val gruppi: List<GruppoParlamentare> = emptyList(),
    /** Legislatura conclusa: la composizione è quella a fine legislatura. */
    val conclusa: Boolean = false,
    val caricamento: Boolean = false,
    val errore: String? = null,
) {
    val seggiTotali: Int get() = gruppi.firstOrNull()?.seggiTotali ?: 0

    val nota: String get() = Testi.notaGruppi(conclusa)

    fun gruppo(nome: String): GruppoParlamentare? = gruppi.firstOrNull { it.nome == nome }
}

class GruppiStore(private val repo: ParlamentoRepository) : Store<GruppiState>(GruppiState()) {

    private var job: Job? = null

    init {
        carica(forza = false)
        alCambioDiLegislatura(repo) {
            aggiorna { it.copy(gruppi = emptyList(), errore = null) }
            carica(forza = false)
        }
    }

    fun selezionaRamo(ramo: Ramo) {
        if (ramo == current().ramo) return
        aggiorna { it.copy(ramo = ramo, gruppi = emptyList(), errore = null) }
        carica(forza = false)
    }

    fun ricarica() = carica(forza = true)

    private fun carica(forza: Boolean) {
        val ramo = current().ramo
        job?.cancel()
        job = scope.launch {
            aggiorna { it.copy(caricamento = true, errore = null) }
            try {
                val gruppi = repo.parlamentari(ramo, forza).inGruppi(ramo)
                val conclusa = repo.legislatura().conclusa
                aggiorna { if (it.ramo != ramo) it else it.copy(gruppi = gruppi, conclusa = conclusa, caricamento = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                aggiorna { it.copy(caricamento = false, errore = e.messaggio()) }
            }
        }
    }
}

/** Raggruppa i parlamentari in carica per gruppo attuale, dal gruppo più numeroso. */
internal fun List<Parlamentare>.inGruppi(ramo: Ramo): List<GruppoParlamentare> =
    groupBy { it.gruppo.ifBlank { Testi.senzaGruppo } }
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, List<Parlamentare>>> { it.value.size }.thenBy { it.key })
        .map { (nome, membri) ->
            GruppoParlamentare(
                ramo = ramo,
                nome = nome,
                membri = membri.sortedWith(compareBy({ it.cognome }, { it.nome })),
                seggiTotali = size,
            )
        }

// ------------------------------------------------------------------ Scheda parlamentare

data class ParlamentareState(
    val parlamentare: Parlamentare,
    val presenze: Presenze? = null,
    /** Nascita, studi, elezione; null finché non è caricato o se non disponibile. */
    val profilo: ProfiloParlamentare? = null,
    /** Gruppi della legislatura, dal primo; null finché non è caricata o se non disponibile. */
    val storiaGruppi: List<Adesione>? = null,
    val caricamento: Boolean = true,
    val errore: String? = null,
)

class ParlamentareStore(
    private val repo: ParlamentoRepository,
    parlamentare: Parlamentare,
) : Store<ParlamentareState>(ParlamentareState(parlamentare)) {

    init {
        ricarica()
    }

    fun ricarica() {
        // Profilo e storia dei gruppi sono indipendenti dalle presenze (e molto più rapidi).
        scope.launch {
            val profilo = runCatching { repo.profilo(current().parlamentare) }.getOrNull()
            aggiorna { it.copy(profilo = profilo) }
        }
        scope.launch {
            val storia = runCatching { repo.storiaGruppi(current().parlamentare) }.getOrNull()
            aggiorna { it.copy(storiaGruppi = storia) }
        }
        scope.launch {
            aggiorna { it.copy(caricamento = true, errore = null) }
            try {
                val presenze = repo.presenze(current().parlamentare)
                aggiorna { it.copy(presenze = presenze, caricamento = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                aggiorna { it.copy(caricamento = false, errore = e.messaggio()) }
            }
        }
    }
}

// ------------------------------------------------------------------ Scheda di un provvedimento

data class SchedaAttoState(
    val atto: Atto,
    val scheda: SchedaAtto? = null,
    val caricamento: Boolean = true,
    val errore: String? = null,
) {
    /** Le votazioni raggruppate per seduta, come nella lista principale. */
    val sedute: List<Seduta> by lazy { scheda?.votazioni.orEmpty().inSedute() }

    val messaggioAttesa: String
        get() = Testi.attesaSchedaAtto
}

class SchedaAttoStore(
    private val repo: ParlamentoRepository,
    atto: Atto,
) : Store<SchedaAttoState>(SchedaAttoState(atto)) {

    init {
        ricarica()
    }

    fun ricarica() {
        scope.launch {
            aggiorna { it.copy(caricamento = true, errore = null) }
            try {
                val scheda = repo.schedaAtto(current().atto)
                aggiorna { it.copy(scheda = scheda, caricamento = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                aggiorna { it.copy(caricamento = false, errore = e.messaggio()) }
            }
        }
    }
}


// ------------------------------------------------------------------ Governo

data class GovernoState(
    /** Governi della legislatura, dal più recente. */
    val governi: List<Governo> = emptyList(),
    val scelto: Governo? = null,
    val composizione: ComposizioneGoverno? = null,
    val caricamento: Boolean = false,
    val errore: String? = null,
) {
    /** La composizione mostrata appartiene al governo scelto (non a quello di prima). */
    val composizioneScelta: ComposizioneGoverno?
        get() = composizione?.takeIf { it.governo.uri == scelto?.uri }
}

class GovernoStore(private val repo: ParlamentoRepository) : Store<GovernoState>(GovernoState()) {

    private var job: Job? = null

    init {
        carica()
        alCambioDiLegislatura(repo) {
            aggiorna { GovernoState() }
            carica()
        }
    }

    fun scegli(governo: Governo) {
        if (governo.uri == current().scelto?.uri) return
        aggiorna { it.copy(scelto = governo, errore = null) }
        carica()
    }

    fun ricarica() = carica()

    /** Elenco dei governi (se manca) e composizione di quello scelto, di default il più recente. */
    private fun carica() {
        job?.cancel()
        job = scope.launch {
            aggiorna { it.copy(caricamento = true, errore = null) }
            try {
                val governi = current().governi.ifEmpty { repo.governi() }
                val scelto = current().scelto ?: governi.firstOrNull()
                aggiorna { it.copy(governi = governi, scelto = scelto) }
                val composizione = scelto?.let { repo.composizioneGoverno(it) }
                aggiorna { it.copy(composizione = composizione, caricamento = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                aggiorna { it.copy(caricamento = false, errore = e.messaggio()) }
            }
        }
    }
}

// ------------------------------------------------------------------ Versus: confronto tra legislature

/** Quanto è stato caricato di una legislatura: ogni parte arriva per conto suo. */
data class DatiLegislatura(
    val governi: List<Governo>? = null,
    val camera: StatisticheAula? = null,
    val senato: StatisticheAula? = null,
    val leggi: StatisticheLeggi? = null,
    /** Almeno una parte non è arrivata. */
    val errore: Boolean = false,
) {
    val completi: Boolean get() = governi != null && camera != null && senato != null && leggi != null
}

/**
 * Una riga del confronto. [quotaA] e [quotaB] (0–1) sono le barre, in proporzione al valore più
 * alto dei due; null se il confronto non ha senso (testo) o un valore manca.
 */
data class VoceVersus(
    val etichetta: String,
    val a: String?,
    val b: String?,
    val quotaA: Float? = null,
    val quotaB: Float? = null,
) {
    val prevaleA: Boolean get() = (quotaA ?: 0f) > (quotaB ?: 0f)
    val prevaleB: Boolean get() = (quotaB ?: 0f) > (quotaA ?: 0f)
}

data class SezioneVersus(val titolo: String, val voci: List<VoceVersus>)

data class VersusState(
    /** Dalla più recente. */
    val disponibili: List<Legislatura> = emptyList(),
    val a: Int? = null,
    val b: Int? = null,
    /** Conteggi divisi per gli anni di durata: le legislature durano diversamente. */
    val perAnno: Boolean = false,
    val dati: Map<Int, DatiLegislatura> = emptyMap(),
) {
    val legislaturaA: Legislatura? get() = disponibili.firstOrNull { it.numero == a }
    val legislaturaB: Legislatura? get() = disponibili.firstOrNull { it.numero == b }

    private val datiA: DatiLegislatura get() = a?.let { dati[it] } ?: DatiLegislatura()
    private val datiB: DatiLegislatura get() = b?.let { dati[it] } ?: DatiLegislatura()

    val caricamento: Boolean
        get() = listOf(datiA, datiB).any { !it.completi && !it.errore }
    val errore: Boolean get() = datiA.errore || datiB.errore

    val sezioni: List<SezioneVersus> by lazy {
        val la = legislaturaA ?: return@lazy emptyList()
        val lb = legislaturaB ?: return@lazy emptyList()
        val anniA = la.giorni?.let { it / 365.25 }
        val anniB = lb.giorni?.let { it / 365.25 }

        /** Conteggio, diviso per anno se richiesto. */
        fun conteggio(etichetta: String, va: Int?, vb: Int?, sempreTotale: Boolean = false): VoceVersus {
            val dividi = perAnno && !sempreTotale
            val xa = va?.let { if (dividi) anniA?.let { anni -> it / anni } else it.toDouble() }
            val xb = vb?.let { if (dividi) anniB?.let { anni -> it / anni } else it.toDouble() }
            fun testo(x: Double?) = x?.let { if (dividi) Formati.decimale(it) else Formati.migliaia(it.toInt()) }
            return voce(if (dividi) Testi.allAnno(etichetta) else etichetta, xa, xb, testo(xa), testo(xb))
        }

        fun quota(etichetta: String, xa: Double?, xb: Double?) =
            voce(etichetta, xa, xb, xa?.let(Formati::percentuale), xb?.let(Formati::percentuale))

        fun ramo(titolo: String, chi: String, sa: StatisticheAula?, sb: StatisticheAula?) = SezioneVersus(
            titolo,
            listOf(
                conteggio(Testi.votazioniVersus, sa?.votazioni, sb?.votazioni),
                conteggio(Testi.votiDiFiducia, sa?.fiducie, sb?.fiducie),
                conteggio(Testi.votiFinali, sa?.finali, sb?.finali),
                quota(Testi.quotaRespinte, sa?.quotaRespinte, sb?.quotaRespinte),
                conteggio(Testi.hannoCambiatoGruppoVersus(chi), sa?.cambiDiGruppo, sb?.cambiDiGruppo, sempreTotale = true),
            ),
        )

        val ga = datiA.governi
        val gb = datiB.governi
        listOf(
            SezioneVersus(
                Testi.inSintesi,
                listOf(
                    voce(
                        Testi.durata,
                        la.giorni?.toDouble(),
                        lb.giorni?.toDouble(),
                        la.giorni?.let { Testi.durata(it, la.conclusa) },
                        lb.giorni?.let { Testi.durata(it, lb.conclusa) },
                    ),
                    conteggio(Testi.governi, ga?.size, gb?.size, sempreTotale = true),
                    VoceVersus(Testi.presidentiDelConsiglio, ga?.let(::presidenti), gb?.let(::presidenti)),
                ),
            ),
            SezioneVersus(
                Testi.leggi,
                listOf(
                    conteggio(Testi.leggiApprovate, datiA.leggi?.totale, datiB.leggi?.totale),
                    conteggio(Testi.leggiOrdinarie, datiA.leggi?.ordinarie, datiB.leggi?.ordinarie),
                    conteggio(Testi.conversioniDl, datiA.leggi?.conversioni, datiB.leggi?.conversioni),
                    conteggio(Testi.leggiCostituzionali, datiA.leggi?.costituzionali, datiB.leggi?.costituzionali),
                    quota(Testi.diIniziativaDelGoverno, datiA.leggi?.quotaGoverno, datiB.leggi?.quotaGoverno),
                ),
            ),
            ramo(Testi.camera, Testi.deputati, datiA.camera, datiB.camera),
            ramo(Testi.senato, Testi.senatori, datiA.senato, datiB.senato),
        )
    }

    private companion object {
        fun voce(etichetta: String, xa: Double?, xb: Double?, a: String?, b: String?): VoceVersus {
            val massimo = maxOf(xa ?: 0.0, xb ?: 0.0)
            val confrontabili = xa != null && xb != null && massimo > 0
            return VoceVersus(
                etichetta = etichetta,
                a = a,
                b = b,
                quotaA = if (confrontabili) (xa!! / massimo).toFloat() else null,
                quotaB = if (confrontabili) (xb!! / massimo).toFloat() else null,
            )
        }

        /** "Governo Conte I" → "Conte": i cognomi dei presidenti, dal primo governo, senza ripetizioni. */
        fun presidenti(governi: List<Governo>): String =
            governi.sortedBy { it.inizio }
                .map { it.nome.substringAfter("Governo ").trim() }
                .distinct()
                .joinToString(", ")
                .ifBlank { "—" }
    }
}

class VersusStore(private val repo: ParlamentoRepository) : Store<VersusState>(VersusState()) {

    private val caricamenti = mutableMapOf<Int, Job>()

    init {
        scope.launch {
            val disponibili = repo.legislature()
            // Di default la legislatura scelta nell'app contro quella prima.
            val a = repo.legislatura().numero
            val b = disponibili.map { it.numero }.filter { it < a }.maxOrNull()
                ?: disponibili.map { it.numero }.firstOrNull { it != a }
            aggiorna { it.copy(disponibili = disponibili, a = a, b = b) }
            carica(a)
            b?.let(::carica)
        }
    }

    fun scegliA(numero: Int) {
        aggiorna { it.copy(a = numero, b = if (it.b == numero) it.a else it.b) }
        current().let { s -> listOfNotNull(s.a, s.b).forEach(::carica) }
    }

    fun scegliB(numero: Int) {
        aggiorna { it.copy(b = numero, a = if (it.a == numero) it.b else it.a) }
        current().let { s -> listOfNotNull(s.a, s.b).forEach(::carica) }
    }

    /** Scambia le due colonne. */
    fun inverti() = aggiorna { it.copy(a = it.b, b = it.a) }

    fun mostraPerAnno(attivo: Boolean) = aggiorna { it.copy(perAnno = attivo) }

    /** Riprova le parti mancanti. */
    fun ricarica() {
        val s = current()
        listOfNotNull(s.a, s.b).forEach { n ->
            if (s.dati[n]?.errore == true) {
                caricamenti.remove(n)
                aggiorna { it.copy(dati = it.dati + (n to it.dati.getValue(n).copy(errore = false))) }
            }
            carica(n)
        }
    }

    /** Carica le parti mancanti di una legislatura, ciascuna appena arriva. */
    private fun carica(n: Int) {
        if (caricamenti[n]?.isActive == true) return
        val presenti = current().dati[n] ?: DatiLegislatura()
        if (presenti.completi) return
        caricamenti[n] = scope.launch {
            fun salva(trasforma: (DatiLegislatura) -> DatiLegislatura) =
                aggiorna { it.copy(dati = it.dati + (n to trasforma(it.dati[n] ?: DatiLegislatura()))) }

            suspend fun parte(giaPresente: Boolean, carica: suspend () -> Unit) {
                if (giaPresente) return
                try {
                    carica()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    salva { it.copy(errore = true) }
                }
            }
            // La Camera risponde in fretta, il Senato in fila: prima le righe veloci.
            val camera = launch {
                parte(presenti.governi != null) { repo.governi(n).let { g -> salva { it.copy(governi = g) } } }
                parte(presenti.camera != null) { repo.statisticheAula(Ramo.CAMERA, n).let { c -> salva { it.copy(camera = c) } } }
            }
            val senato = launch {
                parte(presenti.leggi != null) { repo.statisticheLeggi(n).let { l -> salva { it.copy(leggi = l) } } }
                parte(presenti.senato != null) { repo.statisticheAula(Ramo.SENATO, n).let { st -> salva { it.copy(senato = st) } } }
            }
            camera.join()
            senato.join()
        }
    }
}
