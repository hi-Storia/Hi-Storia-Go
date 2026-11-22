package it.historia.player.data.preferences

import android.content.SharedPreferences

/**
 * Insieme delle chiavi di un file SharedPreferences che appartengono a un progetto.
 *
 * Ogni store dichiara quali delle proprie chiavi appartengono al progetto: l'importazione usa
 * questa informazione per eliminare i residui del progetto precedente e per il backup di
 * sicurezza (rollback). Non è usato per il formato di esportazione, che ha uno schema esplicito.
 */
class ProjectPreferenceSection(
    val preferencesName: String,
    private val prefs: SharedPreferences,
    private val ownsKey: (String) -> Boolean
) {
    fun entries(): Map<String, Any?> = prefs.all.filterKeys(ownsKey)

    /** Rimuove tutte le chiavi del progetto e scrive [values] in un unico commit sincrono. */
    fun replaceAll(values: Map<String, Any?>): Boolean {
        val editor = prefs.edit()
        prefs.all.keys.filter(ownsKey).forEach(editor::remove)
        values.forEach { (key, value) ->
            require(ownsKey(key)) { "La chiave $key non appartiene alla sezione $preferencesName" }
            editor.putTyped(key, value)
        }
        return editor.commit()
    }

    fun clear(): Boolean = replaceAll(emptyMap())
}

internal fun SharedPreferences.Editor.putTyped(key: String, value: Any?): SharedPreferences.Editor {
    return when (value) {
        null -> remove(key)
        is String -> putString(key, value)
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Float -> putFloat(key, value)
        is Boolean -> putBoolean(key, value)
        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        else -> error("Tipo non supportato per la chiave $key")
    }
}
