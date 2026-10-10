package it.aula

import it.aula.data.ParlamentoRepository
import it.aula.data.sparql.SparqlClient
import it.aula.model.Atto
import it.aula.model.Parlamentare
import it.aula.model.Votazione
import it.aula.presentation.AulaStore
import it.aula.presentation.GovernoStore
import it.aula.presentation.GruppiStore
import it.aula.presentation.LegislaturaStore
import it.aula.presentation.ParlamentareStore
import it.aula.presentation.ParlamentariStore
import it.aula.presentation.SchedaAttoStore
import it.aula.presentation.VotazioneStore

/**
 * DI manuale: un solo repository (e quindi una sola coda verso il Senato) per tutta l'app.
 * Da Swift: `AppGraph.shared.aulaStore()`.
 */
object AppGraph {
    /** Log delle chiamate HTTP: le app lo accendono solo in debug, prima di creare il primo store. */
    var logChiamate: Boolean = false

    val repository: ParlamentoRepository by lazy {
        ParlamentoRepository(SparqlClient(SparqlClient.defaultHttpClient(log = logChiamate)))
    }

    /** Legislatura salvata dall'app (null = la corrente): da chiamare prima di creare gli store. */
    fun ripristinaLegislatura(numero: Int?) = repository.scegliLegislatura(numero)

    fun legislaturaStore() = LegislaturaStore(repository)
    fun aulaStore() = AulaStore(repository)
    fun votazioneStore(votazione: Votazione) = VotazioneStore(repository, votazione)
    fun parlamentariStore() = ParlamentariStore(repository)
    fun gruppiStore() = GruppiStore(repository)
    fun governoStore() = GovernoStore(repository)
    fun parlamentareStore(parlamentare: Parlamentare) = ParlamentareStore(repository, parlamentare)
    fun schedaAttoStore(atto: Atto) = SchedaAttoStore(repository, atto)
}
