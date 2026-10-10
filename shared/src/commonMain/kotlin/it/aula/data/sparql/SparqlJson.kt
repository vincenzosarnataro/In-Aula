package it.aula.data.sparql

import it.aula.model.DescrizioneVoto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val json = Json { ignoreUnknownKeys = true }

/**
 * Converte il formato SPARQL 1.1 JSON Results in righe piatte.
 * `{"results":{"bindings":[{"x":{"type":"literal","value":"..."}}]}}` → `[{"x": "..."}]`
 * I literal arrivano spesso con entità HTML ("citt&agrave;"): si decodificano qui, una volta
 * per tutti i campi. Gli URI restano come sono.
 */
internal fun parseSparqlJson(body: String): List<Riga> {
    val root = json.parseToJsonElement(body).jsonObject
    val bindings = root["results"]?.jsonObject?.get("bindings")?.jsonArray ?: return emptyList()
    return bindings.map { binding ->
        (binding as JsonObject).mapValues { (_, v) ->
            val valore = v.jsonObject["value"]?.jsonPrimitive?.content.orEmpty()
            if (v.jsonObject["type"]?.jsonPrimitive?.content == "uri") valore else DescrizioneVoto.decodificaEntita(valore)
        }
    }
}
