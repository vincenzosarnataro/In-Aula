package it.aula.data

import it.aula.Testi
import it.aula.data.sparql.Riga
import it.aula.data.sparql.SparqlClient
import it.aula.model.Atto
import it.aula.model.Adesione
import it.aula.model.FaseIter
import it.aula.model.Relatore
import it.aula.model.ProfiloParlamentare
import it.aula.model.SchedaAtto
import it.aula.model.DescrizioneVoto
import it.aula.model.DettaglioVotazione
import it.aula.model.Esito
import it.aula.model.Formati
import it.aula.model.Parlamentare
import it.aula.model.Presenze
import it.aula.model.Ramo
import it.aula.model.RipartizioneGruppo
import it.aula.model.Seduta
import it.aula.model.TipoVoto
import it.aula.model.Votazione
import it.aula.model.VotoIndividuale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Unico punto di accesso ai dati. Tutto avviene sul device: le query sono pensate per
 * restare leggere (una votazione, un parlamentare, una pagina di lista alla volta).
 * Cache in memoria per ciò che cambia di rado (legislatura, elenchi, gruppi).
 */
class ParlamentoRepository(private val sparql: SparqlClient = SparqlClient()) {

    private val mutex = Mutex()
    private var legislatura: Int? = null
    private val cacheParlamentari = mutableMapOf<Ramo, List<Parlamentare>>()
    private val cacheDettagli = mutableMapOf<String, DettaglioVotazione>()
    private val cacheAdesioni = mutableMapOf<Ramo, Map<String, List<Adesione>>>()
    private val cacheProfili = mutableMapOf<String, ProfiloParlamentare>()

    /** Parlamentari e Gruppi chiedono lo stesso elenco all'avvio: lo si carica una volta sola. */
    private val caricamentoParlamentari = Ramo.entries.associateWith { Mutex() }

    suspend fun legislaturaCorrente(): Int = mutex.withLock {
        legislatura ?: run {
            val n = runCatching {
                sparql.camera(CameraQuery.legislaturaCorrente).firstOrNull()?.get("s")
                    ?.substringAfterLast("repubblica_")?.toIntOrNull()
            }.getOrNull() ?: LEGISLATURA_DI_RISERVA
            legislatura = n
            n
        }
    }

    // ---------------------------------------------------------------- Votazioni e sedute

    suspend fun votazioni(ramo: Ramo, limit: Int = 120, offset: Int = 0): List<Votazione> {
        val leg = legislaturaCorrente()
        return when (ramo) {
            Ramo.CAMERA -> conAttiCamera(
                sparql.camera(CameraQuery.votazioni(leg, limit, offset)).map(::votazioneCamera).distinctBy { it.uri },
                leg,
            )
            Ramo.SENATO -> conDdlSenato(
                sparql.senato(SenatoQuery.votazioni(leg, limit, offset)).map(::votazioneSenato).distinctBy { it.uri },
            )
        }
    }

    /**
     * Completa le votazioni Camera senza ocd:rif_attoCamera con l'atto citato nella
     * descrizione. Se la query fallisce si tiene la lista com'è: l'atto è un di più.
     */
    private suspend fun conAttiCamera(votazioni: List<Votazione>, leg: Int): List<Votazione> {
        val citati = votazioni.filter { it.atto == null }
            .associate { it.uri to DescrizioneVoto.numeroAttoCamera(it.descrizione) }
            .filterValues { it != null }
            .mapValues { it.value!! }
        if (citati.isEmpty()) return votazioni
        // "3083-A" è il testo della commissione: nei dati l'atto è spesso solo "3083".
        val candidati = citati.values.flatMap { listOf(it, it.substringBefore('-')) }.toSet()
        val titoli = runCatching {
            sparql.camera(CameraQuery.attiPerNumero(leg, candidati))
                .associate { it["id"].orEmpty() to it["titolo"].orEmpty() }
        }.getOrElse { return votazioni }
        return votazioni.map { v ->
            val numero = citati[v.uri] ?: return@map v
            val titolo = titoli[numero] ?: titoli[numero.substringBefore('-')] ?: return@map v
            v.copy(atto = DescrizioneVoto.attoCamera(numero, titolo))
        }
    }

    /** Aggiunge alle votazioni del Senato il disegno di legge principale. */
    private suspend fun conDdlSenato(votazioni: List<Votazione>): List<Votazione> {
        val sedute = votazioni.map { it.sedutaUri }.filter { it.isNotBlank() }.distinct()
        if (sedute.isEmpty()) return votazioni
        val righe = runCatching {
            sedute.chunked(SEDUTE_PER_QUERY).flatMap { sparql.senato(SenatoQuery.ddlDelleSedute(it)) }
        }.getOrElse { return votazioni }
        val scelte = righe.groupBy { it["v"].orEmpty() }.mapValues { (_, r) -> sceltaDdl(r) }
        val temi = temiDei(scelte.values.mapNotNull { it?.get("d") }.toSet())
        return votazioni.map { v ->
            val riga = scelte[v.uri] ?: return@map v
            val atto = attoSenato(riga) ?: return@map v
            v.copy(atto = atto.copy(temi = temi[riga["d"]].orEmpty()))
        }
    }

    /** Temi TESEO per ddl. Facoltativi: se la query fallisce non ci sono temi. */
    private suspend fun temiDei(ddl: Set<String>): Map<String, List<String>> {
        if (ddl.isEmpty()) return emptyMap()
        val righe = runCatching {
            ddl.chunked(DDL_PER_QUERY).flatMap { sparql.senato(SenatoQuery.temiDdl(it)) }
        }.getOrElse { return emptyMap() }
        return righe.groupBy({ it["d"].orEmpty() }, { temaLeggibile(it["tema"].orEmpty()) })
            .mapValues { (_, t) -> t.filter { it.isNotBlank() }.distinct().sorted() }
    }

    // ---------------------------------------------------------------- Scheda di un atto

    /**
     * Scheda completa di un provvedimento: dettagli, iter nei due rami (dal Senato, che registra
     * anche le letture Camera), votazioni d'Aula di tutte le letture. Le parti facoltative che
     * non si riescono a caricare restano vuote; senza votazioni né dettagli è un errore.
     */
    suspend fun schedaAtto(atto: Atto): SchedaAtto = coroutineScope {
        val leg = legislaturaCorrente()
        val fase = atto.numero.replace(" ", "")
        val iterRighe = runCatching {
            val fasi = if (atto.ramo == Ramo.CAMERA) listOf(fase, fase.substringBefore('-')) else listOf(fase)
            sparql.senato(SenatoQuery.iter(fasi.distinct(), leg))
        }.getOrDefault(emptyList())
        val iter = iterRighe.distinctBy { it["d"] }.map { r ->
            FaseIter(
                numero = r["fase"].orEmpty().replace(".", ". "),
                ramo = if (r["ramo"] == "S" || r["fase"].orEmpty().startsWith("S")) Ramo.SENATO else Ramo.CAMERA,
                stato = r["stato"]?.takeIf { it.isNotBlank() },
                data = r["data"]?.let(Formati::normalizzaData),
            )
        }.sortedWith(compareBy({ it.data ?: "" }, { it.numero }))

        // Numeri Camera da cercare: quello dell'atto e quelli delle letture Camera nell'iter.
        val basiCamera = buildSet {
            if (atto.ramo == Ramo.CAMERA) add(atto.numeroSemplice.substringBefore('-'))
            iter.filter { it.ramo == Ramo.CAMERA }.forEach { add(it.numero.substringAfter(". ").substringBefore('-')) }
        }
        val ddlSenato = iterRighe.filter { it["fase"].orEmpty().startsWith("S") }.mapNotNull { it["d"] }.distinct()
        val idDdl = iterRighe.firstNotNullOfOrNull { it["id"] }

        val votiCamera = async { basiCamera.flatMap { runCatching { votazioniCameraDellAtto(leg, it) }.getOrDefault(emptyList()) } }
        val votiSenato = async {
            if (idDdl == null) emptyList() else runCatching {
                conDdlSenato(sparql.senato(SenatoQuery.votazioniDelDdl(idDdl)).map(::votazioneSenato).distinctBy { it.uri })
            }.getOrDefault(emptyList())
        }
        val dettagliCamera = async {
            val numeri = buildSet {
                if (atto.ramo == Ramo.CAMERA) { add(atto.numeroSemplice); add(atto.numeroSemplice.substringBefore('-')) }
            }
            if (numeri.isEmpty()) emptyList() else runCatching { sparql.camera(CameraQuery.dettaglioAtto(leg, numeri)) }.getOrDefault(emptyList())
        }
        val relatori = async {
            if (ddlSenato.isEmpty()) emptyList() else runCatching {
                sparql.senato(SenatoQuery.relatori(ddlSenato)).map {
                    Relatore(
                        nome = it["nome"].orEmpty().removePrefix("Sen. ").trim(),
                        organo = it["organo"].orEmpty(),
                        tipo = it["tipo"].orEmpty(),
                    )
                }.distinct()
            }.getOrDefault(emptyList())
        }
        val temi = async { temiDei(ddlSenato.toSet()).values.flatten().distinct().sorted() }

        val camera = dettagliCamera.await()
        // Si preferisce la lettura esatta ("2822-B") alla base ("2822").
        val rigaCamera = camera.firstOrNull { it["id"] == atto.numeroSemplice } ?: camera.firstOrNull()
        val senato = iterRighe.filter { it["fase"].orEmpty().startsWith("S") }
            .maxByOrNull { it["data"].orEmpty() }
        val votazioni = (votiCamera.await() + votiSenato.await())
            .distinctBy { it.uri }
            .sortedWith(compareByDescending<Votazione> { it.data }.thenByDescending { it.numero ?: 0 })

        val firmatari = camera.filter { it["id"] == rigaCamera?.get("id") }
            .mapNotNull { it["firmatario"]?.let(::nomeFirmatario) }
            .distinct()
        val atteso = atto.copy(
            titoloBreve = atto.titoloBreve ?: senato?.get("breve")?.takeIf { it.isNotBlank() },
        )
        if (votazioni.isEmpty() && rigaCamera == null && iter.isEmpty()) {
            throw IllegalStateException(Testi.nessunDatoSullAtto(atto.numero))
        }
        SchedaAtto(
            atto = atteso,
            tipo = rigaCamera?.get("tipo") ?: senato?.get("natura")?.let(Testi::disegnoDiLegge),
            iniziativa = senato?.get("iniziativa")?.takeIf { it.isNotBlank() } ?: rigaCamera?.get("iniziativa"),
            presentatoIl = (rigaCamera?.get("data") ?: senato?.get("presentato"))?.let(Formati::normalizzaData),
            primoFirmatario = rigaCamera?.get("creator")?.let(::nomeFirmatario),
            altriFirmatari = firmatari,
            relatori = relatori.await(),
            temi = (temi.await() + atto.temi).distinct().sorted(),
            iter = iter,
            votazioni = votazioni,
            nota = if (votazioni.any { it.atto?.dedotto == true }) {
                Testi.notaAttiDedotti
            } else {
                null
            },
            sito = when (atto.ramo) {
                Ramo.CAMERA -> "https://www.camera.it/leg$leg/126?leg=$leg&idDocumento=${atto.numeroSemplice}"
                Ramo.SENATO -> idDdl?.let { "https://www.senato.it/leg/$leg/BGT/Schede/Ddliter/$it.htm" }
            },
        )
    }

    /**
     * Votazioni Camera su un atto (tutte le letture con la stessa base, "2822"): si trovano le
     * sedute in cui è citato o collegato, si scaricano tutte le loro votazioni e si attribuisce
     * l'atto con le stesse regole della lista (numero citato, poi deduzione nella seduta).
     */
    private suspend fun votazioniCameraDellAtto(leg: Int, base: String): List<Votazione> {
        val sedute = sparql.camera(CameraQuery.seduteDellAtto(leg, base)).mapNotNull { it["seduta"] }.distinct()
        if (sedute.isEmpty()) return emptyList()
        val tutte = sedute.chunked(SEDUTE_PER_QUERY).flatMap { blocco ->
            sparql.camera(CameraQuery.votazioniDelleSedute(blocco)).map(::votazioneCamera)
        }.distinctBy { it.uri }
        return conAttiCamera(tutte, leg)
            .groupBy { it.sedutaUri }
            .values
            .flatMap(::deduciAtti)
            .filter { it.atto?.numeroSemplice?.substringBefore('-') == base }
    }

    // ---------------------------------------------------------------- Gruppi parlamentari

    /** Storia dei gruppi di un parlamentare nella legislatura corrente, dalla prima adesione. */
    suspend fun storiaGruppi(parlamentare: Parlamentare): List<Adesione> =
        adesioni(parlamentare.ramo)[parlamentare.uri].orEmpty()

    /** Nascita, studi, professione ed elezione. In cache. */
    suspend fun profilo(parlamentare: Parlamentare): ProfiloParlamentare {
        cacheProfili[parlamentare.uri]?.let { return it }
        val profilo = when (parlamentare.ramo) {
            Ramo.CAMERA -> profiloCamera(sparql.camera(CameraQuery.profilo(parlamentare.uri)).firstOrNull())
            Ramo.SENATO -> {
                val leg = legislaturaCorrente()
                profiloSenato(parlamentare.uri, leg, sparql.senato(SenatoQuery.profilo(parlamentare.uri, leg)).firstOrNull())
            }
        }
        cacheProfili[parlamentare.uri] = profilo
        return profilo
    }

    /** Adesioni di tutti i parlamentari di un ramo, già ripulite e unite. In cache. */
    private suspend fun adesioni(ramo: Ramo): Map<String, List<Adesione>> {
        cacheAdesioni[ramo]?.let { return it }
        val leg = legislaturaCorrente()
        val mappa = when (ramo) {
            Ramo.CAMERA -> adesioniCamera(sparql.camera(CameraQuery.adesioniGruppi(leg)))
            Ramo.SENATO -> adesioniSenato(sparql.senato(SenatoQuery.adesioniGruppi(leg)))
        }
        cacheAdesioni[ramo] = mappa
        return mappa
    }

    // ---------------------------------------------------------------- Mappatura


    suspend fun dettaglio(votazione: Votazione): DettaglioVotazione {
        cacheDettagli[votazione.uri]?.let { return it }
        val dettaglio = when (votazione.ramo) {
            Ramo.CAMERA -> dettaglioCamera(votazione)
            Ramo.SENATO -> dettaglioSenato(votazione)
        }
        cacheDettagli[votazione.uri] = dettaglio
        return dettaglio
    }

    private suspend fun dettaglioCamera(votazione: Votazione): DettaglioVotazione {
        val voti = sparql.camera(CameraQuery.votiIndividuali(votazione.uri))
            .map { r ->
                VotoIndividuale(
                    parlamentareUri = r["dep"].orEmpty(),
                    nome = togliLegislatura(r["label"].orEmpty()),
                    gruppo = r["sigla"].orEmpty().ifBlank { "—" },
                    voto = tipoVotoCamera(r["type"].orEmpty(), r["descr"]),
                )
            }
            .distinctBy { it.parlamentareUri }
            .sortedBy { it.nome }
        val nota = if (votazione.segreta) {
            Testi.notaScrutinioSegreto
        } else {
            null
        }
        return DettaglioVotazione(voti, ripartisci(voti), nota)
    }

    private suspend fun dettaglioSenato(votazione: Votazione): DettaglioVotazione = coroutineScope {
        // Le richieste al Senato passano comunque in fila (2s l'una) dentro SparqlClient.
        val gruppi = async {
            runCatching { mappaGruppiSenato(sparql.senato(SenatoQuery.gruppiAllaData(votazione.data))) }
                .getOrDefault(emptyMap())
        }
        val perCategoria = SenatoQuery.categorie.map { prop ->
            async { prop to sparql.senato(SenatoQuery.votiCategoria(votazione.uri, prop)) }
        }
        val mappa = gruppi.await()
        val voti = perCategoria.flatMap { it.await().let { (prop, righe) -> righe.map { prop to it } } }
            .map { (prop, r) ->
                val uri = r["sen"].orEmpty()
                VotoIndividuale(
                    parlamentareUri = uri,
                    nome = "${r["nome"].orEmpty()} ${r["cognome"].orEmpty()}".trim(),
                    gruppo = mappa[uri] ?: "—",
                    voto = tipoVotoSenato(prop),
                )
            }
            .distinctBy { it.parlamentareUri }
            .sortedBy { it.nome }
        val segretaONumeroLegale = votazione.segreta ||
            votazione.titolo.contains("numero legale", ignoreCase = true)
        val nota = if (segretaONumeroLegale) {
            Testi.notaSegretaSenato
        } else {
            Testi.notaAssentiSenato
        }
        DettaglioVotazione(voti, ripartisci(voti), nota)
    }

    // ---------------------------------------------------------------- Parlamentari

    suspend fun parlamentari(ramo: Ramo, forza: Boolean = false): List<Parlamentare> =
        caricamentoParlamentari.getValue(ramo).withLock { caricaParlamentari(ramo, forza) }

    private suspend fun caricaParlamentari(ramo: Ramo, forza: Boolean): List<Parlamentare> {
        if (!forza) cacheParlamentari[ramo]?.let { return it }
        val leg = legislaturaCorrente()
        val elenco = when (ramo) {
            Ramo.CAMERA -> coroutineScope {
                val gruppi = async {
                    runCatching { gruppiDeputatiAttuali(sparql.camera(CameraQuery.adesioniGruppi(leg))) }
                        .getOrDefault(emptyMap())
                }
                val righe = sparql.camera(CameraQuery.deputatiInCarica(leg))
                val mappa = gruppi.await()
                righe.map { parlamentare(it, Ramo.CAMERA, mappa) }
            }
            Ramo.SENATO -> {
                val righe = sparql.senato(SenatoQuery.senatoriInCarica(leg))
                val mappa = runCatching { mappaGruppiSenato(sparql.senato(SenatoQuery.gruppiAttuali)) }
                    .getOrDefault(emptyMap())
                righe.map { parlamentare(it, Ramo.SENATO, mappa) }
            }
        }.distinctBy { it.uri }
        // Cambi di gruppo: facoltativi, l'elenco resta valido anche senza.
        val storie = runCatching { adesioni(ramo) }.getOrDefault(emptyMap())
        val conCambi = elenco.map { p ->
            p.copy(cambiDiGruppo = ((storie[p.uri]?.size ?: 1) - 1).coerceAtLeast(0))
        }
        cacheParlamentari[ramo] = conCambi
        return conCambi
    }

    suspend fun presenze(parlamentare: Parlamentare): Presenze = when (parlamentare.ramo) {
        Ramo.CAMERA -> presenzeCamera(parlamentare.uri)
        Ramo.SENATO -> presenzeSenato(parlamentare.uri)
    }

    private suspend fun presenzeCamera(uri: String): Presenze {
        val conteggi = mutableMapOf<TipoVoto, Int>()
        for (r in sparql.camera(CameraQuery.presenze(uri))) {
            val n = r["n"]?.toIntOrNull() ?: 0
            val tipo = tipoVotoCamera(r["type"].orEmpty(), r["descr"])
            conteggi[tipo] = (conteggi[tipo] ?: 0) + n
        }
        val totale = conteggi.values.sum()
        val presenze = conteggi.filterKeys { it.presente }.values.sum()
        val missioni = conteggi[TipoVoto.MISSIONE] ?: 0
        // "Non ha votato" senza sottocategoria nota resta fuori dalle assenze: non attribuiamo
        // un'assenza che il dato non afferma.
        val nonClassificati = conteggi[TipoVoto.NON_HA_VOTATO] ?: 0
        return Presenze(
            votazioniTotali = totale,
            presenze = presenze,
            missioni = missioni,
            assenze = conteggi[TipoVoto.ASSENTE] ?: 0,
            favorevoli = conteggi[TipoVoto.FAVOREVOLE] ?: 0,
            contrari = conteggi[TipoVoto.CONTRARIO] ?: 0,
            astenuti = conteggi[TipoVoto.ASTENUTO] ?: 0,
            nota = if (nonClassificati > 0) Testi.votiNonClassificati(nonClassificati) else null,
        )
    }

    private suspend fun presenzeSenato(uri: String): Presenze = coroutineScope {
        val leg = legislaturaCorrente()
        val conteggi = SenatoQuery.categorie.map { prop ->
            async {
                prop to (sparql.senato(SenatoQuery.presenzeCategoria(uri, prop, leg))
                    .firstOrNull()?.get("n")?.toIntOrNull() ?: 0)
            }
        }.associate { it.await() }
        val denominatore = runCatching {
            sparql.senato(SenatoQuery.votazioniNelMandato(uri, leg)).firstOrNull()?.get("n")?.toIntOrNull()
        }.getOrNull() ?: 0

        val registrati = conteggi.values.sum()
        val missioni = conteggi["inCongedoMissione"] ?: 0
        // Se il periodo di mandato non spiega tutti i voti registrati, meglio niente
        // percentuale che un'assenza negativa: si usa il totale registrato.
        val usabile = denominatore >= registrati && denominatore > 0
        Presenze(
            votazioniTotali = if (usabile) denominatore else registrati,
            presenze = registrati - missioni,
            missioni = missioni,
            assenze = if (usabile) denominatore - registrati else 0,
            favorevoli = conteggi["favorevole"] ?: 0,
            contrari = conteggi["contrario"] ?: 0,
            astenuti = conteggi["astenuto"] ?: 0,
            nota = if (usabile) null else Testi.mandatoNonRicostruibile,
        )
    }

    private fun votazioneCamera(r: Riga): Votazione {
        val sedutaUri = r["rif_seduta"].orEmpty()
        return Votazione(
            uri = r["s"].orEmpty(),
            ramo = Ramo.CAMERA,
            data = Formati.normalizzaData(r["date"].orEmpty()),
            numero = numeriDaUri(r["s"].orEmpty())?.second,
            titolo = r["title"].orEmpty().ifBlank { r["label"].orEmpty() },
            descrizione = r["description"].orEmpty(),
            esito = when (r["approvato"]) {
                "1" -> Esito.APPROVATA
                "0" -> Esito.RESPINTA
                else -> Esito.SCONOSCIUTO
            },
            esitoGrezzo = r["approvato"].orEmpty(),
            favorevoli = r["favorevoli"]?.toIntOrNull(),
            contrari = r["contrari"]?.toIntOrNull(),
            astenuti = r["astenuti"]?.toIntOrNull(),
            presenti = r["presenti"]?.toIntOrNull(),
            maggioranza = r["maggioranza"]?.toIntOrNull(),
            fiducia = r["richiestaFiducia"] == "1",
            finale = r["votazioneFinale"] == "1",
            segreta = r["votazioneSegreta"] == "1",
            sedutaUri = sedutaUri,
            numeroSeduta = numeriDaUri(r["s"].orEmpty())?.first,
            atto = r["attoId"]?.takeIf { it.isNotBlank() }?.let { id ->
                DescrizioneVoto.attoCamera(id, r["attoTitolo"].orEmpty())
            },
        )
    }

    private fun votazioneSenato(r: Riga): Votazione {
        val label = r["label"].orEmpty()
        val tipo = r["tipo"].orEmpty()
        val esito = r["esito"].orEmpty()
        return Votazione(
            uri = r["v"].orEmpty(),
            ramo = Ramo.SENATO,
            data = Formati.normalizzaData(r["date"].orEmpty()),
            numero = r["numero"]?.toIntOrNull(),
            titolo = label,
            descrizione = tipo,
            esito = when {
                esito.contains("approv", ignoreCase = true) -> Esito.APPROVATA
                esito.contains("respint", ignoreCase = true) -> Esito.RESPINTA
                else -> Esito.SCONOSCIUTO
            },
            esitoGrezzo = esito,
            favorevoli = r["favorevoli"]?.toIntOrNull(),
            contrari = r["contrari"]?.toIntOrNull(),
            astenuti = r["astenuti"]?.toIntOrNull(),
            presenti = r["presenti"]?.toIntOrNull(),
            maggioranza = r["maggioranza"]?.toIntOrNull(),
            // Il tipo semantico al Senato non è in osr:tipoVotazione (che è la modalità):
            // va letto dall'etichetta.
            fiducia = label.contains("fiducia", ignoreCase = true) &&
                !label.contains("sfiducia", ignoreCase = true),
            finale = label.contains("finale", ignoreCase = true),
            segreta = tipo.contains("segret", ignoreCase = true),
            sedutaUri = r["s"].orEmpty(),
            numeroSeduta = numeriDaUri(r["v"].orEmpty())?.first,
        )
    }

    private fun parlamentare(r: Riga, ramo: Ramo, gruppi: Map<String, String>): Parlamentare {
        val uri = r["s"].orEmpty()
        return Parlamentare(
            uri = uri,
            ramo = ramo,
            nome = r["nome"].orEmpty().capitalizzaParole(),
            cognome = r["cognome"].orEmpty().capitalizzaParole(),
            gruppo = gruppi[uri].orEmpty(),
            fotoUrl = when (ramo) {
                Ramo.CAMERA -> r["foto"]?.takeIf { it.isNotBlank() }?.let(::fotoCamera)
                // Le foto di senato.it stanno dietro una challenge anti-bot (AWS WAF) che solo un
                // browser supera: l'app riceverebbe sempre una risposta vuota. Niente URL, quindi
                // niente richiesta destinata a fallire: l'avatar mostra subito le iniziali.
                Ramo.SENATO -> null
            },
        )
    }

    companion object {
        const val LEGISLATURA_DI_RISERVA = 19

        internal fun tipoVotoCamera(tipo: String, descrizione: String?): TipoVoto = when (tipo) {
            "Favorevole" -> TipoVoto.FAVOREVOLE
            "Contrario" -> TipoVoto.CONTRARIO
            "Astensione" -> TipoVoto.ASTENUTO
            "Ha votato" -> TipoVoto.HA_VOTATO
            else -> when (descrizione) {
                "In missione" -> TipoVoto.MISSIONE
                "Presidente di turno" -> TipoVoto.PRESIDENTE_DI_TURNO
                "Non ha partecipato" -> TipoVoto.ASSENTE
                else -> TipoVoto.NON_HA_VOTATO
            }
        }

        internal fun tipoVotoSenato(proprieta: String): TipoVoto = when (proprieta) {
            "favorevole" -> TipoVoto.FAVOREVOLE
            "contrario" -> TipoVoto.CONTRARIO
            "astenuto" -> TipoVoto.ASTENUTO
            "presenteNonVotante" -> TipoVoto.PRESENTE_NON_VOTANTE
            else -> TipoVoto.MISSIONE
        }

        /** Sedute per query ddl del Senato: tiene la request-URI sotto i 2047 byte. */
        internal const val SEDUTE_PER_QUERY = 8

        /** URI di ddl per query del Senato (temi, relatori): stesso limite di lunghezza. */
        internal const val DDL_PER_QUERY = 12

        /** "BIGNAMI Galeazzo" → "Galeazzo Bignami". */
        internal fun nomeFirmatario(grezzo: String): String {
            val parole = grezzo.trim().split(Regex("\\s+"))
            val cognome = parole.takeWhile { it == it.uppercase() && it.any(Char::isLetter) }
            val nome = parole.drop(cognome.size)
            val cognomeBello = cognome.joinToString(" ") { it.lowercase().capitalizzaParole() }
            return (nome + cognomeBello).joinToString(" ").trim().ifBlank { grezzo.trim() }
        }

        private val ID_FOTO_CAMERA = Regex("""id=(\d+)&legislatura=(\d+)""")

        /**
         * La foto pubblicata nei dati (getFoto.asp) rimanda a un file che non esiste più:
         * si punta direttamente a quella usata dal sito della Camera.
         */
        internal fun fotoCamera(url: String): String {
            val (id, leg) = ID_FOTO_CAMERA.find(url)?.destructured ?: return url
            return "https://documenti.camera.it/_dati/leg$leg/schededeputatinuovosito/fotoDefinitivo/big/d$id.jpg"
        }

        /** "GENOVA", "GENOVA" → "Genova"; "BASSANO DEL GRAPPA", "VICENZA" → "Bassano del Grappa (Vicenza)". */
        private fun luogo(citta: String?, provincia: String?): String? {
            val c = citta?.takeIf { it.isNotBlank() }?.capitalizzaParole()?.preposizioniMinuscole() ?: return null
            val p = provincia?.takeIf { it.isNotBlank() }?.capitalizzaParole()?.preposizioniMinuscole()
            return if (p == null || p == c) c else "$c ($p)"
        }

        private fun String.preposizioniMinuscole(): String =
            split(" ").joinToString(" ") { if (it.lowercase() in PREPOSIZIONI) it.lowercase() else it }

        private val PREPOSIZIONI = setOf("di", "del", "della", "dei", "delle", "in", "sul", "sulla", "al", "e")

        /** "LIGURIA - P01" → "Liguria - P01": i codici di collegio restano maiuscoli. */
        private fun collegio(grezzo: String): String =
            grezzo.split(" ").joinToString(" ") { parola ->
                if (parola.any(Char::isDigit)) {
                    parola
                } else {
                    parola.lowercase().replace(Regex("""(^|[-(])(\p{L})""")) { it.groupValues[1] + it.groupValues[2].uppercase() }
                }
            }

        internal fun profiloCamera(r: Riga?): ProfiloParlamentare {
            if (r == null) return ProfiloParlamentare()
            // "Laurea in economia aziendale; Funzionario amministrativo, Sindaco di…"
            val parti = r["descr"].orEmpty().split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val (titolo, professione) = when {
                parti.size >= 2 -> parti.first() to parti.drop(1).joinToString("; ")
                parti.size == 1 && TITOLI_DI_STUDIO.containsMatchIn(parti[0]) -> parti[0] to null
                else -> null to parti.firstOrNull()
            }
            return ProfiloParlamentare(
                nascita = r["nascita"]?.let(Formati::normalizzaData)?.ifBlank { null },
                luogoNascita = luogo(r["luogo"], r["prov"]),
                titoloDiStudio = titolo,
                professione = professione,
                elezione = r["collegio"]?.takeIf { it.isNotBlank() }?.let(::collegio),
                tipoElezione = r["tipo"]?.takeIf { it.isNotBlank() }?.replaceFirstChar(Char::uppercase),
                lista = r["lista"]?.takeIf { it.isNotBlank() },
                sito = r["scheda"]?.takeIf { it.isNotBlank() }?.replaceFirst("http://", "https://"),
            )
        }

        private val TITOLI_DI_STUDIO = Regex("^(laurea|diploma|licenza|dottorato|maturit)", RegexOption.IGNORE_CASE)

        internal fun profiloSenato(uri: String, leg: Int, r: Riga?): ProfiloParlamentare {
            val id = uri.substringAfterLast('/')
            val sito = "https://www.senato.it/leg/$leg/BGT/Schede/Attsen/${id.padStart(8, '0')}.htm"
            if (r == null) return ProfiloParlamentare(sito = sito)
            val tipo = r["tipo"]?.takeIf { it.isNotBlank() }
            // I senatori a vita e di diritto non hanno collegio: si mostra il tipo di mandato.
            val elettivo = tipo == null || tipo.equals("elettivo", ignoreCase = true)
            return ProfiloParlamentare(
                nascita = r["nascita"]?.let(Formati::normalizzaData)?.ifBlank { null },
                luogoNascita = luogo(r["citta"], r["prov"]),
                professione = r["professione"]?.takeIf { it.isNotBlank() },
                elezione = if (elettivo) (r["collegio"] ?: r["regione"])?.takeIf { it.isNotBlank() }?.let(::collegio) else null,
                tipoElezione = if (elettivo) null else tipo?.replaceFirstChar(Char::uppercase),
                sito = sito,
            )
        }

        /** "FARMACIE" → "Farmacie". */
        internal fun temaLeggibile(tema: String): String =
            tema.trim().lowercase().replaceFirstChar { it.uppercase() }

        /**
         * Adesioni Camera per deputato. dc:date vale "YYYYMMDD-YYYYMMDD" o "YYYYMMDD-" se in
         * corso; il gruppo si identifica per URI, l'etichetta è quella attuale del gruppo.
         */
        internal fun adesioniCamera(righe: List<Riga>): Map<String, List<Adesione>> =
            righe.groupBy { it["dep"].orEmpty() }.mapValues { (_, lista) ->
                unisci(
                    lista.map { r ->
                        val periodo = r["periodo"].orEmpty()
                        val dal = periodo.substringBefore('-')
                        val al = periodo.substringAfter('-', "").ifBlank { r["fine"].orEmpty() }
                        Triple(r["g"].orEmpty(), pulisciGruppo(r["gruppo"].orEmpty()), dal to al)
                    },
                )
            }

        /** Adesioni Senato per senatore, con la denominazione valida all'inizio dell'adesione. */
        internal fun adesioniSenato(righe: List<Riga>): Map<String, List<Adesione>> =
            righe.groupBy { it["sen"].orEmpty() }.mapValues { (_, lista) ->
                val perAdesione = lista.groupBy { Triple(it["g"].orEmpty(), it["ini"].orEmpty(), it["fine"].orEmpty()) }
                unisci(
                    perAdesione.map { (chiave, denominazioni) ->
                        val (g, ini, fine) = chiave
                        val titolo = denominazioni.filter { it["dini"].orEmpty() <= ini }
                            .maxByOrNull { it["dini"].orEmpty() }
                            ?: denominazioni.minByOrNull { it["dini"].orEmpty() }
                        Triple(g, titolo?.get("titolo").orEmpty(), ini to fine)
                    },
                )
            }

        /**
         * Ordina per data e unisce adesioni consecutive allo stesso gruppo (il Senato, per
         * esempio, registra un giorno di adesione provvisoria seguito da quella definitiva).
         */
        private fun unisci(adesioni: List<Triple<String, String, Pair<String, String>>>): List<Adesione> {
            val ordinate = adesioni
                .map { (g, nome, date) -> Triple(g, nome, Formati.normalizzaData(date.first) to date.second.takeIf { it.isNotBlank() }?.let(Formati::normalizzaData)) }
                .sortedBy { it.third.first }
            val risultato = mutableListOf<Pair<String, Adesione>>()
            for ((g, nome, date) in ordinate) {
                val ultima = risultato.lastOrNull()
                if (ultima != null && ultima.first == g) {
                    val estesa = ultima.second.copy(al = if (ultima.second.al == null || date.second == null) null else maxOf(ultima.second.al!!, date.second!!))
                    risultato[risultato.lastIndex] = g to estesa
                } else {
                    risultato += g to Adesione(gruppo = nome, dal = date.first, al = date.second)
                }
            }
            return risultato.map { it.second }
        }

        /**
         * Tra i ddl collegati a una votazione sceglie quello in discussione: si scartano
         * gli assorbiti e, a parità, vince il numero più alto (la lettura più recente).
         */
        internal fun sceltaDdl(righe: List<Riga>): Riga? = righe
            .filter { !it["fase"].isNullOrBlank() }
            .sortedWith(
                compareBy<Riga> { it["stato"].orEmpty().contains("assorb", ignoreCase = true) }
                    .thenByDescending { it["fase"].orEmpty().filter(Char::isDigit).toIntOrNull() ?: 0 },
            )
            .firstOrNull()

        internal fun ddlPrincipale(righe: List<Riga>): Atto? = sceltaDdl(righe)?.let(::attoSenato)

        internal fun attoSenato(riga: Riga): Atto? {
            val fase = riga["fase"]?.takeIf { it.isNotBlank() } ?: return null
            val titolo = riga["titolo"].orEmpty().trim()
            val breve = riga["breve"]?.trim()?.takeIf { it.isNotBlank() && it != titolo }
            return Atto(
                numero = fase.replace(Regex("^S\\.\\s*"), "S. "),
                titolo = titolo.ifBlank { breve.orEmpty() },
                titoloBreve = breve,
            )
        }

        internal fun ripartisci(voti: List<VotoIndividuale>): List<RipartizioneGruppo> =
            voti.groupBy { it.gruppo }
                .map { (gruppo, lista) ->
                    val base = RipartizioneGruppo(
                        gruppo = gruppo,
                        favorevoli = lista.count { it.voto == TipoVoto.FAVOREVOLE },
                        contrari = lista.count { it.voto == TipoVoto.CONTRARIO },
                        astenuti = lista.count { it.voto == TipoVoto.ASTENUTO },
                        altri = lista.count { !it.voto.palese },
                        missioni = lista.count { it.voto == TipoVoto.MISSIONE },
                        nonPartecipanti = lista.count { !it.voto.presente && it.voto != TipoVoto.MISSIONE },
                    )
                    val linea = base.votoGruppo
                    base.copy(ribelli = if (linea == null) 0 else lista.count { it.voto.palese && it.voto != linea })
                }
                .sortedByDescending { it.totale }

        /** Gruppo corrente per deputato: adesione senza fine ("YYYYMMDD-") e senza dataFine. */
        internal fun gruppiDeputatiAttuali(righe: List<Riga>): Map<String, String> =
            righe.filter { r ->
                r["fine"].isNullOrBlank() && (r["periodo"]?.let { p -> p.endsWith("-") || !p.contains("-") } ?: true)
            }.associate { r -> r["dep"].orEmpty() to pulisciGruppo(r["gruppo"].orEmpty()) }

        internal fun mappaGruppiSenato(righe: List<Riga>): Map<String, String> {
            val mappa = mutableMapOf<String, String>()
            for (r in righe) {
                val sen = r["sen"] ?: continue
                val gruppo = r["gruppo"] ?: continue
                if (sen !in mappa) mappa[sen] = gruppo
            }
            return mappa
        }

        /** "FRATELLI D'ITALIA (19.10.2022)" → "FRATELLI D'ITALIA". */
        internal fun pulisciGruppo(label: String): String =
            // Per i gruppi ancora attivi la Camera pubblica l'etichetta senza parentesi di chiusura:
            // "MISTO (MISTO) (18.10.2022". Data finale e ")" sono quindi facoltative.
            label.replace(Regex("\\s*\\(\\d{2}\\.\\d{2}\\.\\d{4}(\\s*-\\s*(\\d{2}\\.\\d{2}\\.\\d{4})?)?\\)?\\s*$"), "").trim()

        /** "Mario Rossi, XIX Legislatura della Repubblica" → "Mario Rossi". */
        internal fun togliLegislatura(label: String): String =
            label.replace(Regex(",\\s*.* Legislatura della Repubblica\\s*$"), "").trim()

        /**
         * (numero seduta, numero votazione) dall'URI della votazione:
         * Camera ".../votazione.rdf/vs19_047_005" → (47, 5), Senato ".../votazione/19-167-42" → (167, 42).
         */
        internal fun numeriDaUri(uri: String): Pair<Int, Int>? {
            val g = Regex("(\\d+)[_-](\\d+)[_-](\\d+)$").find(uri)?.groupValues ?: return null
            val seduta = g[2].toIntOrNull() ?: return null
            val numero = g[3].toIntOrNull() ?: return null
            return seduta to numero
        }

        private fun String.capitalizzaParole(): String =
            lowercase().split(" ").joinToString(" ") { parola ->
                parola.split("'").joinToString("'") { it.replaceFirstChar(Char::uppercase) }
            }
    }
}

/** Raggruppa le votazioni in sedute, dalla più recente. */
fun List<Votazione>.inSedute(): List<Seduta> =
    groupBy { it.sedutaUri.ifBlank { "${it.ramo}-${it.data}" } }
        .map { (uri, voti) ->
            val ordinati = deduciAtti(voti).sortedByDescending { it.numero ?: 0 }
            Seduta(
                ramo = ordinati.first().ramo,
                uri = uri,
                data = ordinati.first().data,
                numero = ordinati.first().numeroSeduta,
                votazioni = ordinati,
            )
        }
        .sortedByDescending { it.data }

/**
 * Per le votazioni senza atto (emendamenti, articoli…) prende l'atto della prima votazione
 * successiva della stessa seduta che lo indica: gli emendamenti precedono ordini del giorno e
 * voto finale dello stesso provvedimento. Sui dati Camera di gennaio–maggio 2026, dove il
 * collegamento ufficiale c'è, la regola indovina il 95% delle volte; per questo l'atto
 * dedotto è marcato e l'interfaccia lo dichiara. Le votazioni procedurali non ne ricevono.
 */
internal fun deduciAtti(seduta: List<Votazione>): List<Votazione> {
    val crescenti = seduta.sortedBy { it.numero ?: 0 }
    var prossimo: Atto? = null
    return crescenti.asReversed().map { v ->
        when {
            v.atto != null -> v.also { prossimo = v.atto }
            prossimo != null && DescrizioneVoto.riguardaUnAtto(v.oggetto) ->
                v.copy(atto = prossimo!!.copy(dedotto = true))
            else -> v
        }
    }.asReversed()
}
