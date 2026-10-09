package it.aula.data.sparql

import it.aula.Testi
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Una riga di risultato SPARQL: nome variabile → valore (solo le variabili legate). */
typealias Riga = Map<String, String>

class SparqlException(
    message: String,
    val endpoint: String,
    val status: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Client per i due endpoint Virtuoso del Parlamento.
 *
 * Accorgimenti ripresi da ondata/italianparliament-mcp, dove sono stati misurati sul campo:
 * - Camera: l'endpoint "flappa" per pochi secondi, quindi 5 tentativi con backoff 250ms → 2s.
 * - Senato: almeno 2s tra l'inizio di due richieste consecutive (un burst porta a un 403),
 *   in coda sequenziale così vale anche per chiamate concorrenti.
 * - Senato: la request-URI non può superare 2047 byte (oltre arriva un 403 HTML) e il POST
 *   è sempre rifiutato, quindi la query va compattata e controllata prima dell'invio.
 */
class SparqlClient(
    private val http: HttpClient = defaultHttpClient(),
    private val maxTentativi: Int = 5,
) {
    private val senatoMutex = Mutex()
    private var ultimaChiamataSenato: TimeMark? = null

    suspend fun camera(query: String): List<Riga> = richiesta(CAMERA_ENDPOINT, compatta(query))

    suspend fun senato(query: String): List<Riga> {
        val q = compatta(query)
        val lunghezza = senatoRequestUriLength(q)
        if (lunghezza > SENATO_MAX_REQUEST_URI) {
            throw SparqlException(
                Testi.querySenatoTroppoLunga(lunghezza, SENATO_MAX_REQUEST_URI),
                SENATO_ENDPOINT,
            )
        }
        attendiTurnoSenato()
        return richiesta(SENATO_ENDPOINT, q)
    }

    private suspend fun attendiTurnoSenato() = senatoMutex.withLock {
        val trascorso = ultimaChiamataSenato?.elapsedNow()
        if (trascorso != null && trascorso < SENATO_INTERVALLO_MINIMO) {
            delay(SENATO_INTERVALLO_MINIMO - trascorso)
        }
        ultimaChiamataSenato = TimeSource.Monotonic.markNow()
    }

    private suspend fun richiesta(endpoint: String, query: String): List<Riga> {
        var ultimoErrore: Throwable? = null
        for (tentativo in 1..maxTentativi) {
            try {
                val risposta = http.get(endpoint) {
                    parameter("query", query)
                    parameter("format", "application/json")
                    header(HttpHeaders.Accept, "application/sparql-results+json, application/json")
                }
                val status = risposta.status.value
                if (status in 400..499 && status != 429) {
                    // Errore definitivo: ritentare non serve. Al Senato un 403 è quasi sempre
                    // il blocco per frequenza o una query troppo lunga.
                    val motivo = if (endpoint == SENATO_ENDPOINT && status == 403) {
                        Testi.senatoBloccato
                    } else {
                        Testi.richiestaRifiutata(status)
                    }
                    throw SparqlException(motivo, endpoint, status)
                }
                if (status !in 200..299) throw SparqlException("HTTP $status", endpoint, status)
                return parseSparqlJson(risposta.bodyAsText())
            } catch (e: CancellationException) {
                throw e
            } catch (e: SparqlException) {
                if (e.status != null && e.status in 400..499 && e.status != 429) throw e
                ultimoErrore = e
            } catch (e: Throwable) {
                ultimoErrore = e
            }
            if (tentativo < maxTentativi) {
                val attesa = min(2000L, 250L shl (tentativo - 1))
                delay(attesa.milliseconds)
            }
        }
        throw SparqlException(
            Testi.endpointNonRisponde(maxTentativi),
            endpoint,
            cause = ultimoErrore,
        )
    }

    companion object {
        const val CAMERA_ENDPOINT = "https://dati.camera.it/sparql"
        const val SENATO_ENDPOINT = "https://dati.senato.it/sparql"
        const val SENATO_MAX_REQUEST_URI = 2047
        private val SENATO_INTERVALLO_MINIMO = 2.seconds

        fun defaultHttpClient(log: Boolean = false): HttpClient = HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
            }
            // Una riga per richiesta e una per risposta, con stato e durata: "<-- 200 OK … (812ms)".
            if (log) {
                install(Logging) {
                    level = LogLevel.INFO
                    format = LoggingFormat.OkHttp
                    logger = object : Logger {
                        override fun log(message: String) = logHttp(message)
                    }
                }
            }
        }

        /** Toglie commenti e spazi superflui: al Senato ogni byte della URI conta. */
        fun compatta(query: String): String = query
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")

        /**
         * Lunghezza della request-URI. Usa la codifica percentuale completa (spazio = %20),
         * che è il caso peggiore: se rientra qui, rientra con qualunque encoder.
         */
        fun senatoRequestUriLength(query: String): Int =
            "/sparql?query=".length + query.encodeURLParameter().length +
                "&format=".length + "application/json".encodeURLParameter().length
    }
}
