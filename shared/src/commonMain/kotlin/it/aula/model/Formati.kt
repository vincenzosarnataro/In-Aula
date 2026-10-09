package it.aula.model

import kotlin.math.roundToLong

/** Formattazione in italiano senza dipendenze da piattaforma. */
object Formati {
    private val mesi = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )

    /** `YYYYMMDD` (Camera) o `YYYY-MM-DD` (Senato, anche con suffisso) → `YYYY-MM-DD`. */
    fun normalizzaData(grezza: String): String {
        val cifre = grezza.take(10).filter { it.isDigit() }
        return if (cifre.length >= 8) {
            "${cifre.substring(0, 4)}-${cifre.substring(4, 6)}-${cifre.substring(6, 8)}"
        } else {
            ""
        }
    }

    /** `2026-10-08` → `8 ottobre 2026`. */
    fun dataEstesa(iso: String): String {
        val parti = iso.split("-")
        if (parti.size != 3) return iso
        val giorno = parti[2].toIntOrNull() ?: return iso
        val mese = parti[1].toIntOrNull()?.let { mesi.getOrNull(it - 1) } ?: return iso
        return "$giorno $mese ${parti[0]}"
    }

    /** `41.236` → `41,2%`. */
    fun percentuale(valore: Double): String {
        val decimi = (valore * 10).roundToLong()
        return "${decimi / 10},${decimi % 10}%"
    }
}
