package dev.inkdot.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class ReaderMode { LTR, RTL, VERTICAL }

class Pref<T>(
    private val sp: SharedPreferences,
    private val key: String,
    default: T,
    read: (SharedPreferences, String, T) -> T,
    private val write: (SharedPreferences.Editor, String, T) -> Unit,
) {
    private val state = MutableStateFlow(read(sp, key, default))
    val flow: StateFlow<T> = state

    var value: T
        get() = state.value
        set(v) {
            state.value = v
            sp.edit { write(this, key, v) }
        }
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("inkdot", Context.MODE_PRIVATE)

    val theme = enumPref("theme", ThemeMode.DARK)
    val amoled = boolPref("amoled", true)
    val readerMode = enumPref("reader_mode", ReaderMode.LTR)
    val glassFlip = boolPref("glass_flip", true)

    private fun boolPref(key: String, default: Boolean) =
        Pref(sp, key, default, { s, k, d -> s.getBoolean(k, d) }, { e, k, v -> e.putBoolean(k, v) })

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E) = Pref(
        sp, key, default,
        { s, k, d -> runCatching { enumValueOf<E>(s.getString(k, null) ?: d.name) }.getOrDefault(d) },
        { e, k, v -> e.putString(k, v.name) },
    )
}
