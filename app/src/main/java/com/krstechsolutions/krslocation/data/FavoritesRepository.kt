package com.krstechsolutions.krslocation.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.krstechsolutions.krslocation.model.LatLng
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Favorite(
    val id: String,
    val name: String,
    val point: LatLng,
    val createdAt: Long,
)

/** Saved places, stored as a JSON array in SharedPreferences. */
class FavoritesRepository(context: Context) {

    private val prefs = context.getSharedPreferences("krs_favorites", Context.MODE_PRIVATE)

    val favorites: SnapshotStateList<Favorite> = mutableStateListOf<Favorite>().also { it.addAll(load()) }

    fun add(name: String, point: LatLng): Favorite {
        val fav = Favorite(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { point.format(4) },
            point = point,
            createdAt = System.currentTimeMillis(),
        )
        favorites.add(0, fav)
        save()
        return fav
    }

    fun rename(id: String, name: String) {
        val index = favorites.indexOfFirst { it.id == id }
        if (index < 0) return
        favorites[index] = favorites[index].copy(name = name.trim().ifEmpty { favorites[index].name })
        save()
    }

    fun remove(id: String) {
        if (favorites.removeAll { it.id == id }) save()
    }

    private fun load(): List<Favorite> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Favorite(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name", ""),
                    point = LatLng(o.getDouble("lat"), o.getDouble("lng")),
                    createdAt = o.optLong("createdAt", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save() {
        val array = JSONArray()
        favorites.forEach { f ->
            array.put(
                JSONObject()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("lat", f.point.latitude)
                    .put("lng", f.point.longitude)
                    .put("createdAt", f.createdAt)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "favorites"
    }
}
