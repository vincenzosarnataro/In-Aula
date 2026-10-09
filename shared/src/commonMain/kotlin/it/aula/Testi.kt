package it.aula

import it.aula.model.AssenzeDecisive
import it.aula.model.ConteggioVoto
import it.aula.model.Ramo
import it.aula.model.Votazione

/**
 * Tutti i testi dell'interfaccia, condivisi da Android (Compose) e iOS (SwiftUI): una frase si
 * cambia qui e cambia su entrambe le app. Le frasi con numeri sono funzioni, così singolare e
 * plurale si decidono una volta sola. Da Swift: `Testi.shared.riprova`, o `testi.riprova`.
 *
 * Restano fuori le traduzioni delle sigle parlamentari ([it.aula.model.DescrizioneVoto]) e i
 * nomi dei mesi ([it.aula.model.Formati]): sono regole di formattazione, non testi dell'interfaccia.
 */
object Testi {

    // ---------------------------------------------------------------- Generali

    const val riprova = "Riprova"
    const val erroreTitolo = "Non riesco a raggiungere i dati"
    const val erroreRete = "Errore di rete. Riprova."
    const val indietro = "Indietro"
    const val cancellaRicerca = "Cancella ricerca"
    const val rimuoviFiltro = "Rimuovi filtro"
    const val selezionato = "Selezionato"
    const val temi = "Temi"

    // ---------------------------------------------------------------- Navigazione e tema

    const val aula = "Aula"
    const val parlamentari = "Parlamentari"

    const val tema = "Tema"
    const val temaSistema = "Come il sistema"
    const val temaChiaro = "Chiaro"
    const val temaScuro = "Scuro"
    fun temaAttuale(etichetta: String) = "Tema: ${etichetta.lowercase()}"

    // ---------------------------------------------------------------- Modello

    const val ramo = "Ramo"
    const val camera = "Camera"
    const val senato = "Senato"

    const val approvata = "Approvata"
    const val respinta = "Respinta"

    const val favorevole = "Favorevole"
    const val contrario = "Contrario"
    const val astenuto = "Astenuto"
    const val haVotatoSegreto = "Ha votato (segreto)"
    const val presenteNonVotante = "Presente non votante"
    const val presidenteDiTurno = "Presidente di turno"
    const val inMissione = "In missione/congedo"
    const val assente = "Assente"
    const val nonHaVotato = "Non ha votato"

    const val etichettaFiducia = "Fiducia"
    const val etichettaVotoFinale = "Voto finale"
    const val etichettaSegreta = "Segreta"
    const val etichettaSulFilo = "Sul filo"

    const val oggi = "oggi"
    fun seduta(numero: Int?) = if (numero != null) "Seduta n. $numero" else "Seduta"
    fun scarto(voti: Int) = if (voti == 1) "1 voto di scarto" else "$voti voti di scarto"

    // ---------------------------------------------------------------- Aula

    const val inAula = "In Aula"
    fun sottotitoloAula(ramo: Ramo) = when (ramo) {
        Ramo.CAMERA -> "Le votazioni della Camera"
        Ramo.SENATO -> "Le votazioni del Senato"
    }

    /** Messaggio durante il primo caricamento: solo il Senato è abbastanza lento da meritarlo. */
    fun attesaVotazioni(ramo: Ramo): String? =
        if (ramo == Ramo.SENATO) "Il Senato risponde con calma: un attimo…" else null

    const val cercaVotazioni = "Cerca per atto, oggetto o numero"
    const val votazioniPrecedenti = "Cerca nelle votazioni precedenti"
    fun nessunaVotazione(caricate: Int) = "Nessuna delle $caricate votazioni caricate corrisponde."
    fun votazioniFiltrate(visibili: Int, caricate: Int) = "$visibili su $caricate votazioni caricate"

    const val filtroTutte = "Tutte"
    const val filtroFiducia = "Fiducia"
    const val filtroFinali = "Voti finali"
    const val filtroRespinte = "Respinte"
    const val filtroSulFilo = "Sul filo"

    fun numeroVotazione(numero: Int) = "n. $numero"
    fun conteggioVoti(favorevoli: Int, contrari: Int, astenuti: Int) =
        "$favorevoli sì · $contrari no · $astenuti astenuti"

    // ---------------------------------------------------------------- Dettaglio votazione

    fun titoloVotazione(numero: Int?) = if (numero != null) "Votazione n. $numero" else "Votazione"

    /** "Camera · 8 ottobre 2026 · Seduta n. 721" */
    fun sottotitoloVotazione(v: Votazione) =
        "${v.ramo.etichetta} · ${v.dataEstesa}" + (v.numeroSeduta?.let { " · " + seduta(it) } ?: "")

    fun attesaDettaglio(ramo: Ramo) = when (ramo) {
        Ramo.SENATO -> "Il Senato accetta una richiesta ogni 2 secondi: il dettaglio richiede qualche istante."
        Ramo.CAMERA -> "Carico i voti dei deputati…"
    }

    const val presenti = "Presenti"
    const val favorevoli = "Favorevoli"
    const val contrari = "Contrari"
    const val astenuti = "Astenuti"

    fun presentatoDa(proponenti: String) = "Presentato da $proponenti"
    const val attoDedotto =
        "Dedotto dalle altre votazioni della seduta: i dati ufficiali non collegano ancora questa votazione all'atto."
    const val apriAtto = "Iter e tutte le votazioni sull'atto ›"

    const val assenzeDecisive = "Assenze decisive"
    fun spiegazioneAssenze(assenze: AssenzeDecisive, ramo: Ramo): String {
        val chi = if (ramo == Ramo.SENATO) "senatori" else "deputati"
        return "Per ribaltare l'esito servivano ${assenze.servivano} voti. Tra i gruppi che hanno votato " +
            "con la parte perdente mancavano ${assenze.assenti} $chi, missioni escluse."
    }
    fun assentiPerGruppo(assenze: AssenzeDecisive) =
        assenze.perGruppo.joinToString(" · ") { "${it.gruppo} ${it.assenti}" }
    fun decisiviDaSoli(gruppi: List<String>) = "Sarebbero bastati da soli gli assenti di ${gruppi.joinToString(", ")}."

    const val distribuzioneVoto = "Distribuzione del voto"
    const val vista = "Vista"
    const val perVoto = "Per voto"
    const val perGruppo = "Per gruppo"
    fun inEvidenza(gruppo: String) = "In evidenza: $gruppo"
    /** Descrizione dell'emiciclo per lo screen reader: "Favorevole: 180, Contrario: 120…". */
    fun descrizioneEmiciclo(conteggi: List<ConteggioVoto>) =
        conteggi.joinToString(", ") { "${it.tipo.etichetta}: ${it.numero}" }

    const val comeHannoVotatoIGruppi = "Come hanno votato i gruppi"
    const val spiegazioneCompattezza = "Compattezza: quota del gruppo, missioni escluse, che ha votato come la " +
        "maggioranza dei suoi componenti. Tocca un gruppo per evidenziarlo."
    const val ribelli = "Ribelli"
    const val ribelle = "Ribelle"
    const val nonPartecipanti = "Non partecipanti"
    const val compattezza = "Compattezza"
    fun comeHannoVotato(voti: Int) = "Come hanno votato · $voti"
    fun soloRibelli(ribelli: Int) = "Solo ribelli · $ribelli"

    const val notaScrutinioSegreto = "Scrutinio segreto: è registrata la partecipazione al voto, non la scelta del singolo."
    const val notaSegretaSenato =
        "Voto segreto o verifica del numero legale: il Senato registra solo presenze e missioni, non la scelta del singolo."
    const val notaAssentiSenato = "Il Senato non registra l'assente semplice: chi non compare qui non era in Aula."

    // ---------------------------------------------------------------- Parlamentari

    const val cercaParlamentari = "Cerca per nome o gruppo"
    fun inCarica(n: Int) = "$n in carica"
    fun hannoCambiatoGruppo(n: Int) = "Hanno cambiato gruppo · $n"
    fun cambiDiGruppo(n: Int) = if (n == 1) "1 cambio di gruppo" else "$n cambi di gruppo"
    fun attesaParlamentari(ramo: Ramo) = when (ramo) {
        Ramo.SENATO -> "Carico i senatori in carica…"
        Ramo.CAMERA -> "Carico i deputati in carica…"
    }

    // ---------------------------------------------------------------- Scheda parlamentare

    fun attesaPresenze(ramo: Ramo) = when (ramo) {
        Ramo.SENATO -> "Il Senato accetta una richiesta ogni 2 secondi: servono circa 12 secondi."
        Ramo.CAMERA -> "Conto le presenze su tutte le votazioni della legislatura…"
    }

    const val partecipazioneAlVoto = "Partecipazione al voto"
    fun presenzeSu(votazioni: Int) = "presenze su $votazioni votazioni"
    const val presenze = "Presenze"
    const val missioni = "Missioni"
    const val assenze = "Assenze"
    const val votiEspressi = "Voti espressi"
    const val formulaPresenze =
        "Formula Openpolis: le missioni non contano come assenze. Scarto tipico rispetto a Openpolis entro un punto percentuale."
    fun votiNonClassificati(n: Int) = "$n voti \"Non ha votato\" senza causa indicata alla fonte."
    const val mandatoNonRicostruibile = "Mandato non ricostruibile dai dati: le assenze non sono calcolabili."

    fun storiaGruppi(cambi: Int) = when (cambi) {
        0 -> "Gruppo parlamentare"
        1 -> "Gruppi · 1 cambio in questa legislatura"
        else -> "Gruppi · $cambi cambi in questa legislatura"
    }

    // ---------------------------------------------------------------- Scheda atto

    const val provvedimento = "Provvedimento"
    const val apriSulSito = "Apri sul sito ufficiale"
    const val attesaSchedaAtto =
        "Ricostruisco l'iter e le votazioni nei due rami: il Senato accetta una richiesta ogni 2 secondi."
    const val iter = "Iter"
    const val dettagli = "Dettagli"
    const val iniziativa = "Iniziativa"
    const val presentatoIl = "Presentato il"
    const val primoFirmatario = "Primo firmatario"
    fun altriFirmatari(n: Int) = "Altri firmatari · $n"
    const val mostraTutti = "Mostra tutti"
    const val relatori = "Relatori"
    fun votazioniInAula(n: Int) = "Votazioni in Aula · $n"
    fun respinteSulFilo(respinte: Int, sulFilo: Int) = "$respinte respinte · $sulFilo sul filo"
    fun disegnoDiLegge(natura: String) = "Disegno di legge ($natura)"
    fun nessunDatoSullAtto(numero: String) = "Nessun dato su $numero negli open data."
    const val notaAttiDedotti =
        "Alcune votazioni recenti sono attribuite all'atto per deduzione dalle altre votazioni della seduta."

    // ---------------------------------------------------------------- Errori delle fonti

    fun querySenatoTroppoLunga(byte: Int, massimo: Int) =
        "Query troppo lunga per il Senato: $byte byte di request-URI (massimo $massimo)."
    const val senatoBloccato = "Il Senato ha bloccato temporaneamente le richieste (403). Riprova tra qualche minuto."
    fun richiestaRifiutata(status: Int) = "Richiesta SPARQL rifiutata ($status)."
    fun endpointNonRisponde(tentativi: Int) = "L'endpoint non risponde dopo $tentativi tentativi."
}
