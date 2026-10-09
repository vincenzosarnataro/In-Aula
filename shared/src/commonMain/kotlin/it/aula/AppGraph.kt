package it.aula

import it.aula.data.ParlamentoRepository
import it.aula.model.Atto
import it.aula.model.Parlamentare
import it.aula.model.Votazione
import it.aula.presentation.AulaStore
import it.aula.presentation.ParlamentareStore
import it.aula.presentation.ParlamentariStore
import it.aula.presentation.SchedaAttoStore
import it.aula.presentation.VotazioneStore

/**
 * DI manuale: un solo repository (e quindi una sola coda verso il Senato) per tutta l'app.
 * Da Swift: `AppGraph.shared.aulaStore()`.
 */
object AppGraph {
    val repository: ParlamentoRepository by lazy { ParlamentoRepository() }

    fun aulaStore() = AulaStore(repository)
    fun votazioneStore(votazione: Votazione) = VotazioneStore(repository, votazione)
    fun parlamentariStore() = ParlamentariStore(repository)
    fun parlamentareStore(parlamentare: Parlamentare) = ParlamentareStore(repository, parlamentare)
    fun schedaAttoStore(atto: Atto) = SchedaAttoStore(repository, atto)
}
