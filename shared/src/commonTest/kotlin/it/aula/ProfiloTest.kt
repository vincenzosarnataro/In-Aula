package it.aula

import it.aula.data.ParlamentoRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfiloTest {
    @Test
    fun fotoCameraPuntaAlFileDelSito() {
        assertEquals(
            "https://documenti.camera.it/_dati/leg19/schededeputatinuovosito/fotoDefinitivo/big/d305733.jpg",
            ParlamentoRepository.fotoCamera("http://documenti.camera.it/apps/nuovosito/deputato/getFoto.asp?id=305733&legislatura=19"),
        )
    }

    @Test
    fun profiloCameraSeparaStudiEProfessione() {
        val p = ParlamentoRepository.profiloCamera(
            mapOf(
                "descr" to "Laurea in economia aziendale; Funzionario amministrativo, Sindaco del Comune di Bogliasco",
                "nascita" to "19710930",
                "luogo" to "BASSANO DEL GRAPPA",
                "prov" to "VICENZA",
                "collegio" to "LIGURIA - P01",
                "tipo" to "maggioritario",
                "scheda" to "http://www.camera.it/uri-res/N2Ls?urn:x",
            ),
        )
        assertEquals("Laurea in economia aziendale", p.titoloDiStudio)
        assertEquals("Funzionario amministrativo, Sindaco del Comune di Bogliasco", p.professione)
        assertEquals("Bassano del Grappa (Vicenza), 30 settembre 1971", p.nascitaEstesa)
        assertEquals("Liguria - P01 · Maggioritario", p.elezioneEstesa)
        assertEquals("https://www.camera.it/uri-res/N2Ls?urn:x", p.sito)
    }

    @Test
    fun profiloSenatoSenzaTitoloDiStudio() {
        val p = ParlamentoRepository.profiloSenato(
            "http://dati.senato.it/senatore/17542",
            19,
            mapOf(
                "nascita" to "1959-06-19",
                "citta" to "Ferrara",
                "prov" to "Ferrara",
                "professione" to "Avvocato",
                "collegio" to "EMILIA-ROMAGNA - U04 (RAVENNA)",
                "tipo" to "elettivo",
            ),
        )
        assertNull(p.titoloDiStudio)
        assertEquals("Ferrara, 19 giugno 1959", p.nascitaEstesa)
        assertEquals("Emilia-Romagna - U04 (Ravenna)", p.elezioneEstesa)
        assertEquals("https://www.senato.it/leg/19/BGT/Schede/Attsen/00017542.htm", p.sito)
    }
}
