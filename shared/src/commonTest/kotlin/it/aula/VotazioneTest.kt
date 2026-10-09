package it.aula

import it.aula.data.ParlamentoRepository
import it.aula.data.deduciAtti
import it.aula.model.Atto
import it.aula.model.Esito
import it.aula.model.Ramo
import it.aula.model.Votazione
import it.aula.model.Emiciclo
import it.aula.model.TipoVoto
import it.aula.model.VotoIndividuale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VotazioneTest {

    @Test
    fun lEmicicloHaUnSeggioPerOgniVotoEStaNelRiquadro() {
        listOf(1, 7, 200, 400).forEach { n ->
            val d = Emiciclo.disponi(n)
            assertEquals(n, d.seggi.size)
            d.seggi.forEach { s ->
                assertTrue(s.x - s.raggio >= -1e-9 && s.x + s.raggio <= 1 + 1e-9, "x fuori per n=$n: $s")
                val r = s.raggio * d.proporzione
                assertTrue(s.y - r >= -1e-9 && s.y + r <= 1 + 1e-9, "y fuori per n=$n: $s")
            }
            // Ordinati da sinistra a destra.
            assertTrue(d.seggi.first().x <= d.seggi.last().x)
        }
    }

    @Test
    fun iSeggiStannoSuArchiERaggiAllineati() {
        listOf(400, 398, 321, 205, 6, 5).forEach { n ->
            val (file, spicchi) = Emiciclo.griglia(n)
            assertTrue(file * spicchi - n in 0 until file, "n=$n: $file file x $spicchi spicchi")
            val disposizione = Emiciclo.disponi(n)
            // Coordinate in unità della larghezza, con l'origine nel centro della base dell'arco
            // (che in altezza normalizzata sta a proporzione / 2).
            val centroY = disposizione.proporzione / 2
            fun polari(x: Double, y: Double): Pair<Double, Double> {
                val dx = x - 0.5
                val dy = (centroY - y) / disposizione.proporzione
                return kotlin.math.hypot(dx, dy) to kotlin.math.atan2(dy, dx)
            }
            val seggi = disposizione.seggi.map { polari(it.x, it.y) }
            val archi = seggi.map { kotlin.math.round(it.first * 1e6) }.distinct()
            assertEquals(file, archi.size, "n=$n: i pallini devono stare su $file archi")
            // Ogni spicchio sta su un raggio: tutti i suoi pallini hanno lo stesso angolo.
            val spicchiTrovati = seggi.groupBy { kotlin.math.round(it.second * 1e6) }
            assertEquals(spicchi, spicchiTrovati.size, "n=$n: i pallini devono stare su $spicchi raggi")
            assertEquals(n, seggi.size)
        }
    }

    @Test
    fun unPallinoPerOgniSeggioQualunqueSiaIlNumero() {
        for (n in 1..450) assertEquals(n, Emiciclo.disponi(n).seggi.size, "n=$n")
    }

    @Test
    fun compattezzaRibelliENonPartecipantiComeOpenpolis() {
        fun voto(i: Int, tipo: TipoVoto) = VotoIndividuale("u$i", "N$i", "G", tipo)
        val voti = List(6) { voto(it, TipoVoto.FAVOREVOLE) } +
            voto(10, TipoVoto.CONTRARIO) +
            voto(11, TipoVoto.ASSENTE) +
            voto(12, TipoVoto.ASSENTE) +
            voto(13, TipoVoto.MISSIONE)
        val g = ParlamentoRepository.ripartisci(voti).single()
        assertEquals(TipoVoto.FAVOREVOLE, g.votoGruppo)
        assertEquals(1, g.ribelli)
        assertEquals(2, g.nonPartecipanti)
        assertEquals(1, g.missioni)
        // 6 su 9 in carica (la missione non conta).
        assertEquals("66,7%", g.compattezzaLabel)
    }

    private fun votazione(numero: Int, descrizione: String, atto: Atto? = null) = Votazione(
        uri = "v$numero", ramo = Ramo.CAMERA, data = "2026-10-01", numero = numero, titolo = "Votazione",
        descrizione = descrizione, esito = Esito.APPROVATA, esitoGrezzo = "1", favorevoli = 1, contrari = 0,
        astenuti = 0, presenti = 1, fiducia = false, finale = false, segreta = false,
        sedutaUri = "s1", numeroSeduta = 1, atto = atto,
    )

    @Test
    fun gliEmendamentiPrendonoLAttoDelVotoSuccessivoMarcatoComeDedotto() {
        val dl = Atto("C. 3045", "Prezzi petroliferi")
        val risultato = deduciAtti(
            listOf(
                votazione(1, "EM 1.1"),
                votazione(2, "EM 1.2"),
                votazione(5, "Verifica del numero legale"),
                votazione(6, "ODG 9/3045-A/1", dl),
                votazione(7, "EM 2.1"),
            ),
        ).associateBy { it.numero }
        assertEquals(dl.copy(dedotto = true), risultato[1]?.atto)
        assertEquals(dl.copy(dedotto = true), risultato[2]?.atto)
        assertEquals(dl, risultato[6]?.atto)
        // Le votazioni procedurali restano senza atto anche se stanno in mezzo.
        assertNull(risultato[5]?.atto)
        // Dopo l'ultimo riferimento non si inventa nulla.
        assertNull(risultato[7]?.atto)
    }

    @Test
    fun alSenatoVinceIlDdlNonAssorbitoPiuRecente() {
        val atto = ParlamentoRepository.ddlPrincipale(
            listOf(
                mapOf("fase" to "S.61", "titolo" to "Disposizioni elettorali", "stato" to "assorbito"),
                mapOf("fase" to "S.1822", "titolo" to "Disposizioni elettorali", "stato" to "assorbito"),
                mapOf("fase" to "S.1971", "titolo" to "Disposizioni in materia di elezioni", "breve" to "Legge elettorale", "stato" to "appr. con modificaz"),
            ),
        )
        assertEquals("S. 1971", atto?.numero)
        assertEquals("Legge elettorale", atto?.titoloBreve)
    }

    private fun conteggi(
        ramo: Ramo, esito: Esito, fav: Int, contr: Int, ast: Int, maggioranza: Int? = null,
    ) = votazione(1, "Votazione").copy(
        ramo = ramo, esito = esito, favorevoli = fav, contrari = contr, astenuti = ast, maggioranza = maggioranza,
    )

    @Test
    fun scartoComeOpenpolisEVotiPerRibaltare() {
        // Camera, fiducia del 6 ottobre 2026: Openpolis indica la distanza dalla maggioranza.
        val fiducia = conteggi(Ramo.CAMERA, Esito.APPROVATA, 227, 149, 3, maggioranza = 189)
        assertEquals(38, fiducia.scarto)
        // Alla Camera gli astenuti non votano: servivano 78 contrari in più.
        assertEquals(78, fiducia.votiPerRibaltare)
        assertEquals(false, fiducia.sulFilo)
        // Respinta: un favorevole oltre il pareggio.
        assertEquals(79, conteggi(Ramo.CAMERA, Esito.RESPINTA, 151, 229, 0).votiPerRibaltare)
        // Al Senato gli astenuti pesano come contrari.
        assertEquals(31, conteggi(Ramo.SENATO, Esito.APPROVATA, 72, 40, 1).votiPerRibaltare)
        val stretta = conteggi(Ramo.CAMERA, Esito.APPROVATA, 150, 145, 0)
        assertEquals(true, stretta.sulFilo)
        assertTrue("Sul filo" in stretta.etichette)
    }

    @Test
    fun assenzeDecisiveTraIGruppiPerdenti() {
        fun voti(gruppo: String, tipo: TipoVoto, n: Int, da: Int) =
            List(n) { VotoIndividuale("$gruppo${da + it}", "N", gruppo, tipo) }
        val dettaglio = it.aula.model.DettaglioVotazione(
            voti = voti("A", TipoVoto.FAVOREVOLE, 10, 0) +
                voti("B", TipoVoto.CONTRARIO, 6, 0) + voti("B", TipoVoto.ASSENTE, 3, 100) +
                voti("C", TipoVoto.CONTRARIO, 1, 0) + voti("C", TipoVoto.ASSENTE, 2, 100) +
                // Le missioni non contano.
                voti("C", TipoVoto.MISSIONE, 5, 200),
            ripartizione = emptyList(),
            nota = null,
        ).let { it.copy(ripartizione = ParlamentoRepository.ripartisci(it.voti)) }
        val stato = it.aula.presentation.VotazioneState(
            votazione = conteggi(Ramo.CAMERA, Esito.APPROVATA, 10, 7, 0),
            dettaglio = dettaglio,
        )
        val assenze = stato.assenzeDecisive
        assertEquals(3, assenze?.servivano)
        assertEquals(5, assenze?.assenti)
        assertEquals(listOf("B"), assenze?.decisiviDaSoli)
        assertEquals(listOf("B", "C"), assenze?.perGruppo?.map { it.gruppo })
        // Con uno scarto più ampio le assenze non bastano.
        assertNull(stato.copy(votazione = conteggi(Ramo.CAMERA, Esito.APPROVATA, 20, 7, 0)).assenzeDecisive)
    }

    @Test
    fun storiaDeiGruppiCameraESenato() {
        val camera = ParlamentoRepository.adesioniCamera(
            listOf(
                mapOf("dep" to "d1", "g" to "gr4131", "gruppo" to "MOVIMENTO 5 STELLE (M5S) (18.10.2022", "periodo" to "20221018-20260429"),
                mapOf("dep" to "d1", "g" to "gr4111", "gruppo" to "MISTO (MISTO) (18.10.2022", "periodo" to "20260429-20260728"),
                mapOf("dep" to "d1", "g" to "gr4133", "gruppo" to "FRATELLI D'ITALIA (FDI) (18.10.2022", "periodo" to "20260728-"),
                mapOf("dep" to "d2", "g" to "gr4133", "gruppo" to "FRATELLI D'ITALIA (FDI) (18.10.2022", "periodo" to "20221018-"),
            ),
        )
        assertEquals(listOf("MOVIMENTO 5 STELLE (M5S)", "MISTO (MISTO)", "FRATELLI D'ITALIA (FDI)"), camera["d1"]?.map { it.gruppo })
        assertEquals("2022-10-18", camera["d1"]?.first()?.dal)
        assertEquals("2026-04-29", camera["d1"]?.first()?.al)
        assertNull(camera["d1"]?.last()?.al)
        assertEquals(1, camera["d2"]?.size)

        // Senato: un giorno di adesione provvisoria allo stesso gruppo si unisce alla successiva;
        // il nome è quello valido all'inizio dell'adesione.
        val senato = ParlamentoRepository.adesioniSenato(
            listOf(
                mapOf("sen" to "s1", "g" to "91", "ini" to "2022-10-18", "fine" to "2022-10-18", "titolo" to "Vecchio nome", "dini" to "2022-10-13"),
                mapOf("sen" to "s1", "g" to "91", "ini" to "2022-10-19", "fine" to "2023-11-08", "titolo" to "Vecchio nome", "dini" to "2022-10-13"),
                mapOf("sen" to "s1", "g" to "9", "ini" to "2023-11-09", "titolo" to "Misto", "dini" to "2022-10-13"),
                mapOf("sen" to "s1", "g" to "9", "ini" to "2023-11-09", "titolo" to "Nome futuro", "dini" to "2025-01-01"),
            ),
        )
        assertEquals(listOf("Vecchio nome", "Misto"), senato["s1"]?.map { it.gruppo })
        assertEquals("2023-11-08", senato["s1"]?.first()?.al)
    }

    @Test
    fun statiDellIterPerEsteso() {
        fun stato(s: String) = it.aula.model.FaseIter("S. 1971", Ramo.SENATO, s, null).statoEsteso
        assertEquals("Approvato con modificazioni", stato("appr. con modificaz"))
        assertEquals("Approvato definitivamente, non ancora pubblicato", stato("appr. def. non pubbl"))
        assertEquals("Assorbito", stato("assorbito"))
    }

    @Test
    fun nomiDeiFirmatari() {
        assertEquals("Galeazzo Bignami", ParlamentoRepository.nomeFirmatario("BIGNAMI Galeazzo"))
        assertEquals("Paolo Emilio Russo", ParlamentoRepository.nomeFirmatario("RUSSO Paolo Emilio"))
    }

    @Test
    fun filtriERicercaNellaListaDelleVotazioni() {
        val atto = Atto("S. 1786", "Delega al Governo…", "Delega in materia farmaceutica", temi = listOf("Farmacie"))
        // Votazioni nette (300 a 10) tranne la seconda, respinta per 5 voti.
        val sedute = listOf(
            votazione(1, "EM 1.1", atto).copy(favorevoli = 300, contrari = 10),
            conteggi(Ramo.CAMERA, Esito.RESPINTA, 100, 104, 0).copy(uri = "v2", numero = 2, descrizione = "ODG 9/3045-A/1"),
            votazione(3, "Pdl art.2 C.2822-B Votaz. questione di fiducia").copy(fiducia = true, favorevoli = 300, contrari = 10),
        )
        val stato = it.aula.presentation.AulaState(sedute = listOf(it.aula.model.Seduta(Ramo.CAMERA, "s1", "2026-10-01", 1, sedute)))
        assertEquals(3, stato.votazioniVisibili)
        assertEquals(1, stato.copy(filtro = it.aula.presentation.FiltroVotazioni.FIDUCIA).votazioniVisibili)
        assertEquals(1, stato.copy(filtro = it.aula.presentation.FiltroVotazioni.SUL_FILO).votazioniVisibili)
        assertEquals(1, stato.copy(ricerca = "farmaceutica").votazioniVisibili)
        assertEquals(1, stato.copy(ricerca = "s.1786").votazioniVisibili)
        assertEquals(1, stato.copy(tema = "Farmacie").votazioniVisibili)
        assertEquals(listOf("Farmacie"), stato.temiDisponibili)
        assertEquals(0, stato.copy(ricerca = "inesistente").seduteVisibili.size)
    }
}
