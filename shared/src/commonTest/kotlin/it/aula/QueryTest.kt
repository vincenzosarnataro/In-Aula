package it.aula

import it.aula.data.ParlamentoRepository
import it.aula.data.SenatoQuery
import it.aula.data.sparql.SparqlClient
import it.aula.data.sparql.parseSparqlJson
import it.aula.model.DescrizioneVoto
import it.aula.model.Formati
import it.aula.model.Legislatura
import it.aula.model.TipoVoto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueryTest {

    private val uriSenatore = "http://dati.senato.it/senatore/32609"
    private val uriVotazione = "http://dati.senato.it/votazione/19-167-42"

    @Test
    fun leQueryDelSenatoStannoNelLimiteDellaRequestUri() {
        val query = buildList {
            add(SenatoQuery.votazioni(19, 120, 10_000))
            val ddl = List(ParlamentoRepository.DDL_PER_QUERY) { "http://dati.senato.it/ddl/${59875 + it}" }
            add(SenatoQuery.temiDdl(ddl))
            add(SenatoQuery.relatori(ddl))
            add(SenatoQuery.iter(listOf("C.2822-B", "C.2822"), 19))
            add(SenatoQuery.votazioniDelDdl("55147"))
            add(SenatoQuery.adesioniGruppi(19))
            add(SenatoQuery.ddlDelleSedute(List(ParlamentoRepository.SEDUTE_PER_QUERY) { "http://dati.senato.it/sedutaassemblea/${24370 + it}" }))
            add(SenatoQuery.gruppiAllaData("2026-10-08"))
            add(SenatoQuery.gruppiAttuali)
            add(SenatoQuery.senatoriInCarica(19))
            add(SenatoQuery.senatoriInCarica(18, "2022-10-12"))
            add(SenatoQuery.votazioniNelMandato(uriSenatore, 19))
            SenatoQuery.categorie.forEach {
                add(SenatoQuery.votiCategoria(uriVotazione, it))
                add(SenatoQuery.presenzeCategoria(uriSenatore, it, 19))
            }
        }
        for (q in query) {
            val lunghezza = SparqlClient.senatoRequestUriLength(SparqlClient.compatta(q))
            assertTrue(lunghezza <= SparqlClient.SENATO_MAX_REQUEST_URI, "Query di $lunghezza byte:\n$q")
        }
    }

    @Test
    fun parsingRisultatiSparql() {
        val json = """
            {"head":{"vars":["s","n"]},"results":{"bindings":[
              {"s":{"type":"uri","value":"http://x/1"},"n":{"type":"typed-literal","value":"42"}},
              {"s":{"type":"uri","value":"http://x/2"}}
            ]}}
        """.trimIndent()
        val righe = parseSparqlJson(json)
        assertEquals(2, righe.size)
        assertEquals("42", righe[0]["n"])
        assertEquals(null, righe[1]["n"])
    }

    @Test
    fun dateEFormati() {
        assertEquals("2026-10-08", Formati.normalizzaData("20261008"))
        assertEquals("2026-10-08", Formati.normalizzaData("2026-10-08"))
        assertEquals("8 ottobre 2026", Formati.dataEstesa("2026-10-08"))
        assertEquals("41,2%", Formati.percentuale(41.236))
    }

    @Test
    fun numeriDagliUri() {
        assertEquals(47 to 5, ParlamentoRepository.numeriDaUri("http://dati.camera.it/ocd/votazione.rdf/vs19_047_005"))
        assertEquals(167 to 42, ParlamentoRepository.numeriDaUri(uriVotazione))
    }

    @Test
    fun scomposizioneDelNonHaVotato() {
        assertEquals(TipoVoto.MISSIONE, ParlamentoRepository.tipoVotoCamera("Non ha votato", "In missione"))
        assertEquals(TipoVoto.ASSENTE, ParlamentoRepository.tipoVotoCamera("Non ha votato", "Non ha partecipato"))
        assertEquals(TipoVoto.NON_HA_VOTATO, ParlamentoRepository.tipoVotoCamera("Non ha votato", "Altro"))
        assertEquals(TipoVoto.HA_VOTATO, ParlamentoRepository.tipoVotoCamera("Ha votato", null))
    }

    @Test
    fun pulituraEtichette() {
        assertEquals("FRATELLI D'ITALIA", ParlamentoRepository.pulisciGruppo("FRATELLI D'ITALIA (19.10.2022)"))
        // Così arrivano dalla Camera le etichette dei gruppi ancora attivi.
        assertEquals("FRATELLI D'ITALIA (FDI)", ParlamentoRepository.pulisciGruppo("FRATELLI D'ITALIA (FDI) (18.10.2022"))
        assertEquals("MISTO (MISTO)", ParlamentoRepository.pulisciGruppo("MISTO (MISTO) (18.10.2022-"))
        assertEquals("IV-CR", ParlamentoRepository.pulisciGruppo("IV-CR (20.11.2023-15.01.2025)"))
        // Le sigle tra parentesi senza data restano.
        assertEquals("NM(N-C-U-I)M-CP", ParlamentoRepository.pulisciGruppo("NM(N-C-U-I)M-CP"))
        assertEquals("Mario Rossi", ParlamentoRepository.togliLegislatura("Mario Rossi, XIX Legislatura della Repubblica"))
    }

    @Test
    fun legislature() {
        assertEquals("XIX", Legislatura.numeroRomano(19))
        assertEquals("XIV", Legislatura.numeroRomano(14))
        assertEquals("XVIII", Legislatura.numeroRomano(18))

        val conclusa = ParlamentoRepository.legislatura(
            mapOf("s" to "http://dati.camera.it/ocd/legislatura.rdf/repubblica_18", "d" to "20180323-20221012"),
        )
        assertEquals(Legislatura(18, "2018-03-23", "2022-10-12"), conclusa)
        assertEquals("2018–2022", conclusa?.periodo)
        assertTrue(conclusa?.conclusa == true)

        val inCorso = ParlamentoRepository.legislatura(
            mapOf("s" to "http://dati.camera.it/ocd/legislatura.rdf/repubblica_19", "d" to "20221013"),
        )
        assertEquals(Legislatura(19, "2022-10-13", null), inCorso)
        assertEquals("dal 2022", inCorso?.periodo)
    }

    @Test
    fun entitaHtml() {
        assertEquals("città è più", DescrizioneVoto.decodificaEntita("citt&agrave; &egrave; pi&ugrave;"))
        assertEquals("È l’Aquila", DescrizioneVoto.decodificaEntita("&Egrave; l&rsquo;Aquila"))
        assertEquals("l'ente «x» – y", DescrizioneVoto.decodificaEntita("l&#39;ente &laquo;x&raquo; &ndash; y"))
        assertEquals("à", DescrizioneVoto.decodificaEntita("&#xE0;"))
        assertEquals("l'atto", DescrizioneVoto.decodificaEntita("l&amp;#39;atto"))
        // Punto e virgola mancante: si decodifica solo se la parola finisce lì.
        assertEquals("perché no", DescrizioneVoto.decodificaEntita("perch&eacute no"))
        // Ciò che non è un'entità nota resta com'è.
        assertEquals("R&D e AT&T", DescrizioneVoto.decodificaEntita("R&D e AT&T"))

        val json = """
            {"head":{"vars":["s","t"]},"results":{"bindings":[
              {"s":{"type":"uri","value":"http://x/getFoto.asp?id=1&amp;leg=19"},
               "t":{"type":"literal","value":"Libert&agrave; di stampa"}}
            ]}}
        """.trimIndent()
        val riga = parseSparqlJson(json).single()
        assertEquals("Libertà di stampa", riga["t"])
        assertEquals("http://x/getFoto.asp?id=1&amp;leg=19", riga["s"])
    }

    @Test
    fun membriDelGoverno() {
        val riga = mapOf(
            "ruolo" to "MINISTRO",
            "incarico" to "Ministro dell'Economia e delle finanze (23.10.2022)",
            "nome" to "GIANCARLO",
            "cognome" to "GIORGETTI",
            "inizio" to "20221023",
            "interim" to "0",
            "dep" to "http://dati.camera.it/ocd/deputato.rdf/d50115_19",
            "foto" to "http://documenti.camera.it/apps/nuovosito/deputato/getFoto.asp?id=50115&legislatura=19",
        )
        val m = ParlamentoRepository.membroGoverno(riga)!!
        assertEquals("Giancarlo Giorgetti", m.nomeCompleto)
        assertEquals("Ministro dell'Economia e delle finanze", m.incarico)
        assertEquals("2022-10-23", m.dal)
        assertEquals(false, m.cessato)
        assertEquals("http://dati.camera.it/ocd/deputato.rdf/d50115_19", m.deputato?.uri)
        assertEquals(null, ParlamentoRepository.membroGoverno(riga + ("ruolo" to "SOTTOSEGRETARIO DI STATO")))

        assertEquals("economia e delle finanze", ParlamentoRepository.chiaveIncarico("Ministro dell'Economia e delle finanze"))
        assertEquals("sport e i giovani", ParlamentoRepository.chiaveIncarico("Ministro per lo Sport e i giovani"))
        assertEquals("interno", ParlamentoRepository.chiaveIncarico("Ministro dell'Interno"))
        assertEquals("disabilità", ParlamentoRepository.chiaveIncarico("Ministra per le Disabilità"))

        val governo = ParlamentoRepository.governo(
            mapOf("g" to "http://dati.camera.it/ocd/governo.rdf/g182", "titolo" to "I Governo Draghi (12.02.2021 - 21.10.2022)", "d" to "20210212-20221021"),
        )
        assertEquals("I Governo Draghi", governo.nome)
        assertEquals("12 febbraio 2021 – 21 ottobre 2022", governo.periodo)
    }

    @Test
    fun avvicendamentiSenzaCambiDiNomeDeiMinisteri() {
        val governo = it.aula.model.Governo("g", "I Governo Meloni", "2022-10-21", null)
        fun ministro(cognome: String, incarico: String, al: String? = null) =
            it.aula.model.MembroGoverno(it.aula.model.RuoloGoverno.MINISTRO, "X", cognome, incarico, dal = "2022-10-23", al = al)
        val c = it.aula.model.ComposizioneGoverno(
            governo,
            listOf(
                ministro("Pichetto", "Ministro della Transizione ecologica", al = "2022-11-11"),
                ministro("Pichetto", "Ministro dell'Ambiente e della sicurezza energetica"),
                ministro("Sangiuliano", "Ministro della Cultura", al = "2024-09-06"),
                ministro("Giuli", "Ministro della Cultura"),
            ),
        )
        assertEquals(listOf("X Sangiuliano"), c.avvicendamenti.map { it.nomeCompleto })
        assertEquals(2, c.ministri.size)
    }
}
