package it.aula.data.sparql

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true }

/**
 * Converte il formato SPARQL 1.1 JSON Results in righe piatte.
 * `{"results":{"bindings":[{"x":{"type":"literal","value":"..."}}]}}` → `[{"x": "..."}]`
 */
internal fun parseSparqlJson(body: String): List<Riga> {
    val root = json.parseToJsonElement(body).jsonObject
    val bindings = root["results"]?.jsonObject?.get("bindings")?.jsonArray ?: return emptyList()
    return bindings.map { binding ->
        (binding as JsonObject).mapValues { (_, v) ->
            v.jsonObject["value"]?.jsonPrimitive?.content.orEmpty()
        }
    }
}
