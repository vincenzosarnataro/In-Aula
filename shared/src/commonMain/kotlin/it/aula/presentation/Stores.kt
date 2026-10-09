package it.aula.presentation

import it.aula.Testi
import it.aula.data.ParlamentoRepository
import it.aula.data.inSedute
import it.aula.model.Adesione
import it.aula.model.AssentiGruppo
import it.aula.model.AssenzeDecisive
import it.aula.model.Atto
import it.aula.model.ConteggioVoto
import it.aula.model.DettaglioVotazione
import it.aula.model.Disposizione
import it.aula.model.Emiciclo
import it.aula.model.Esito
import it.aula.model.Parlamentare
import it.aula.model.Presenze
import it.aula.model.ProfiloParlamentare
import it.aula.model.Ramo
import it.aula.model.SchedaAtto
import it.aula.model.Seduta
import it.aula.model.TipoVoto
import it.aula.model.Votazione
import it.aula.model.VotoIndividuale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
}

class ParlamentariStore(private val repo: ParlamentoRepository) :
    Store<ParlamentariState>(ParlamentariState()) {

    private var job: Job? = null

    init {
        carica(forza = false)
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
                aggiorna { if (it.ramo != ramo) it else it.copy(tutti = elenco, caricamento = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                aggiorna { it.copy(caricamento = false, errore = e.messaggio()) }
            }
        }
    }
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

