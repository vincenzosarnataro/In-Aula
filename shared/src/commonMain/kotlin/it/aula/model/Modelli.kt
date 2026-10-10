package it.aula.model

import it.aula.Testi
import kotlinx.serialization.Serializable

@Serializable
enum class Ramo(val etichetta: String) {
    CAMERA(Testi.camera),
    SENATO(Testi.senato),
}

@Serializable
enum class Esito(val etichetta: String) {
    APPROVATA(Testi.approvata),
    RESPINTA(Testi.respinta),
    SCONOSCIUTO("—"),
}

/** Votazione d'Aula. Le date sono sempre normalizzate in ISO `YYYY-MM-DD`. */
@Serializable
data class Votazione(
    val uri: String,
    val ramo: Ramo,
    val data: String,
    val numero: Int?,
    val titolo: String,
    val descrizione: String,
    val esito: Esito,
    val esitoGrezzo: String,
    val favorevoli: Int?,
    val contrari: Int?,
    val astenuti: Int?,
    val presenti: Int?,
    val fiducia: Boolean,
    val finale: Boolean,
    val segreta: Boolean,
    val sedutaUri: String,
    val numeroSeduta: Int?,
    /** Il provvedimento a cui si riferisce la votazione, se è noto o deducibile. */
    val atto: Atto? = null,
    /** Voti necessari per approvare, come pubblicati da Camera e Senato. */
    val maggioranza: Int? = null,
) {
    /** Che cosa si vota, in italiano corrente: "Emendamento 8.1010", "Voto finale"… */
    val oggetto: String get() = DescrizioneVoto.oggetto(ramo, titolo, descrizione)

    /** Riga principale negli elenchi: il provvedimento, se lo conosciamo, altrimenti l'oggetto. */
    val intestazione: String get() = atto?.titoloBreve ?: atto?.titolo ?: oggetto

    val dataEstesa: String get() = Formati.dataEstesa(data)
    val haConteggi: Boolean get() = favorevoli != null || contrari != null
    val favorevoliN: Int get() = favorevoli ?: 0
    val contrariN: Int get() = contrari ?: 0
    val astenutiN: Int get() = astenuti ?: 0

    /**
     * Voti di scarto come su Openpolis: distanza dei favorevoli dalla maggioranza richiesta.
     * Senza il dato della maggioranza si ripiega sulla differenza tra favorevoli e contrari.
     */
    val scarto: Int?
        get() = when {
            !haConteggi -> null
            maggioranza != null -> kotlin.math.abs(favorevoliN - maggioranza)
            else -> kotlin.math.abs(favorevoliN - contrariN)
        }

    /**
     * Quanti voti in più dalla parte perdente sarebbero bastati a ribaltare l'esito.
     * Alla Camera gli astenuti non contano tra i votanti; al Senato sì, e pesano come contrari.
     * Il pareggio respinge.
     */
    val votiPerRibaltare: Int?
        get() {
            if (!haConteggi || esito == Esito.SCONOSCIUTO) return null
            val contro = contrariN + if (ramo == Ramo.SENATO) astenutiN else 0
            return when (esito) {
                Esito.APPROVATA -> (favorevoliN - contro).coerceAtLeast(1)
                else -> (contro - favorevoliN + 1).coerceAtLeast(1)
            }
        }

    /**
     * Votazione decisa per pochi voti: per ribaltarla bastava al massimo il 5% di chi ha votato
     * (minimo 5 voti). Una soglia fissa non funziona: al Senato i votanti sono la metà, e in
     * questa legislatura alla Camera i margini sotto i 10 voti si contano sulle dita.
     */
    val sulFilo: Boolean
        get() {
            val servono = votiPerRibaltare ?: return false
            val votanti = favorevoliN + contrariN + astenutiN
            return servono <= maxOf(SOGLIA_MINIMA_SUL_FILO, (votanti * QUOTA_SUL_FILO).toInt())
        }

    val scartoLabel: String?
        get() = scarto?.let(Testi::scarto)
    val etichette: List<String>
        get() = buildList {
            if (fiducia) add(Testi.etichettaFiducia)
            if (finale) add(Testi.etichettaVotoFinale)
            if (segreta) add(Testi.etichettaSegreta)
            if (sulFilo) add(Testi.etichettaSulFilo)
        }

    companion object {
        const val QUOTA_SUL_FILO = 0.05
        const val SOGLIA_MINIMA_SUL_FILO = 5
    }
}

/**
 * Provvedimento (disegno o proposta di legge) su cui verte una votazione.
 * [dedotto] indica che i dati ufficiali non collegano ancora la votazione all'atto:
 * è stato ricavato dalle altre votazioni della stessa seduta.
 */
@Serializable
data class Atto(
    val numero: String,
    val titolo: String,
    val titoloBreve: String? = null,
    /** Per esempio "Conversione del decreto-legge n. 144/2026". */
    val natura: String? = null,
    val proponenti: String? = null,
    val dedotto: Boolean = false,
    /** Temi del thesaurus TESEO (solo Senato). */
    val temi: List<String> = emptyList(),
) {
    /** Ramo in cui si trova questa lettura dell'atto, dal prefisso del numero ("C. 2822-B", "S. 1786"). */
    val ramo: Ramo get() = if (numero.startsWith("S")) Ramo.SENATO else Ramo.CAMERA

    /** Numero senza ramo: "2822-B", "1786". */
    val numeroSemplice: String get() = numero.substringAfter('.').trim()
}

/** Seduta ricostruita raggruppando le votazioni (stessa seduta, stessa data). */
data class Seduta(
    val ramo: Ramo,
    val uri: String,
    val data: String,
    val numero: Int?,
    val votazioni: List<Votazione>,
) {
    val titolo: String
        get() = Testi.seduta(numero) + " · " + Formati.dataEstesa(data)
}

enum class TipoVoto(val etichetta: String, val presente: Boolean) {
    FAVOREVOLE(Testi.favorevole, true),
    CONTRARIO(Testi.contrario, true),
    ASTENUTO(Testi.astenuto, true),
    /** Scrutinio segreto alla Camera: partecipazione registrata, scelta no. */
    HA_VOTATO(Testi.haVotatoSegreto, true),
    PRESENTE_NON_VOTANTE(Testi.presenteNonVotante, true),
    PRESIDENTE_DI_TURNO(Testi.presidenteDiTurno, true),
    MISSIONE(Testi.inMissione, false),
    ASSENTE(Testi.assente, false),
    /** "Non ha votato" alla Camera senza sottocategoria riconosciuta. */
    NON_HA_VOTATO(Testi.nonHaVotato, false),
    ;

    /** Voto espresso in chiaro: l'unico su cui ha senso parlare di linea di gruppo. */
    val palese: Boolean get() = this == FAVOREVOLE || this == CONTRARIO || this == ASTENUTO
}

data class VotoIndividuale(
    val parlamentareUri: String,
    val nome: String,
    val gruppo: String,
    val voto: TipoVoto,
)

data class ConteggioVoto(val tipo: TipoVoto, val numero: Int)

data class RipartizioneGruppo(
    val gruppo: String,
    val favorevoli: Int,
    val contrari: Int,
    val astenuti: Int,
    val altri: Int,
    /** In missione/congedo: esclusi dal calcolo della compattezza. */
    val missioni: Int = 0,
    /** Assenti o non votanti senza giustificazione. */
    val nonPartecipanti: Int = 0,
    /** Hanno espresso un voto diverso da quello prevalente nel gruppo. */
    val ribelli: Int = 0,
) {
    val totale: Int get() = favorevoli + contrari + astenuti + altri

    /** Voto prevalente tra favorevole, contrario e astenuto; null se nessuno ha espresso un voto palese. */
    val votoGruppo: TipoVoto?
        get() = listOf(TipoVoto.FAVOREVOLE to favorevoli, TipoVoto.CONTRARIO to contrari, TipoVoto.ASTENUTO to astenuti)
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first

    private val inCarica: Int get() = totale - missioni

    /**
     * Indice di compattezza alla Openpolis: quota dei componenti (esclusi i missionari)
     * che hanno votato come il gruppo. Assenze e voti difformi lo abbassano.
     */
    val compattezza: Double?
        get() {
            val linea = when (votoGruppo) {
                TipoVoto.FAVOREVOLE -> favorevoli
                TipoVoto.CONTRARIO -> contrari
                TipoVoto.ASTENUTO -> astenuti
                else -> return null
            }
            return if (inCarica > 0) linea * 100.0 / inCarica else null
        }

    val nonPartecipantiPct: Double get() = if (inCarica > 0) nonPartecipanti * 100.0 / inCarica else 0.0
    val ribelliPct: Double get() = if (inCarica > 0) ribelli * 100.0 / inCarica else 0.0
    val compattezzaLabel: String get() = compattezza?.let(Formati::percentuale) ?: "—"
    val nonPartecipantiLabel: String get() = "$nonPartecipanti (${Formati.percentuale(nonPartecipantiPct)})"
    val ribelliLabel: String get() = "$ribelli (${Formati.percentuale(ribelliPct)})"
}

data class DettaglioVotazione(
    val voti: List<VotoIndividuale>,
    val ripartizione: List<RipartizioneGruppo>,
    val nota: String?,
)

enum class RuoloGoverno {
    PRESIDENTE,
    VICEPRESIDENTE,
    MINISTRO,
    MINISTRO_SENZA_PORTAFOGLIO,
}

/** Date ISO; [fine] è null per il governo in carica. */
data class Governo(
    val uri: String,
    /** "I Governo Meloni". */
    val nome: String,
    val inizio: String?,
    val fine: String?,
) {
    val inCarica: Boolean get() = fine == null

    /** "dal 21 ottobre 2022", "12 febbraio 2021 – 21 ottobre 2022". */
    val periodo: String?
        get() {
            val da = inizio?.let(Formati::dataEstesa) ?: return null
            return if (fine != null) "$da – ${Formati.dataEstesa(fine)}" else Testi.dal(da)
        }
}

data class MembroGoverno(
    val ruolo: RuoloGoverno,
    val nome: String,
    val cognome: String,
    /** "Ministro della Difesa". */
    val incarico: String,
    /** Funzioni aggiuntive: "con funzione di Segretario del Consiglio dei ministri". */
    val delega: String? = null,
    /** Date ISO; [al] è null se è rimasto fino alla fine del governo (o è ancora in carica). */
    val dal: String? = null,
    val al: String? = null,
    val interim: Boolean = false,
    /** La scheda da deputato nella legislatura, se lo è. */
    val deputato: Parlamentare? = null,
) {
    val nomeCompleto: String get() = "$nome $cognome".trim()
    val iniziali: String
        get() = "${nome.firstOrNull() ?: ""}${cognome.firstOrNull() ?: ""}".uppercase()
    val fotoUrl: String? get() = deputato?.fotoUrl

    /** "dal 23 ottobre 2022 al 4 settembre 2024". */
    val periodo: String?
        get() {
            val da = dal?.let(Formati::dataEstesa) ?: return null
            return if (al != null) Testi.dalAl(da, Formati.dataEstesa(al)) else Testi.dal(da)
        }
}

data class ComposizioneGoverno(
    val governo: Governo,
    val membri: List<MembroGoverno>,
) {
    /**
     * Ha lasciato prima della fine del governo. Per i governi conclusi la Camera a volte mette la
     * data di fine a tutti (II Conte), a volte solo a chi è uscito (Draghi): chi arriva fino
     * alla fine, con qualche giorno di tolleranza, è rimasto.
     */
    private fun uscito(m: MembroGoverno): Boolean {
        val al = m.al ?: return false
        val fine = governo.fine ?: return true
        return (Formati.giorniTra(al, fine) ?: 0) > TOLLERANZA_GIORNI
    }

    private val attuali: List<MembroGoverno> get() = membri.filter { !uscito(it) }

    val presidente: MembroGoverno? get() = attuali.firstOrNull { it.ruolo == RuoloGoverno.PRESIDENTE }
    val vicepresidenti: List<MembroGoverno> get() = attuali.filter { it.ruolo == RuoloGoverno.VICEPRESIDENTE }
    val ministri: List<MembroGoverno> get() = attuali.filter { it.ruolo == RuoloGoverno.MINISTRO }
    val senzaPortafoglio: List<MembroGoverno>
        get() = attuali.filter { it.ruolo == RuoloGoverno.MINISTRO_SENZA_PORTAFOGLIO }

    /**
     * Chi ha lasciato il governo prima della sua fine, con l'ultimo incarico, dal più recente.
     * Restano fuori i cambi di nome dei ministeri e gli interim di chi è ancora nel governo.
     */
    val avvicendamenti: List<MembroGoverno>
        get() {
            val ancoraDentro = attuali.mapTo(mutableSetOf()) { it.nomeCompleto }
            return membri.filter { uscito(it) && it.nomeCompleto !in ancoraDentro }
                .groupBy { it.nomeCompleto }
                .map { (_, incarichi) -> incarichi.maxBy { it.al.orEmpty() } }
                .sortedByDescending { it.al }
        }

    private companion object {
        const val TOLLERANZA_GIORNI = 3
    }
}

/** Totali di un ramo in una legislatura, per il confronto tra legislature. */
data class StatisticheAula(
    val votazioni: Int,
    /** null se non registrate nei dati (la Camera non le marca nelle prime legislature). */
    val fiducie: Int?,
    val finali: Int,
    val respinte: Int,
    /** Votazioni con esito noto: il denominatore della quota di respinte. */
    val conEsito: Int,
    /** Parlamentari che hanno aderito ad almeno due gruppi. */
    val cambiDiGruppo: Int,
) {
    val quotaRespinte: Double? get() = if (conEsito > 0) respinte * 100.0 / conEsito else null
}

/** Leggi approvate definitivamente in una legislatura, per natura. */
data class StatisticheLeggi(
    val ordinarie: Int,
    val conversioni: Int,
    val bilancio: Int,
    val costituzionali: Int,
    val delGoverno: Int,
) {
    val totale: Int get() = ordinarie + conversioni + bilancio + costituzionali
    val quotaGoverno: Double? get() = if (totale > 0) delGoverno * 100.0 / totale else null
}

/** Legislatura della Repubblica. Date ISO; [fine] è null per quella in corso. */
data class Legislatura(
    val numero: Int,
    val inizio: String? = null,
    val fine: String? = null,
) {
    val romano: String get() = numeroRomano(numero)

    /** Giorni dall'inizio alla fine, o a oggi se è in corso. */
    val giorni: Int? get() = inizio?.let { Formati.giorniTra(it, fine ?: Formati.oggi()) }
    val etichetta: String get() = Testi.legislatura(romano)
    val conclusa: Boolean get() = fine != null

    /** "2018–2022", "dal 2022"; null se le date non sono note. */
    val periodo: String?
        get() {
            val da = inizio?.take(4) ?: return null
            return if (fine != null) "$da–${fine.take(4)}" else Testi.dal(da)
        }

    companion object {
        private val CIFRE = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
            50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
        )

        /** 19 → "XIX". */
        fun numeroRomano(n: Int): String {
            var resto = n
            return buildString {
                for ((valore, simbolo) in CIFRE) {
                    while (resto >= valore) {
                        append(simbolo)
                        resto -= valore
                    }
                }
            }
        }
    }
}

@Serializable
data class Parlamentare(
    val uri: String,
    val ramo: Ramo,
    val nome: String,
    val cognome: String,
    val gruppo: String,
    val fotoUrl: String?,
    /** Quante volte ha cambiato gruppo in questa legislatura. */
    val cambiDiGruppo: Int = 0,
) {
    val nomeCompleto: String get() = "$nome $cognome".trim()
    val iniziali: String
        get() = "${nome.firstOrNull() ?: ""}${cognome.firstOrNull() ?: ""}".uppercase()
}

/**
 * Gruppo parlamentare di un ramo, ricostruito dai parlamentari in carica: niente query in più,
 * e la composizione coincide con quella dell'elenco dei parlamentari.
 */
data class GruppoParlamentare(
    val ramo: Ramo,
    val nome: String,
    val membri: List<Parlamentare>,
    /** Parlamentari in carica nel ramo: il denominatore della quota. */
    val seggiTotali: Int,
) {
    /** Colore del gruppo come 0xRRGGBB, uguale nelle due app e stabile nel tempo. */
    val colore: Long get() = ColoriGruppi.colore(nome)

    val seggi: Int get() = membri.size
    val quota: Double get() = if (seggiTotali > 0) seggi * 100.0 / seggiTotali else 0.0
    val quotaLabel: String get() = Formati.percentuale(quota)

    /** Membri che in questa legislatura hanno cambiato gruppo almeno una volta. */
    val conCambi: Int get() = membri.count { it.cambiDiGruppo > 0 }
}

/**
 * Un colore fisso per gruppo, ispirato a quello del partito. I nomi cambiano tra i rami e nel
 * tempo ("LEGA - SALVINI PREMIER" alla Camera, "Lega Salvini Premier - Partito Sardo d'Azione"
 * al Senato), quindi si riconoscono per parole chiave. L'ordine conta: "lega" va provato prima
 * di "azione", che compare anche nel nome del gruppo della Lega al Senato.
 *
 * Toni scelti con un validatore di palette (distanza percettiva OKLab): a vista normale ogni
 * coppia resta sopra ΔE 12, tranne i due verdi di Lega e AVS che sono voluti. Con 11 colori la
 * separazione per i daltonici non si ottiene per tutte le coppie: nella torta l'identità è
 * affidata anche allo stacco tra le fette e alla legenda con i nomi.
 */
object ColoriGruppi {
    private val perParolaChiave: List<Pair<String, Long>> = listOf(
        "fratelli d'italia" to 0x1F3A93,
        "partito democratico" to 0xB71C1C,
        "lega" to 0x1B7F3B,
        "movimento 5 stelle" to 0xE0A800,
        "forza italia" to 0x3B8FD9,
        "alleanza verdi" to 0x689F38,
        "noi moderati" to 0xE65100,
        "italia viva" to 0xF48FB1,
        "azione" to 0x7E57C2,
        "autonomie" to 0x4DD0E1,
        "misto" to 0xABABAB,
    )

    /** Per i gruppi non in tabella: scelto dal nome, quindi sempre lo stesso per lo stesso gruppo. */
    private val diRiserva: List<Long> = listOf(0x6D4C41, 0x5C6BC0, 0x26A69A, 0xAB47BC, 0x8D6E63, 0x546E7A)

    const val SENZA_GRUPPO: Long = 0xDADADA

    fun colore(nome: String): Long {
        if (nome == Testi.senzaGruppo) return SENZA_GRUPPO
        // lowercase() copre anche "MoVimento 5 Stelle" del Senato.
        val n = nome.lowercase()
        perParolaChiave.firstOrNull { (chiave, _) -> chiave in n }?.let { return it.second }
        return diRiserva[(nome.hashCode() and Int.MAX_VALUE) % diRiserva.size]
    }
}

/**
 * Partecipazione al voto, con la formula di Openpolis: presenze = voti espressi +
 * presenze senza voto (+ turni di presidenza alla Camera); le missioni sono una
 * categoria a sé e non contano come assenze.
 */
data class Presenze(
    val votazioniTotali: Int,
    val presenze: Int,
    val missioni: Int,
    val assenze: Int,
    val favorevoli: Int,
    val contrari: Int,
    val astenuti: Int,
    val nota: String?,
) {
    private fun pct(n: Int): Double = if (votazioniTotali > 0) n * 100.0 / votazioniTotali else 0.0
    val presenzePct: Double get() = pct(presenze)
    val missioniPct: Double get() = pct(missioni)
    val assenzePct: Double get() = pct(assenze)
    val presenzeLabel: String get() = Formati.percentuale(presenzePct)
    val missioniLabel: String get() = Formati.percentuale(missioniPct)
    val assenzeLabel: String get() = Formati.percentuale(assenzePct)
}

/**
 * Chi è il parlamentare, oltre al nome: i due rami pubblicano campi diversi (il titolo di
 * studio e la lista solo la Camera), quindi è tutto facoltativo.
 */
data class ProfiloParlamentare(
    /** Data ISO. */
    val nascita: String? = null,
    /** "Bassano del Grappa (Vicenza)". */
    val luogoNascita: String? = null,
    val titoloDiStudio: String? = null,
    val professione: String? = null,
    /** Circoscrizione o collegio: "Liguria - P01", "Emilia-Romagna - U04 (Ravenna)". */
    val elezione: String? = null,
    /** "Proporzionale", "Maggioritario", "A vita"… */
    val tipoElezione: String? = null,
    val lista: String? = null,
    /** Scheda personale sul sito ufficiale del ramo. */
    val sito: String? = null,
) {
    val nascitaEstesa: String?
        get() = listOfNotNull(luogoNascita, nascita?.let(Formati::dataEstesa))
            .joinToString(", ").ifBlank { null }

    val elezioneEstesa: String?
        get() = listOfNotNull(elezione, tipoElezione).joinToString(" · ").ifBlank { null }

    /** Le voci da mostrare, etichetta → valore, solo quelle presenti. */
    val voci: List<VoceProfilo>
        get() = listOf(
            VoceProfilo(Testi.nascita, nascitaEstesa),
            VoceProfilo(Testi.titoloDiStudio, titoloDiStudio),
            VoceProfilo(Testi.professione, professione),
            VoceProfilo(Testi.elezione, elezioneEstesa),
            VoceProfilo(Testi.lista, lista),
        ).filter { !it.valore.isNullOrBlank() }
}

data class VoceProfilo(val etichetta: String, val valore: String?)

/** Appartenenza a un gruppo parlamentare. Date ISO; [al] null se ancora in corso. */
data class Adesione(val gruppo: String, val dal: String, val al: String?) {
    val periodo: String
        get() = Formati.dataEstesa(dal) + " – " + (al?.let(Formati::dataEstesa) ?: Testi.oggi)
}

/** Un passaggio dell'iter di un provvedimento: "C.2822" approvato, poi "S.1971"… */
data class FaseIter(
    val numero: String,
    val ramo: Ramo,
    val stato: String?,
    val data: String?,
) {
    val dataEstesa: String? get() = data?.let(Formati::dataEstesa)

    /** Lo stato per esteso: il Senato lo pubblica abbreviato ("appr. con modificaz"). */
    val statoEsteso: String?
        get() = stato?.let { grezzo ->
            ABBREVIAZIONI.fold(grezzo.trim()) { t, (sigla, esteso) -> t.replace(sigla, esteso, ignoreCase = true) }
                .replaceFirstChar { it.uppercase() }
        }

    private companion object {
        val ABBREVIAZIONI = listOf(
            "appr. def. non pubbl" to "approvato definitivamente, non ancora pubblicato",
            "appr. con modificaz" to "approvato con modificazioni",
            "appr. def." to "approvato definitivamente",
            "appr." to "approvato",
            "in corso di esame in comm." to "in esame in commissione",
            "comm." to "commissione",
        )
    }
}

/** Relatore di un disegno di legge al Senato, in commissione o in Assemblea. */
data class Relatore(val nome: String, val organo: String, val tipo: String)

/** Tutto ciò che sappiamo di un provvedimento, nei due rami. */
data class SchedaAtto(
    val atto: Atto,
    /** "Progetto di legge", "Disegno di legge", "ordinaria"… */
    val tipo: String?,
    /** "Parlamentare", "Governo", oppure la descrizione del Senato ("Gov. Meloni-I: Ministro…"). */
    val iniziativa: String?,
    val presentatoIl: String?,
    val primoFirmatario: String?,
    val altriFirmatari: List<String>,
    val relatori: List<Relatore>,
    val temi: List<String>,
    val iter: List<FaseIter>,
    /** Votazioni d'Aula sull'atto in tutte le sue letture, dalla più recente. */
    val votazioni: List<Votazione>,
    val nota: String?,
    /** Pagina ufficiale dell'atto sul sito del ramo in cui si trova questa lettura. */
    val sito: String? = null,
) {
    val presentatoIlEsteso: String? get() = presentatoIl?.let(Formati::dataEstesa)
}

/**
 * Le assenze tra chi ha perso sarebbero bastate a ribaltare il risultato: [assenti] membri
 * dei gruppi schierati con la parte perdente non hanno partecipato, ne servivano [servivano].
 */
data class AssenzeDecisive(
    val servivano: Int,
    val assenti: Int,
    /** Gruppo e suoi assenti, dal più numeroso. */
    val perGruppo: List<AssentiGruppo>,
    /** Gruppi le cui sole assenze sarebbero bastate. */
    val decisiviDaSoli: List<String>,
)

data class AssentiGruppo(val gruppo: String, val assenti: Int)

