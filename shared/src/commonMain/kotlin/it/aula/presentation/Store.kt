package it.aula.presentation

import it.aula.Testi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base degli state holder condivisi. Android lo osserva via [state] (StateFlow),
 * SwiftUI via [observe], che non richiede di toccare Flow/coroutine da Swift.
 */
abstract class Store<S : Any>(iniziale: S) {
    internal val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(iniziale)
    val state: StateFlow<S> = _state.asStateFlow()

    /** Stato corrente, tipizzato anche lato Swift. */
    fun current(): S = _state.value

    protected fun aggiorna(trasforma: (S) -> S) = _state.update(trasforma)

    fun observe(onChange: (S) -> Unit): Osservazione {
        val job = scope.launch { _state.collect { onChange(it) } }
        return Osservazione(job)
    }

    /** Da chiamare quando la schermata viene distrutta (onCleared / deinit). */
    fun close() = scope.cancel()
}

class Osservazione internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

internal fun Throwable.messaggio(): String =
    message?.takeIf { it.isNotBlank() } ?: Testi.erroreRete
