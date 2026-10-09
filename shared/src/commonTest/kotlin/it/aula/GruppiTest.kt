package it.aula

import it.aula.model.ColoriGruppi
import it.aula.model.Parlamentare
import it.aula.model.Ramo
import it.aula.presentation.inGruppi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GruppiTest {
    private fun p(cognome: String, gruppo: String, cambi: Int = 0) =
        Parlamentare("uri:$cognome", Ramo.CAMERA, "Nome", cognome, gruppo, fotoUrl = null, cambiDiGruppo = cambi)

    @Test
    fun gruppiDalPiuNumerosoConQuotaSulTotale() {
        val gruppi = listOf(
            p("Rossi", "MISTO"),
            p("Bianchi", "PROGRESSISTI"),
            p("Abate", "PROGRESSISTI", cambi = 1),
            p("Verdi", ""),
        ).inGruppi(Ramo.CAMERA)

        assertEquals(listOf("PROGRESSISTI", "MISTO", Testi.senzaGruppo), gruppi.map { it.nome })
        val primo = gruppi.first()
        assertEquals(listOf("Abate", "Bianchi"), primo.membri.map { it.cognome })
        assertEquals("50,0%", primo.quotaLabel)
        assertEquals(1, primo.conCambi)
    }

    @Test
    fun coloriFissiRiconosconoINomiDeiDueRami() {
        // Stesso partito, nomi diversi tra Camera e Senato.
        assertEquals(ColoriGruppi.colore("MOVIMENTO 5 STELLE"), ColoriGruppi.colore("MoVimento 5 Stelle"))
        assertEquals(
            ColoriGruppi.colore("NOI MODERATI (NOI CON L'ITALIA, CORAGGIO ITALIA, UDC E ITALIA AL CENTRO)-MAIE-CENTRO POPOLARE"),
            ColoriGruppi.colore("Civici d'Italia-UDC-Noi Moderati (Noi con l'Italia, Coraggio Italia, Italia al Centro)-MAIE-Centro Popolare"),
        )
        // "Partito Sardo d'Azione" non deve far finire la Lega tra i colori di Azione.
        assertEquals(ColoriGruppi.colore("LEGA - SALVINI PREMIER"), ColoriGruppi.colore("Lega Salvini Premier - Partito Sardo d'Azione"))
        assertNotEquals(ColoriGruppi.colore("Lega Salvini Premier - Partito Sardo d'Azione"), ColoriGruppi.colore("AZIONE-POPOLARI EUROPEISTI RIFORMATORI-RENEW EUROPE"))
        // Un gruppo sconosciuto ha comunque sempre lo stesso colore.
        assertEquals(ColoriGruppi.colore("NUOVO GRUPPO"), ColoriGruppi.colore("NUOVO GRUPPO"))
    }
}
