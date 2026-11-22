package it.historia.player.data.project

import it.historia.player.data.preferences.ProjectPreferenceSection
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Copia di sicurezza, esatta e tipizzata, delle chiavi di progetto presenti negli store.
 * Serve solo per il rollback locale dell'importazione: non viene mai esportata.
 */
internal object ProjectPreferencesBackup {
    fun encode(sections: List<ProjectPreferenceSection>): String {
        val root = JSONObject()
        sections.forEach { section ->
            val values = JSONObject()
            section.entries().toSortedMap().forEach { (key, value) ->
                values.put(key, encodeValue(value) ?: return@forEach)
            }
            root.put(section.preferencesName, values)
        }
        return JSONObject().put("version", 1).put("sections", root).toString()
    }

    fun restore(sections: List<ProjectPreferenceSection>, text: String) {
        val root = JSONObject(text).getJSONObject("sections")
        sections.forEach { section ->
            val values = root.optJSONObject(section.preferencesName) ?: JSONObject()
            val decoded = linkedMapOf<String, Any?>()
            values.keys().forEach { key -> decoded[key] = decodeValue(values.getJSONObject(key)) }
            if (!section.replaceAll(decoded)) {
                throw IOException("Ripristino delle preferenze ${section.preferencesName} non riuscito.")
            }
        }
    }

    private fun encodeValue(value: Any?): JSONObject? {
        return when (value) {
            is String -> JSONObject().put("t", "s").put("v", value)
            is Int -> JSONObject().put("t", "i").put("v", value)
            is Long -> JSONObject().put("t", "l").put("v", value)
            is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
            is Boolean -> JSONObject().put("t", "b").put("v", value)
            is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.filterIsInstance<String>().sorted()))
            else -> null
        }
    }

    private fun decodeValue(json: JSONObject): Any? {
        return when (json.getString("t")) {
            "s" -> json.getString("v")
            "i" -> json.getInt("v")
            "l" -> json.getLong("v")
            "f" -> json.getDouble("v").toFloat()
            "b" -> json.getBoolean("v")
            "set" -> json.getJSONArray("v").let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
            else -> null
        }
    }
}
