package it.aula.data.sparql

/**
 * Nella console di Xcode. Non NSLog: è variadica, e Kotlin/Native passa la String al `%@` senza
 * convertirla in NSString, così NSLog legge i byte del testo come un puntatore (EXC_BAD_ACCESS).
 */
internal actual fun logHttp(message: String) {
    println("[AulaHttp] $message")
}
