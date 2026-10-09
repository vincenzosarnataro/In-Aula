package it.aula

import it.aula.model.DescrizioneVoto
import it.aula.model.DescrizioneVoto.leggibile
import it.aula.model.Ramo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Descrizioni prese dagli open data di Camera e Senato (ottobre 2026). */
class DescrizioneVotoTest {

    @Test
    fun sigleDellaCamera() {
        assertEquals("Emendamento 8.1010", leggibile("EM 8.1010"))
        assertEquals("Emendamenti 8.1009, 8.3, 8.7, 8.8", leggibile("EM 8.1009, 8.3, 8.7, 8.8"))
        assertEquals("Emendamento 8-bis.1", leggibile("EM 8-BIS.1"))
        assertEquals("Articolo aggiuntivo 6.0101", leggibile("ART AGG 6.0101"))
        assertEquals("Articolo 8", leggibile("ARTICOLO 8"))
        assertEquals("Mantenimento dell'articolo 9", leggibile("MANTENIMENTO ARTICOLO 9"))
        assertEquals("Ordine del giorno n. 20", leggibile("ODG 9/2360 E ABB-A/20"))
        assertEquals("Ordine del giorno n. 15", leggibile("ODG 9/3045-A/15"))
        assertEquals("Voto finale", leggibile("DDL 2825-A - VOTO FINALE"))
        assertEquals("Voto finale", leggibile("TU PDL 2360 E ABB-A - VOTO FINALE"))
        assertEquals("Questione di fiducia sull'articolo 2", leggibile("Pdl art.2 C.2822-B Votaz. questione di fiducia"))
        assertEquals("Questione di fiducia", leggibile("Votazione Fiducia A.C. 3083-A"))
        assertEquals("Questioni pregiudiziali n. 1, 2, 3, 4 e 5", leggibile("PDL 2822-B - PREG COST 1,2,3,4 E 5"))
        // Forma con il riferimento all'atto davanti (luglio 2026).
        assertEquals("Emendamento 1.1", leggibile("PDL 2822-A E ABB - EM 1.1"))
        assertEquals("Questioni pregiudiziali n. 1, 2, 3, 4, 5", leggibile("PDL 2822-A E AB - QP COST 1,2,3,4,5"))
        assertEquals("Ordine del giorno n. 16", leggibile("ODG 9/2822-A E ABB/16"))
    }

    @Test
    fun risoluzioni() {
        assertEquals("Risoluzione n. 6-00261 · dispositivo", leggibile("RIS 6-261 DISPOSITIVO"))
        assertEquals("Risoluzione n. 6-00257", leggibile("RIS 6-257"))
        assertEquals(
            "Risoluzione n. 6-00255 · capoversi 1,3,4,12 del dispositivo (riformulata)",
            leggibile("RIS 6-255 CPV 1,3,4,12 DISP RIF"),
        )
        assertEquals(
            "Risoluzione n. 6-00260 · esclusi premessa e capoversi 4, 22 del dispositivo",
            leggibile("RIS 6-260 NO PREM NO CPV 4, 22 DISP"),
        )
        assertEquals("Risoluzione n. 6-00262 · parte non preclusa", leggibile("RIS 6-262 PARTE NON PRECLUSA"))
    }

    @Test
    fun etichetteDelSenato() {
        assertEquals("Emendamento 3.315 (Mazzella e altri)", leggibile("Em. 3.315, Mazzella e altri"))
        assertEquals("Emendamenti 2.1 e 2.2", leggibile("Emm. 2.1 e 2.2"))
        assertEquals("Voto finale", leggibile("Votazione finale"))
    }

    @Test
    fun formeEsteseELeSconosciuteRestanoComeSono() {
        assertEquals("Emendamento 1.5 BONAFE' ed altri", DescrizioneVoto.oggetto(Ramo.CAMERA, "Votazione", "Emendamento 1.5 BONAFE&amp;#39; ed altri"))
        assertEquals("Inversione dell'ordine del giorno", leggibile("Inversione dell'ordine del giorno"))
    }

    @Test
    fun numeroDellAttoCamera() {
        assertEquals("2822-B", DescrizioneVoto.numeroAttoCamera("Pdl art.2 C.2822-B Votaz. questione di fiducia"))
        assertEquals("3083-A", DescrizioneVoto.numeroAttoCamera("Votazione Fiducia A.C. 3083-A"))
        assertEquals("2825-A", DescrizioneVoto.numeroAttoCamera("DDL 2825-A - VOTO FINALE"))
        assertEquals("2360-A", DescrizioneVoto.numeroAttoCamera("TU PDL 2360 E ABB-A - VOTO FINALE"))
        assertEquals("3045-A", DescrizioneVoto.numeroAttoCamera("ODG 9/3045-A/15"))
        assertNull(DescrizioneVoto.numeroAttoCamera("EM 8.1010"))
        assertNull(DescrizioneVoto.numeroAttoCamera("RIS 6-255 CPV 6 DISP RIF"))
    }

    @Test
    fun titoliDegliAttiCamera() {
        val elettorale = DescrizioneVoto.attoCamera(
            "2822-B",
            " S. 1971. - BIGNAMI ed altri: \"Disposizioni in materia di elezioni della Camera dei deputati e del Senato della Repubblica\" &lt;i&gt;(approvata dalla Camera e modificata dal Senato) &lt;/i&gt;(2822-B)",
        )
        assertEquals("C. 2822-B", elettorale.numero)
        assertEquals("Disposizioni in materia di elezioni della Camera dei deputati e del Senato della Repubblica", elettorale.titolo)
        assertEquals("Bignami ed altri", elettorale.proponenti)
        assertNull(elettorale.titoloBreve)

        val decreto = DescrizioneVoto.attoCamera(
            "3083",
            " \"Conversione in legge del decreto-legge 7 agosto 2026, n. 144, recante disposizioni urgenti per la funzionalità della pubblica amministrazione e degli enti territoriali, nonché in materia di protezione civile\" (3083) ",
        )
        assertEquals("Conversione del decreto-legge n. 144/2026", decreto.natura)
        assertEquals(
            "Disposizioni urgenti per la funzionalità della pubblica amministrazione e degli enti territoriali, nonché in materia di protezione civile",
            decreto.titoloBreve,
        )
        assertNull(decreto.proponenti)
    }
}
