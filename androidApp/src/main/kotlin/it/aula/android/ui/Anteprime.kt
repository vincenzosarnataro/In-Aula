package it.aula.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import it.aula.data.inSedute
import it.aula.model.Adesione
import it.aula.model.Atto
import it.aula.model.DettaglioVotazione
import it.aula.model.Esito
import it.aula.model.FaseIter
import it.aula.model.Parlamentare
import it.aula.model.Presenze
import it.aula.model.Ramo
import it.aula.model.Relatore
import it.aula.model.RipartizioneGruppo
import it.aula.model.SchedaAtto
import it.aula.model.TipoVoto
import it.aula.model.Votazione
import it.aula.model.VotoIndividuale
import it.aula.presentation.AulaState
import it.aula.presentation.FiltroVotazioni
import it.aula.presentation.ParlamentareState
import it.aula.presentation.ParlamentariState
import it.aula.presentation.SchedaAttoState
import it.aula.presentation.VotazioneState

// Preview delle schermate con dati inventati: nomi, gruppi e numeri non corrispondono a
// parlamentari o votazioni reali.

// ---------------------------------------------------------------- Cornice

/** Tema dell'app (segue chiaro/scuro della preview) e la preferenza che serve a [PulsanteTema]. */
@Composable
private fun Anteprima(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferenza = remember { PreferenzaTema(context) }
    CompositionLocalProvider(LocalPreferenzaTema provides preferenza) {
        AulaTheme {
            Surface(color = MaterialTheme.colorScheme.background) { content() }
        }
    }
}

// ---------------------------------------------------------------- Dati di esempio

private object Esempi {
    private class Gruppo(val nome: String, val seggi: Int, val linea: TipoVoto)

    private val gruppi = listOf(
        Gruppo("PROGRESSISTI", 118, TipoVoto.FAVOREVOLE),
        Gruppo("POPOLARI", 72, TipoVoto.FAVOREVOLE),
        Gruppo("LIBERALI", 41, TipoVoto.FAVOREVOLE),
        Gruppo("CIVICI", 67, TipoVoto.CONTRARIO),
        Gruppo("VERDI", 52, TipoVoto.CONTRARIO),
        Gruppo("AUTONOMIE", 21, TipoVoto.ASTENUTO),
        Gruppo("MISTO", 29, TipoVoto.CONTRARIO),
    )

    private val nomi = listOf("Anna", "Marco", "Giulia", "Luca", "Sara", "Paolo", "Elena", "Davide", "Chiara", "Andrea")
    private val cognomi = listOf("Rossi", "Bianchi", "Esposito", "Romano", "Colombo", "Ricci", "Marino", "Greco", "Bruno", "Gallo")

    val atto = Atto(
        numero = "C. 1234-B",
        titolo = "Disposizioni per la tutela e la valorizzazione dei piccoli comuni montani",
        titoloBreve = "Piccoli comuni montani",
        natura = "Progetto di legge",
        proponenti = "Rossi ed altri",
        temi = listOf("Enti locali", "Montagna"),
    )

    val votazione = Votazione(
        uri = "esempio:votazione:12",
        ramo = Ramo.CAMERA,
        data = "2026-10-06",
        numero = 12,
        titolo = "Disposizioni per la tutela e la valorizzazione dei piccoli comuni montani",
        descrizione = "Voto finale",
        esito = Esito.APPROVATA,
        esitoGrezzo = "Approvato",
        favorevoli = 221,
        contrari = 139,
        astenuti = 19,
        presenti = 379,
        fiducia = false,
        finale = true,
        segreta = false,
        sedutaUri = "esempio:seduta:721",
        numeroSeduta = 721,
        atto = atto,
        maggioranza = 181,
    )

    /** Un voto per seggio: quasi tutti seguono la linea del gruppo, qualcuno no, qualcuno è assente. */
    val voti: List<VotoIndividuale> = gruppi.flatMap { g ->
        (0 until g.seggi).map { i ->
            val voto = when {
                i % 23 == 7 -> TipoVoto.MISSIONE
                i % 17 == 3 -> TipoVoto.NON_HA_VOTATO
                i % 41 == 11 && g.linea != TipoVoto.CONTRARIO -> TipoVoto.CONTRARIO
                else -> g.linea
            }
            VotoIndividuale(
                parlamentareUri = "esempio:${g.nome}:$i",
                nome = "${nomi[i % nomi.size]} ${cognomi[(i / nomi.size) % cognomi.size]}".uppercase(),
                gruppo = g.nome,
                voto = voto,
            )
        }
    }

    val ripartizione: List<RipartizioneGruppo> = gruppi.map { g ->
        val delGruppo = voti.filter { it.gruppo == g.nome }
        fun conta(t: TipoVoto) = delGruppo.count { it.voto == t }
        RipartizioneGruppo(
            gruppo = g.nome,
            favorevoli = conta(TipoVoto.FAVOREVOLE),
            contrari = conta(TipoVoto.CONTRARIO),
            astenuti = conta(TipoVoto.ASTENUTO),
            altri = delGruppo.count { !it.voto.palese },
            missioni = conta(TipoVoto.MISSIONE),
            nonPartecipanti = conta(TipoVoto.NON_HA_VOTATO),
            ribelli = delGruppo.count { it.voto.palese && it.voto != g.linea },
        )
    }

    val votazioni: List<Votazione> = listOf(
        votazione,
        votazione.copy(
            uri = "esempio:votazione:11", numero = 11, descrizione = "Emendamento 3.200",
            esito = Esito.RESPINTA, favorevoli = 172, contrari = 178, astenuti = 4, finale = false,
        ),
        votazione.copy(
            uri = "esempio:votazione:10", numero = 10, descrizione = "Questione di fiducia sull'articolo 2",
            favorevoli = 230, contrari = 140, astenuti = 2, fiducia = true, finale = false,
        ),
        votazione.copy(
            uri = "esempio:votazione:31", numero = 31, data = "2026-10-01", sedutaUri = "esempio:seduta:718",
            numeroSeduta = 718, descrizione = "Ordine del giorno n. 9/1234-B/4", esito = Esito.RESPINTA,
            favorevoli = 13, contrari = 166, astenuti = 111, finale = false,
        ),
        votazione.copy(
            uri = "esempio:votazione:30", numero = 30, data = "2026-10-01", sedutaUri = "esempio:seduta:718",
            numeroSeduta = 718, descrizione = "Questioni pregiudiziali", esito = Esito.RESPINTA,
            favorevoli = 151, contrari = 229, astenuti = 0, segreta = true, finale = false,
        ),
    )

    val parlamentari: List<Parlamentare> = List(14) { i ->
        Parlamentare(
            uri = "esempio:parlamentare:$i",
            ramo = Ramo.CAMERA,
            nome = nomi[i % nomi.size],
            cognome = cognomi[(i * 7 + i / nomi.size) % cognomi.size],
            gruppo = gruppi[i % gruppi.size].nome,
            fotoUrl = null,
            cambiDiGruppo = if (i % 5 == 2) 1 else 0,
        )
    }.sortedBy { it.cognome }

    val parlamentare = Parlamentare(
        uri = "esempio:parlamentare:giulia-esposito",
        ramo = Ramo.SENATO,
        nome = "Giulia",
        cognome = "Esposito",
        gruppo = "Liberali",
        fotoUrl = null,
        cambiDiGruppo = 1,
    )

    val scheda = SchedaAtto(
        atto = atto,
        tipo = "Progetto di Legge",
        iniziativa = "Parlamentare",
        presentatoIl = "2026-03-15",
        primoFirmatario = "Anna Rossi",
        altriFirmatari = listOf("Marco Bianchi", "Luca Romano", "Sara Colombo", "Paolo Ricci", "Elena Marino", "Davide Greco"),
        relatori = listOf(Relatore("Chiara Bruno", "VIII Commissione", "commissione")),
        temi = atto.temi,
        iter = listOf(
            FaseIter("C. 1234", Ramo.CAMERA, "approvato", "2026-06-16"),
            FaseIter("S. 987", Ramo.SENATO, "appr. con modificaz", "2026-09-15"),
            FaseIter("C. 1234-B", Ramo.CAMERA, "appr. def. non pubbl", "2026-10-06"),
        ),
        votazioni = votazioni.take(3),
        nota = null,
    )
}

// ---------------------------------------------------------------- Aula

@PreviewLightDark
@Composable
internal fun AnteprimaAula() = Anteprima {
    AulaContenuto(AulaState(sedute = Esempi.votazioni.inSedute()), AzioniAula())
}

@Preview(name = "Aula · filtro Respinte")
@Composable
internal fun AnteprimaAulaFiltrata() = Anteprima {
    AulaContenuto(
        AulaState(sedute = Esempi.votazioni.inSedute(), filtro = FiltroVotazioni.RESPINTE),
        AzioniAula(),
    )
}

@Preview(name = "Aula · caricamento")
@Composable
internal fun AnteprimaAulaCaricamento() = Anteprima {
    AulaContenuto(AulaState(ramo = Ramo.SENATO, caricamento = true), AzioniAula())
}

@Preview(name = "Aula · errore")
@Composable
internal fun AnteprimaAulaErrore() = Anteprima {
    AulaContenuto(AulaState(errore = "Il server della Camera non risponde."), AzioniAula())
}

// ---------------------------------------------------------------- Dettaglio votazione

@PreviewLightDark
@Composable
internal fun AnteprimaVotazione() = Anteprima {
    VotazioneContenuto(
        VotazioneState(
            votazione = Esempi.votazione,
            dettaglio = DettaglioVotazione(Esempi.voti, Esempi.ripartizione, nota = null),
            caricamento = false,
        ),
        AzioniVotazione(),
    )
}

@Preview(name = "Votazione · gruppo in evidenza", heightDp = 1400)
@Composable
internal fun AnteprimaVotazioneGruppo() = Anteprima {
    VotazioneContenuto(
        VotazioneState(
            votazione = Esempi.votazione,
            dettaglio = DettaglioVotazione(Esempi.voti, Esempi.ripartizione, nota = null),
            filtroGruppo = "CIVICI",
            caricamento = false,
        ),
        AzioniVotazione(),
    )
}

@Preview(name = "Votazione · caricamento")
@Composable
internal fun AnteprimaVotazioneCaricamento() = Anteprima {
    VotazioneContenuto(VotazioneState(votazione = Esempi.votazione), AzioniVotazione())
}

// ---------------------------------------------------------------- Scheda provvedimento

@PreviewLightDark
@Composable
internal fun AnteprimaSchedaAtto() = Anteprima {
    SchedaAttoContenuto(SchedaAttoState(atto = Esempi.atto, scheda = Esempi.scheda, caricamento = false))
}

@Preview(name = "Scheda atto · caricamento")
@Composable
internal fun AnteprimaSchedaAttoCaricamento() = Anteprima {
    SchedaAttoContenuto(SchedaAttoState(atto = Esempi.atto))
}

// ---------------------------------------------------------------- Parlamentari

@PreviewLightDark
@Composable
internal fun AnteprimaParlamentari() = Anteprima {
    ParlamentariContenuto(ParlamentariState(tutti = Esempi.parlamentari), AzioniParlamentari())
}

@Preview(name = "Parlamentari · solo cambi di gruppo")
@Composable
internal fun AnteprimaParlamentariCambi() = Anteprima {
    ParlamentariContenuto(ParlamentariState(tutti = Esempi.parlamentari, soloCambi = true), AzioniParlamentari())
}

// ---------------------------------------------------------------- Scheda parlamentare

@PreviewLightDark
@Composable
internal fun AnteprimaParlamentare() = Anteprima {
    ParlamentareContenuto(
        ParlamentareState(
            parlamentare = Esempi.parlamentare,
            presenze = Presenze(
                votazioniTotali = 8338, presenze = 6012, missioni = 1630, assenze = 696,
                favorevoli = 3480, contrari = 1890, astenuti = 642, nota = null,
            ),
            storiaGruppi = listOf(
                Adesione("Civici", "2022-10-13", "2024-03-20"),
                Adesione("Liberali", "2024-03-21", null),
            ),
            caricamento = false,
        ),
    )
}

@Preview(name = "Parlamentare · caricamento presenze")
@Composable
internal fun AnteprimaParlamentareCaricamento() = Anteprima {
    ParlamentareContenuto(ParlamentareState(parlamentare = Esempi.parlamentare))
}
