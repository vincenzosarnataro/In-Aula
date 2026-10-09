package it.aula.data.sparql

import platform.Foundation.NSLog

/** Nella console di Xcode e in Console.app. */
internal actual fun logHttp(message: String) {
    NSLog("[AulaHttp] %@", message)
}
