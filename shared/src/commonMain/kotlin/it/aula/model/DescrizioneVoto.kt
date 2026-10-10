package it.aula.model

/**
 * Traduce le descrizioni delle votazioni in italiano corrente.
 *
 * La Camera pubblica subito sigle provvisorie ("EM 8.1010", "RIS 6-255 CPV 6 DISP",
 * "Pdl art.2 C.2822-B Votaz. questione di fiducia") e solo settimane dopo le sostituisce con
 * testi estesi; il Senato usa etichette brevi ("Em. 3.315, Mazzella e altri"). Le forme che
 * non riconosciamo restano come sono: meglio la sigla originale che una parafrasi sbagliata.
 */
object DescrizioneVoto {

    fun oggetto(ramo: Ramo, titolo: String, descrizione: String): String {
        val grezzo = when (ramo) {
            // Alla Camera il titolo è sempre "Votazione": il contenuto sta nella descrizione.
            Ramo.CAMERA -> descrizione.ifBlank { titolo }
            Ramo.SENATO -> titolo.ifBlank { descrizione }
        }
        return leggibile(decodificaEntita(grezzo).trim())
    }

    fun leggibile(testo: String): String {
        if (testo.isBlank()) return "Votazione"
        // Il riferimento all'atto si mostra a parte: "DDL 2825-A - ", "PDL 2822-A E ABB - ",
        // "TU PDL 2360 E ABB-A - " vanno tolti prima di riconoscere la sigla del voto.
        val t = testo.trim().replace(PREFISSO_ATTO, "").trim().ifBlank { testo.trim() }
        val u = t.uppercase()

        return when {
            "FIDUCIA" in u -> Regex("ART\\.?\\s*(\\d+)").find(u)
                ?.let { "Questione di fiducia sull'articolo ${it.groupValues[1]}" }
                ?: "Questione di fiducia"
            "VOTO FINALE" in u || "VOTAZIONE FINALE" in u -> "Voto finale"
            u.startsWith("PREG") || u.startsWith("QP") -> "Questioni pregiudiziali" +
                (Regex("^(?:PREG\\w*|QP)\\s+(?:COST\\w*\\s+)?(.+)$").find(u)?.groupValues?.get(1)
                    ?.let { " n. " + elenco(it) } ?: "")
            u.startsWith("SOSP") || u.startsWith("QS") -> "Questione sospensiva"
            Regex("^EMM?\\.?\\s").containsMatchIn(u) -> emendamenti(t)
            u.startsWith("ART AGG") || u.startsWith("ART. AGG") ->
                "Articolo aggiuntivo " + t.substringAfter("AGG", "").trimStart('.', ' ')
            u.startsWith("MANTENIMENTO ART") -> "Mantenimento dell'articolo " + numeroFinale(t)
            Regex("^ART(?:ICOLO|\\.)?\\s+\\d").containsMatchIn(u) -> "Articolo " + numeroFinale(t)
            u.startsWith("ODG ") -> ordineDelGiorno(t)
            Regex("^RIS?\\s+\\d").containsMatchIn(u) -> risoluzione(u)
            u.startsWith("MOZ ") -> "Mozione " + t.substring(4).trim()
            else -> t
        }
    }

    private val PREFISSO_ATTO = Regex(
        "^(?:TU\\s+)?(?:DDL|PDL)(?:\\s+COST)?\\s+\\d+(?:-[A-Z]+)?(?:\\s+E\\s+ABB?(?:-[A-Z]+)?)?\\s*-\\s*",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Vero se l'oggetto (già reso leggibile) è una parte di un provvedimento: solo per queste
     * votazioni ha senso dedurre l'atto. Numero legale, calendario, inversioni dell'ordine
     * del giorno sono votazioni procedurali e restano senza atto.
     */
    fun riguardaUnAtto(oggetto: String): Boolean =
        Regex("^(Emendament|Articol|Mantenimento|Ordine del giorno|Questione|Questioni|Voto finale)")
            .containsMatchIn(oggetto)

    /**
     * Numero dell'atto Camera citato esplicitamente nella descrizione ("C.2822-B",
     * "A.C. 3083-A", "DDL 2825-A", "TU PDL 2360 E ABB-A", "ODG 9/3045-A/15"), o null.
     */
    fun numeroAttoCamera(descrizione: String): String? {
        val u = decodificaEntita(descrizione).uppercase()
        val schemi = listOf(
            "\\bA\\.?\\s*C\\.\\s*(\\d+(?:-[A-Z]+)?)",
            "\\bC\\.\\s?(\\d+(?:-[A-Z]+)?)",
            "\\b(?:TU\\s+)?(?:DDL|PDL)(?:\\s+COST)?\\s+(\\d+(?:-[A-Z]+)?)",
            "\\bODG\\s+9/(\\d+(?:-[A-Z]+)?)",
        )
        for (schema in schemi) {
            val numero = Regex(schema).find(u)?.groupValues?.get(1) ?: continue
            // "2360 E ABB-A": il suffisso della lettura sta dopo "E ABB".
            val abbinate = Regex(Regex.escape(numero) + "\\s+E\\s+ABB-([A-Z]+)").find(u)
            return if (abbinate != null && '-' !in numero) "$numero-${abbinate.groupValues[1]}" else numero
        }
        return null
    }

    /**
     * Titolo di un atto Camera, ripulito. Arriva come
     * ` S. 1971. - BIGNAMI ed altri: "Disposizioni in materia…" <i>(approvata…)</i>(2822-B)`.
     */
    fun attoCamera(numero: String, titoloGrezzo: String): Atto {
        var t = decodificaEntita(titoloGrezzo).replace(Regex("<[^>]+>"), " ")
        t = t.replace(Regex("\\s+"), " ").trim()
            .replace(Regex("\\(\\s*\\d+(?:-[A-Z]+)?\\s*\\)\\s*$"), "")
            .replace(Regex("^S\\.\\s*\\d+\\.\\s*-\\s*"), "")
            .trim()
        var proponenti: String? = null
        var titolo = t
        Regex("^(?:(.*?):\\s*)?\"(.+)\"\\s*(.*)$").find(t)?.let { m ->
            proponenti = m.groupValues[1].trim().ifBlank { null }?.let(::nomiPropri)
            titolo = m.groupValues[2].trim()
        }
        var titoloBreve: String? = null
        var natura: String? = null
        Regex(
            "^Conversione in legge(?:, con modificazioni,)? del decreto-legge \\d+ \\S+ (\\d{4}), n\\. (\\d+), recante (.+)$",
            RegexOption.IGNORE_CASE,
        ).find(titolo)?.let { m ->
            natura = "Conversione del decreto-legge n. ${m.groupValues[2]}/${m.groupValues[1]}"
            titoloBreve = m.groupValues[3].replaceFirstChar { it.uppercase() }
        }
        return Atto(
            numero = "C. $numero",
            titolo = titolo,
            titoloBreve = titoloBreve,
            natura = natura,
            proponenti = proponenti,
        )
    }

    /**
     * Decodifica le entità HTML, anche quelle codificate due volte ("&amp;#39;"): numeriche
     * ("&#39;", "&#xE0;") e con nome ("&agrave;", "&rsquo;"). Il punto e virgola finale a volte
     * manca nei dati ("&egrave "): si accetta se dopo il nome non continua una parola.
     */
    fun decodificaEntita(testo: String): String {
        if ('&' !in testo) return testo
        var t = testo
        repeat(2) {
            t = ENTITA.replace(t) { m ->
                val nome = m.groupValues[1]
                val codice = when {
                    nome.startsWith("#x", ignoreCase = true) -> nome.drop(2).toIntOrNull(16)
                    nome.startsWith("#") -> nome.drop(1).toIntOrNull()
                    else -> ENTITA_CON_NOME[nome]
                }
                when {
                    codice == null || codice !in 1..0xFFFF -> m.value
                    // Senza ";" solo le entità con nome: "&#39" da solo è troppo ambiguo.
                    m.groupValues[2].isEmpty() && nome.startsWith("#") -> m.value
                    else -> codice.toChar().toString()
                }
            }
        }
        return t
    }

    private val ENTITA = Regex("&(#[xX][0-9a-fA-F]+|#\\d+|[a-zA-Z]+\\d*)(;|(?![a-zA-Z0-9]))")

    private val ENTITA_CON_NOME: Map<String, Int> = buildMap {
        put("amp", '&'.code); put("quot", '"'.code); put("lt", '<'.code); put("gt", '>'.code)
        put("apos", '\''.code); put("nbsp", 0xA0)
        put("lsquo", 0x2018); put("rsquo", 0x2019); put("sbquo", 0x201A)
        put("ldquo", 0x201C); put("rdquo", 0x201D); put("bdquo", 0x201E)
        put("laquo", 0xAB); put("raquo", 0xBB)
        put("ndash", 0x2013); put("mdash", 0x2014); put("hellip", 0x2026); put("bull", 0x2022)
        put("euro", 0x20AC); put("deg", 0xB0); put("sect", 0xA7); put("para", 0xB6)
        put("ordf", 0xAA); put("ordm", 0xBA); put("middot", 0xB7); put("copy", 0xA9); put("reg", 0xAE)
        put("szlig", 0xDF)
        // Lettere accentate: "&agrave;" → "à", "&Egrave;" → "È".
        val accentate = mapOf(
            "grave" to ("aeiou" to "àèìòù"),
            "acute" to ("aeiouy" to "áéíóúý"),
            "circ" to ("aeiou" to "âêîôû"),
            "uml" to ("aeiouy" to "äëïöüÿ"),
            "tilde" to ("ano" to "ãñõ"),
        )
        for ((segno, lettere) in accentate) {
            lettere.first.zip(lettere.second).forEach { (base, accentata) ->
                put("$base$segno", accentata.code)
                put("${base.uppercaseChar()}$segno", accentata.uppercaseChar().code)
            }
        }
        put("ccedil", 'ç'.code); put("Ccedil", 'Ç'.code)
    }

    private fun emendamenti(t: String): String {
        val plurale = Regex("^EMM", RegexOption.IGNORE_CASE).containsMatchIn(t)
        val resto = t.replace(Regex("^EMM?\\.?\\s+", RegexOption.IGNORE_CASE), "").trim()
        // Senato: "3.315, Mazzella e altri" → numero e presentatori.
        Regex("^([\\d.\\-A-Za-z]+(?:\\s+e\\s+[\\d.\\-]+)?),\\s+(\\D.*)$").find(resto)?.let { m ->
            val nome = if (plurale) "Emendamenti" else "Emendamento"
            return "$nome ${m.groupValues[1]} (${m.groupValues[2]})"
        }
        val piuNumeri = Regex("\\d\\s*(?:,|\\s+E\\s+)\\s*\\d", RegexOption.IGNORE_CASE).containsMatchIn(resto)
        val nome = if (plurale || piuNumeri) "Emendamenti" else "Emendamento"
        return "$nome ${resto.lowercaseSuffissi()}"
    }

    private fun ordineDelGiorno(t: String): String {
        // Camera: "ODG 9/3045-A/15" → numero d'ordine finale. Senato: "Odg G1.100".
        Regex("9/[^/]+/(\\d+)").find(t)?.let { return "Ordine del giorno n. ${it.groupValues[1]}" }
        return "Ordine del giorno " + t.substring(4).trim()
    }

    /** "RIS 6-255 NO PREM NO CPV 4, 22 DISP" → "Risoluzione n. 6-00255 · esclusi premessa e capoversi 4, 22 del dispositivo". */
    private fun risoluzione(u: String): String {
        val m = Regex("^RIS?\\s+(\\d+)-(\\d+)\\s*(.*)$").find(u) ?: return u
        val numero = "${m.groupValues[1]}-${m.groupValues[2].padStart(5, '0')}"
        var parte = " " + m.groupValues[3] + " "
        val sostituzioni = listOf(
            "PARTE NON PRECLUSA" to "parte non preclusa",
            "PART NON PRECL" to "parte non preclusa",
            "DISPOSITIVO" to "dispositivo",
            "NO PREM NO CPV" to "esclusi premessa e capoversi",
            "NO PREM" to "esclusa la premessa",
            "NO CPV" to "esclusi i capoversi",
            "PREM E CPV" to "premessa e capoversi",
            "PREMESSA" to "premessa",
            "CPV" to "capoversi",
            "DISP" to "del dispositivo",
            "DIS" to "del dispositivo",
            "RIF" to "(riformulata)",
        )
        for ((sigla, testo) in sostituzioni) parte = parte.replace(Regex("(?<=[\\s,\\d])$sigla(?=[\\s,\\d]|$)"), " $testo ")
        parte = parte.replace(" E ", " e ").replace(Regex("\\s+"), " ").replace(" ,", ",").trim()
        return if (parte.isBlank()) "Risoluzione n. $numero" else "Risoluzione n. $numero · $parte"
    }

    private fun numeroFinale(t: String): String = Regex("(\\d[\\w.\\-]*)\\s*$").find(t)?.groupValues?.get(1) ?: t

    private fun elenco(t: String): String = t.replace(Regex("\\s*,\\s*"), ", ").replace(" E ", " e ")

    /** "8-BIS.1" → "8-bis.1": i suffissi latini vanno in minuscolo. */
    private fun String.lowercaseSuffissi(): String =
        replace(Regex("-(BIS|TER|QUATER)\\b")) { "-" + it.groupValues[1].lowercase() }.replace(" E ", " e ")

    /** "BIGNAMI ed altri" → "Bignami ed altri". */
    private fun nomiPropri(testo: String): String =
        testo.split(" ").joinToString(" ") { parola ->
            if (parola.length > 1 && parola == parola.uppercase() && parola.any { it.isLetter() }) {
                parola.lowercase().replaceFirstChar { it.uppercase() }
            } else {
                parola
            }
        }
}
