package it.aula.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

/**
 * Posizione di un seggio nell'emiciclo, in coordinate normalizzate:
 * `x` va da 0 (sinistra) a 1 (destra) sulla larghezza, `y` da 0 (in alto) a 1 (in basso)
 * sull'altezza. `raggio` è relativo alla larghezza.
 */
data class Seggio(val x: Double, val y: Double, val raggio: Double)

/** I seggi in ordine da sinistra a destra e il rapporto larghezza/altezza del disegno. */
data class Disposizione(val seggi: List<Seggio>, val proporzione: Double)

/**
 * Disposizione a emiciclo dei seggi su una griglia polare: ogni fila concentrica ha lo stesso
 * numero di seggi, così i pallini stanno allineati sia sugli archi sia sui raggi. I seggi sono
 * ordinati per spicchio, da sinistra a destra e dall'interno verso l'esterno: seggi consecutivi
 * (un gruppo, un tipo di voto) formano spicchi con i bordi dritti.
 */
object Emiciclo {
    /** Vuoto centrale abbastanza ampio da ospitare esito e scarto. */
    private const val RAGGIO_INTERNO = 0.55

    fun disponi(n: Int): Disposizione {
        if (n <= 0) return Disposizione(emptyList(), 2.0)
        val (file, spicchi) = griglia(n)
        val passoRadiale = if (file > 1) (1 - RAGGIO_INTERNO) / (file - 1) else 1 - RAGGIO_INTERNO
        val raggi = List(file) { i -> if (file > 1) RAGGIO_INTERNO + i * passoRadiale else 1.0 }
        // I pallini non devono toccarsi dove sono più vicini: tra le file e sull'arco interno.
        val passoArcoInterno = if (spicchi > 1) PI * raggi.first() / (spicchi - 1) else passoRadiale
        val raggioPallino = 0.42 * min(passoRadiale, passoArcoInterno)

        // Spicchio per spicchio. I posti avanzati (meno di uno per spicchio) si tolgono dalla
        // fila esterna degli ultimi spicchi: l'arco esterno finisce un po' prima, senza buchi.
        val avanzi = file * spicchi - n
        val seggi = buildList {
            for (i in 0 until spicchi) {
                val angolo = if (spicchi == 1) PI / 2 else PI * (1 - i.toDouble() / (spicchi - 1))
                val fileQui = if (i >= spicchi - avanzi) file - 1 else file
                for (j in 0 until fileQui) add(angolo to raggi[j])
            }
        }.take(n) // con pochissimi seggi gli avanzi possono superare gli spicchi

        // In unità in cui la larghezza vale 2: il pallino più esterno tocca i bordi,
        // e sotto la base resta lo spazio per la fila di pallini più bassa.
        val estensione = 1 + raggioPallino
        val margine = raggioPallino / estensione
        val altezza = 1 + margine
        val seggiNormalizzati = seggi.map { (angolo, r) ->
            val x = r * cos(angolo) / estensione
            val y = r * sin(angolo) / estensione
            Seggio(x = (x + 1) / 2, y = (1 - y) / altezza, raggio = margine / 2)
        }
        return Disposizione(seggiNormalizzati, proporzione = 2 / altezza)
    }

    /**
     * Numero di file e di spicchi. Si cerca un passo tra le file simile a quello sull'arco a
     * metà emiciclo (pallini ben spaziati in entrambe le direzioni) con pochi posti avanzati,
     * che accorciano l'arco esterno.
     */
    internal fun griglia(n: Int): Pair<Int, Int> {
        if (n < 6) return 1 to n
        var migliore: Triple<Double, Int, Int>? = null
        for (file in 2..30) {
            val spicchi = ceil(n.toDouble() / file).toInt()
            if (spicchi < 2) break
            val passoRadiale = (1 - RAGGIO_INTERNO) / (file - 1)
            val passoArco = PI * (1 + RAGGIO_INTERNO) / 2 / (spicchi - 1)
            val costo = abs(ln(passoRadiale / passoArco)) + 0.08 * (file * spicchi - n)
            if (migliore == null || costo < migliore.first) migliore = Triple(costo, file, spicchi)
        }
        return migliore!!.second to migliore.third
    }
}
