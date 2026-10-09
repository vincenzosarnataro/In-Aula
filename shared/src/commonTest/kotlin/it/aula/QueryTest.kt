package it.aula

import it.aula.data.ParlamentoRepository
import it.aula.data.SenatoQuery
import it.aula.data.sparql.SparqlClient
import it.aula.data.sparql.parseSparqlJson
import it.aula.model.Formati
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
}
