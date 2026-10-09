package it.aula.data

/**
 * Query SPARQL. Struttura e proprietà sono quelle verificate in ondata/italianparliament-mcp
 * (src/tools/votes.ts, vote-detail.ts, attendance.ts, senato-*.ts, deputies.ts, senators.ts).
 *
 * Trappole di Virtuoso da ricordare:
 * - le date Camera sono literal `YYYYMMDD`: nei confronti vanno avvolte in STR();
 * - la tripla `?v a ocd:voto` sta in due named graph: contare con COUNT(DISTINCT ?v);
 * - prima selezionare e limitare gli URI in una subquery, poi agganciare gli OPTIONAL
 *   (altrimenti Virtuoso materializza tutto e va in timeout).
 */
internal object CameraQuery {
    private const val PREFISSI = """
PREFIX ocd: <http://dati.camera.it/ocd/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
PREFIX dc: <http://purl.org/dc/elements/1.1/>
"""

    private fun leg(n: Int) = "<http://dati.camera.it/ocd/legislatura.rdf/repubblica_$n>"

    val legislaturaCorrente = """
$PREFISSI
SELECT ?s WHERE {
  ?s a ocd:legislatura ; dc:date ?d .
  FILTER(CONTAINS(STR(?s), "repubblica_"))
}
ORDER BY DESC(?d)
LIMIT 1
"""

    /** Variabili di una votazione: le stesse per la lista, le sedute e le schede degli atti. */
    private const val CAMPI = """?s ?label ?title ?description ?date ?approvato ?favorevoli ?contrari ?astenuti
       ?presenti ?maggioranza ?richiestaFiducia ?votazioneSegreta ?votazioneFinale ?rif_seduta ?attoId ?attoTitolo"""

    private const val DETTAGLI = """
  ?s rdfs:label ?label .
  OPTIONAL { ?s dc:title ?title }
  OPTIONAL { ?s dc:description ?description }
  OPTIONAL { ?s ocd:approvato ?approvato }
  OPTIONAL { ?s ocd:favorevoli ?favorevoli }
  OPTIONAL { ?s ocd:contrari ?contrari }
  OPTIONAL { ?s ocd:astenuti ?astenuti }
  OPTIONAL { ?s ocd:presenti ?presenti }
  OPTIONAL { ?s ocd:maggioranza ?maggioranza }
  OPTIONAL { ?s ocd:richiestaFiducia ?richiestaFiducia }
  OPTIONAL { ?s ocd:votazioneSegreta ?votazioneSegreta }
  OPTIONAL { ?s ocd:votazioneFinale ?votazioneFinale }
  OPTIONAL { ?s ocd:rif_seduta ?rif_seduta }
  OPTIONAL { ?s ocd:rif_attoCamera ?atto . ?atto dc:identifier ?attoId ; dc:title ?attoTitolo }"""

    fun votazioni(legislatura: Int, limit: Int, offset: Int) = """
$PREFISSI
SELECT DISTINCT $CAMPI
WHERE {
  {
    SELECT ?s (MAX(?vd) AS ?date) WHERE {
      ?s a ocd:votazione ; ocd:rif_leg ${leg(legislatura)} .
      OPTIONAL { ?s dc:date ?vd }
    }
    GROUP BY ?s
    ORDER BY DESC(?date)
    LIMIT $limit
    OFFSET $offset
  }
$DETTAGLI
}
ORDER BY DESC(?date)
"""

    /** Tutte le votazioni di alcune sedute: servono per dedurre l'atto degli emendamenti. */
    fun votazioniDelleSedute(sedute: Collection<String>) = """
$PREFISSI
SELECT DISTINCT $CAMPI
WHERE {
  ?s a ocd:votazione ; ocd:rif_seduta ?seduta ; dc:date ?date .
  FILTER(?seduta IN (${sedute.joinToString { "<$it>" }}))
$DETTAGLI
}
"""

    /**
     * Sedute in cui si è votato su un atto, in qualunque lettura ("2822", "2822-A", "2822-B"):
     * per collegamento ufficiale o per numero citato nella descrizione ("PDL 2822-A E ABB - EM 1.1",
     * "ODG 9/2822-A/16", "C.2822-B"). Il numero deve seguire un prefisso d'atto: "EM 1.2822" no.
     */
    fun seduteDellAtto(legislatura: Int, base: String) = """
$PREFISSI
SELECT DISTINCT ?seduta WHERE {
  ?v a ocd:votazione ; ocd:rif_leg ${leg(legislatura)} ; ocd:rif_seduta ?seduta .
  {
    ?v dc:description ?d .
    # CONTAINS sfrutta l'indice testuale di Virtuoso; REGEX da sola costa 15 secondi.
    FILTER(CONTAINS(?d, "$base") && REGEX(?d, "(C\\.|A\\.C\\. ?|DDL |PDL |PDL COST |9/)$base([^0-9]|$)", "i"))
  } UNION {
    ?v ocd:rif_attoCamera ?a . ?a dc:identifier ?id .
    FILTER(?id = "$base" || STRSTARTS(?id, "$base-"))
  }
}
"""

    /** Scheda di un atto Camera: firmatari, iniziativa, data, tipo. Una riga per firmatario. */
    fun dettaglioAtto(legislatura: Int, numeri: Collection<String>) = """
$PREFISSI
SELECT DISTINCT ?id ?titolo ?creator ?iniziativa ?data ?tipo ?firmatario WHERE {
  ?a a ocd:atto ; ocd:rif_leg ${leg(legislatura)} ; dc:identifier ?id ; dc:title ?titolo .
  FILTER(?id IN (${numeri.joinToString { "\"$it\"" }}))
  OPTIONAL { ?a dc:creator ?creator }
  OPTIONAL { ?a ocd:iniziativa ?iniziativa }
  OPTIONAL { ?a dc:date ?data }
  OPTIONAL { ?a dc:type ?tipo }
  OPTIONAL { ?a dc:contributor ?firmatario }
}
"""

    /**
     * Titoli degli atti per numero ("2822-B", "3083"). Serve per le votazioni recenti:
     * ocd:rif_attoCamera arriva con mesi di ritardo, ma la descrizione spesso cita l'atto.
     */
    fun attiPerNumero(legislatura: Int, numeri: Collection<String>) = """
$PREFISSI
SELECT DISTINCT ?id ?titolo WHERE {
  ?a a ocd:atto ; ocd:rif_leg ${leg(legislatura)} ; dc:identifier ?id ; dc:title ?titolo .
  FILTER(?id IN (${numeri.joinToString { "\"$it\"" }}))
}
"""

    /**
     * Voti individuali. dc:description c'è solo sui "Non ha votato" e distingue
     * "In missione", "Presidente di turno" e "Non ha partecipato" (l'assenza vera).
     */
    fun votiIndividuali(votazioneUri: String) = """
$PREFISSI
SELECT DISTINCT ?dep ?label ?type ?descr ?sigla
WHERE {
  ?v a ocd:voto ; ocd:rif_votazione <$votazioneUri> ; ocd:rif_deputato ?dep ; dc:type ?type .
  OPTIONAL { ?v dc:description ?descr }
  OPTIONAL { ?v ocd:siglaGruppo ?sigla }
  OPTIONAL { ?dep rdfs:label ?label }
}
LIMIT 1000
"""

    fun deputatiInCarica(legislatura: Int) = """
$PREFISSI
SELECT DISTINCT ?s ?nome ?cognome ?foto
WHERE {
  ?s a ocd:deputato ; ocd:rif_leg ${leg(legislatura)} ;
     foaf:firstName ?nome ; foaf:surname ?cognome ; ocd:rif_mandatoCamera ?m .
  OPTIONAL { ?m ocd:endDate ?fine }
  OPTIONAL { ?s foaf:depiction ?foto }
  FILTER(!BOUND(?fine))
}
ORDER BY ?cognome ?nome
"""

    /**
     * Adesioni ai gruppi della legislatura. dc:date vale "YYYYMMDD-YYYYMMDD" oppure
     * "YYYYMMDD-" se l'adesione è ancora in corso: il filtro sulle attive si fa in Kotlin.
     */
    fun adesioniGruppi(legislatura: Int) = """
$PREFISSI
SELECT DISTINCT ?dep ?g ?gruppo ?periodo ?fine
WHERE {
  ?g a ocd:gruppoParlamentare ; ocd:rif_leg ${leg(legislatura)} ; rdfs:label ?gruppo ;
     ocd:siComponeDi ?m .
  ?m ocd:rif_deputato ?dep .
  OPTIONAL { ?m dc:date ?periodo }
  OPTIONAL { ?m ocd:dataFine ?fine }
}
"""

    /**
     * Profilo di un deputato. Titolo di studio e professione stanno insieme in dc:description
     * ("Laurea in …; Ingegnere"); la nascita è sulla persona, collegata tramite il mandato.
     */
    fun profilo(deputatoUri: String) = """
$PREFISSI
PREFIX bio: <http://purl.org/vocab/bio/0.1/>
PREFIX dct: <http://purl.org/dc/terms/>
SELECT ?descr ?scheda ?nascita ?luogo ?prov ?collegio ?lista ?tipo WHERE {
  <$deputatoUri> ocd:rif_mandatoCamera ?m .
  OPTIONAL { <$deputatoUri> dc:description ?descr }
  OPTIONAL { <$deputatoUri> dct:isReferencedBy ?scheda }
  OPTIONAL {
    ?p a foaf:Person ; ocd:rif_mandatoCamera ?m ; bio:Birth ?b .
    ?b bio:date ?nascita .
    OPTIONAL { ?b ocd:rif_luogo ?l . ?l rdfs:label ?luogo . OPTIONAL { ?l ocd:parentADM2 ?prov } }
  }
  OPTIONAL {
    ?m ocd:rif_elezione ?e .
    OPTIONAL { ?e dc:coverage ?collegio }
    OPTIONAL { ?e ocd:lista ?lista }
    OPTIONAL { ?e ocd:tipoElezione ?tipo }
  }
}
LIMIT 1
"""

    fun presenze(deputatoUri: String) = """
$PREFISSI
SELECT ?type ?descr (COUNT(DISTINCT ?v) AS ?n) WHERE {
  ?v a ocd:voto ; ocd:rif_deputato <$deputatoUri> ; dc:type ?type .
  OPTIONAL { ?v dc:description ?descr }
} GROUP BY ?type ?descr
"""
}

/**
 * Query Senato: corte per costruzione (limite di 2047 byte sulla request-URI, POST rifiutato)
 * e una per categoria di voto, perché questo Virtuoso non supporta VALUES/BIND.
 */
internal object SenatoQuery {
    private const val PREFISSI = """
PREFIX osr: <http://dati.senato.it/osr/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
"""

    /** Proprietà osr che legano una votazione ai senatori, per categoria. */
    val categorie = listOf(
        "favorevole",
        "contrario",
        "astenuto",
        "presenteNonVotante",
        "inCongedoMissione",
    )

    fun votazioni(legislatura: Int, limit: Int, offset: Int) = """
$PREFISSI
SELECT DISTINCT ?v ?date ?numero ?tipo ?label ?esito ?favorevoli ?contrari ?astenuti ?presenti ?maggioranza ?s
WHERE {
  ?v a osr:Votazione ; osr:legislatura $legislatura ; osr:seduta ?s .
  ?s osr:dataSeduta ?date .
  OPTIONAL { ?v rdfs:label ?label }
  OPTIONAL { ?v osr:numero ?numero }
  OPTIONAL { ?v osr:tipoVotazione ?tipo }
  OPTIONAL { ?v osr:esito ?esito }
  OPTIONAL { ?v osr:favorevoli ?favorevoli }
  OPTIONAL { ?v osr:contrari ?contrari }
  OPTIONAL { ?v osr:astenuti ?astenuti }
  OPTIONAL { ?v osr:presenti ?presenti }
  OPTIONAL { ?v osr:maggioranza ?maggioranza }
}
ORDER BY DESC(?date) DESC(?numero) DESC(?v)
LIMIT $limit
OFFSET $offset
"""

    /**
     * Disegni di legge collegati alle votazioni di alcune sedute. Una votazione può puntare a
     * molti ddl (discussione congiunta): la scelta del principale si fa in Kotlin.
     * Query separata dalla lista, così le righe multiple non alterano la paginazione.
     * Il Virtuoso del Senato non conosce VALUES: si filtra con IN.
     */
    fun ddlDelleSedute(sedute: Collection<String>) = """
PREFIX osr: <http://dati.senato.it/osr/>
SELECT DISTINCT ?v ?d ?fase ?titolo ?breve ?stato WHERE {
  ?v osr:seduta ?s ; osr:oggetto ?o .
  FILTER(?s IN (${sedute.joinToString { "<$it>" }}))
  ?o osr:relativoA ?d . ?d a osr:Ddl ; osr:fase ?fase .
  OPTIONAL { ?d osr:titolo ?titolo }
  OPTIONAL { ?d osr:titoloBreve ?breve }
  OPTIONAL { ?d osr:statoDdl ?stato }
}
"""

    /** Temi generali (thesaurus TESEO) di alcuni ddl. livello è un xsd:string: si confronta con STR(). */
    fun temiDdl(ddl: Collection<String>) = """
PREFIX osr: <http://dati.senato.it/osr/>
PREFIX dcterms: <http://purl.org/dc/terms/>
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
SELECT DISTINCT ?d ?tema WHERE {
  ?d osr:classificazione ?c .
  FILTER(?d IN (${ddl.joinToString { "<$it>" }}))
  ?c osr:livello ?livello ; dcterms:subject ?t .
  FILTER(STR(?livello) = "Generale")
  ?t skos:prefLabel ?tema .
}
"""

    /**
     * Iter completo di un provvedimento nei due rami, a partire da una sua fase ("C.2822-B",
     * "S.1971"): tutte le fasi con lo stesso idDdl. Il Senato registra anche le letture Camera.
     * I numeri si ripetono tra legislature, e fase è un xsd:string: serve STR() per confrontarla.
     */
    fun iter(fasi: Collection<String>, legislatura: Int) = """
PREFIX osr: <http://dati.senato.it/osr/>
SELECT DISTINCT ?d ?id ?fase ?ramo ?stato ?data ?titolo ?breve ?iniziativa ?presentato ?natura WHERE {
  ?x a osr:Ddl ; osr:legislatura $legislatura ; osr:fase ?f ; osr:idDdl ?id .
  FILTER(STR(?f) IN (${fasi.joinToString { "\"$it\"" }}))
  ?d a osr:Ddl ; osr:idDdl ?id ; osr:fase ?fase .
  OPTIONAL { ?d osr:ramo ?ramo }
  OPTIONAL { ?d osr:statoDdl ?stato }
  OPTIONAL { ?d osr:dataStatoDdl ?data }
  OPTIONAL { ?d osr:titolo ?titolo }
  OPTIONAL { ?d osr:titoloBreve ?breve }
  OPTIONAL { ?d osr:descrIniziativa ?iniziativa }
  OPTIONAL { ?d osr:dataPresentazione ?presentato }
  OPTIONAL { ?d osr:natura ?natura }
}
"""

    fun relatori(ddl: Collection<String>) = """
PREFIX osr: <http://dati.senato.it/osr/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?nome ?organo ?tipo WHERE {
  ?d osr:relatore ?r .
  FILTER(?d IN (${ddl.joinToString { "<$it>" }}))
  ?r rdfs:label ?nome .
  OPTIONAL { ?r osr:organo ?organo }
  OPTIONAL { ?r osr:tipoRelatore ?tipo }
}
"""

    /** Votazioni d'Aula del Senato su tutte le fasi di un provvedimento (stesso idDdl). */
    fun votazioniDelDdl(idDdl: String) = """
$PREFISSI
SELECT DISTINCT ?v ?date ?numero ?tipo ?label ?esito ?favorevoli ?contrari ?astenuti ?presenti ?maggioranza ?s
WHERE {
  ?v a osr:Votazione ; osr:seduta ?s ; osr:oggetto ?o .
  ?o osr:relativoA ?d . ?d osr:idDdl $idDdl .
  ?s osr:dataSeduta ?date .
  OPTIONAL { ?v rdfs:label ?label }
  OPTIONAL { ?v osr:numero ?numero }
  OPTIONAL { ?v osr:tipoVotazione ?tipo }
  OPTIONAL { ?v osr:esito ?esito }
  OPTIONAL { ?v osr:favorevoli ?favorevoli }
  OPTIONAL { ?v osr:contrari ?contrari }
  OPTIONAL { ?v osr:astenuti ?astenuti }
  OPTIONAL { ?v osr:presenti ?presenti }
  OPTIONAL { ?v osr:maggioranza ?maggioranza }
}
"""

    /**
     * Adesioni ai gruppi dei senatori in una legislatura, con le denominazioni del gruppo
     * (un gruppo può cambiare nome: si sceglie quella valida all'inizio dell'adesione).
     */
    fun adesioniGruppi(legislatura: Int) = """
PREFIX osr: <http://dati.senato.it/osr/>
PREFIX ocd: <http://dati.camera.it/ocd/>
SELECT DISTINCT ?sen ?g ?ini ?fine ?titolo ?dini WHERE {
  ?sen ocd:aderisce ?m .
  ?m a ocd:adesioneGruppo ; osr:legislatura $legislatura ; osr:gruppo ?g ; osr:inizio ?ini .
  OPTIONAL { ?m osr:fine ?fine }
  ?g osr:denominazione ?den . ?den osr:titolo ?titolo ; osr:inizio ?dini .
}
"""

    fun votiCategoria(votazioneUri: String, proprieta: String) = """
PREFIX osr: <http://dati.senato.it/osr/>
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
SELECT DISTINCT ?sen ?nome ?cognome WHERE {
  <$votazioneUri> osr:$proprieta ?sen .
  OPTIONAL { ?sen foaf:firstName ?nome }
  OPTIONAL { ?sen foaf:lastName ?cognome }
}
"""

    /** Gruppo di ogni senatore alla data indicata (`YYYY-MM-DD`). */
    fun gruppiAllaData(data: String) = """
PREFIX osr: <http://dati.senato.it/osr/>
PREFIX ocd: <http://dati.camera.it/ocd/>
SELECT DISTINCT ?sen ?gruppo WHERE {
  ?sen a osr:Senatore ; ocd:aderisce ?m .
  ?m a ocd:adesioneGruppo ; osr:gruppo ?g ; osr:inizio ?ini .
  OPTIONAL { ?m osr:fine ?fine }
  ?g osr:denominazione ?den .
  ?den osr:titolo ?gruppo ; osr:inizio ?dini .
  OPTIONAL { ?den osr:fine ?dfine }
  FILTER(str(?ini) <= "$data")
  FILTER(!BOUND(?fine) || str(?fine) >= "$data")
  FILTER(str(?dini) <= "$data")
  FILTER(!BOUND(?dfine) || str(?dfine) >= "$data")
}
"""

    /** Gruppo attuale: adesione e denominazione senza data di fine. */
    val gruppiAttuali = """
PREFIX osr: <http://dati.senato.it/osr/>
PREFIX ocd: <http://dati.camera.it/ocd/>
SELECT DISTINCT ?sen ?gruppo WHERE {
  ?sen a osr:Senatore ; ocd:aderisce ?m .
  ?m a ocd:adesioneGruppo ; osr:gruppo ?g .
  OPTIONAL { ?m osr:fine ?fine }
  ?g osr:denominazione ?den .
  ?den osr:titolo ?gruppo .
  OPTIONAL { ?den osr:fine ?dfine }
  FILTER(!BOUND(?fine) && !BOUND(?dfine))
}
"""

    /** Senza foaf:depiction: le foto del Senato non sono scaricabili dall'app (vedi ParlamentoRepository.parlamentare). */
    fun senatoriInCarica(legislatura: Int) = """
PREFIX osr: <http://dati.senato.it/osr/>
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
SELECT DISTINCT ?s ?nome ?cognome WHERE {
  ?s a osr:Senatore ; foaf:firstName ?nome ; foaf:lastName ?cognome ; osr:mandato ?m .
  ?m osr:legislatura $legislatura .
  OPTIONAL { ?m osr:fine ?fine }
  FILTER(!BOUND(?fine))
}
ORDER BY ?cognome ?nome
"""

    /** Profilo di un senatore: il Senato non pubblica il titolo di studio. */
    fun profilo(senatoreUri: String, legislatura: Int) = """
PREFIX osr: <http://dati.senato.it/osr/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?nascita ?citta ?prov ?professione ?collegio ?regione ?tipo WHERE {
  <$senatoreUri> osr:mandato ?m . ?m osr:legislatura $legislatura .
  OPTIONAL { <$senatoreUri> osr:dataNascita ?nascita }
  OPTIONAL { <$senatoreUri> osr:cittaNascita ?citta }
  OPTIONAL { <$senatoreUri> osr:provinciaNascita ?prov }
  OPTIONAL { <$senatoreUri> osr:professione ?pr . ?pr rdfs:label ?professione }
  OPTIONAL { ?m osr:collegioElezione ?collegio }
  OPTIONAL { ?m osr:regioneElezione ?regione }
  OPTIONAL { ?m osr:tipoMandato ?tipo }
}
LIMIT 1
"""

    fun presenzeCategoria(senatoreUri: String, proprieta: String, legislatura: Int) = """
PREFIX osr: <http://dati.senato.it/osr/>
SELECT (COUNT(?v) AS ?n) WHERE {
  ?v osr:$proprieta <$senatoreUri> ; osr:legislatura $legislatura .
}
"""

    /**
     * Denominatore delle percentuali: votazioni della legislatura cadute DENTRO il mandato
     * del senatore. Il Senato non registra l'assente semplice: l'assenza è la differenza.
     */
    fun votazioniNelMandato(senatoreUri: String, legislatura: Int) = """
PREFIX osr: <http://dati.senato.it/osr/>
SELECT (COUNT(DISTINCT ?v) AS ?n) WHERE {
  <$senatoreUri> osr:mandato ?m .
  ?m osr:legislatura $legislatura ; osr:inizio ?i .
  OPTIONAL { ?m osr:fine ?f }
  ?v a osr:Votazione ; osr:legislatura $legislatura ; osr:seduta ?s .
  ?s osr:dataSeduta ?d .
  FILTER(?d >= ?i && (!BOUND(?f) || ?d <= ?f))
}
"""
}
