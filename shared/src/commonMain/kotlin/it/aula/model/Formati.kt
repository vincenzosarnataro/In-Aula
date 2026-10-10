package it.aula.model

import kotlin.math.roundToLong
import kotlin.time.Clock

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

    /** `12345` → `12.345`. */
    fun migliaia(n: Int): String {
        val cifre = kotlin.math.abs(n).toString()
        val gruppi = cifre.reversed().chunked(3).joinToString(".").reversed()
        return if (n < 0) "-$gruppi" else gruppi
    }

    /** `3.25` → `3,3`; da 10 in su senza decimali, con le migliaia: `1234.6` → `1.235`. */
    fun decimale(valore: Double): String =
        if (valore >= 10) {
            migliaia(valore.roundToLong().toInt())
        } else {
            val decimi = (valore * 10).roundToLong()
            "${decimi / 10},${decimi % 10}"
        }

    /** Giorni tra due date ISO (`YYYY-MM-DD`). */
    fun giorniTra(da: String, a: String): Int? {
        val inizio = giornoEpoca(da) ?: return null
        val fine = giornoEpoca(a) ?: return null
        return (fine - inizio).toInt()
    }

    /** La data di oggi, ISO (UTC). */
    fun oggi(): String = daGiornoEpoca(Clock.System.now().toEpochMilliseconds().floorDiv(86_400_000L))

    // Conversioni tra data civile e giorni dal 1970-01-01 (algoritmo di Howard Hinnant).
    private fun giornoEpoca(iso: String): Long? {
        val parti = iso.split("-")
        if (parti.size != 3) return null
        var y = parti[0].toLongOrNull() ?: return null
        val m = parti[1].toLongOrNull() ?: return null
        val d = parti[2].toLongOrNull() ?: return null
        if (m <= 2) y -= 1
        val era = y.floorDiv(400)
        val yoe = y - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    private fun daGiornoEpoca(giorni: Long): String {
        val z = giorni + 719468
        val era = z.floorDiv(146097)
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + (if (m <= 2) 1 else 0)
        return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }
}
