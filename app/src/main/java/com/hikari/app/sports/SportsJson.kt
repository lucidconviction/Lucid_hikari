package com.hikari.app.sports

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * A small mutable JSON tree with org.json's reading and writing habits (opt* reads that coerce
 * strings to numbers and give "" or a fallback for anything missing, put() that chains), so the
 * TV app's feed parsers port line for line. Built on kotlinx.serialization for the actual text.
 */

/** org.json's explicit null value (a key that is present, but null). */
internal object JsonNullValue {
    override fun toString(): String = "null"
}

internal class JSONObject {
    private val map = java.util.LinkedHashMap<String, Any?>()

    constructor()
    constructor(text: String) : this(SportsJson.parseToJsonElement(text) as JsonObject)
    constructor(o: JsonObject) {
        o.forEach { (k, v) -> map[k] = fromElement(v) }
    }
    constructor(from: Map<*, *>) {
        from.forEach { (k, v) -> if (k != null) map[k.toString()] = if (v is JsonElement) fromElement(v) else wrap(v) }
    }

    fun put(key: String, value: Any?): JSONObject {
        if (value == null) map.remove(key) else map[key] = wrap(value)
        return this
    }

    fun opt(key: String): Any? = map[key]
    fun has(key: String): Boolean = map.containsKey(key)
    fun isNull(key: String): Boolean = map[key].let { it == null || it === JsonNullValue }
    fun keys(): Iterator<String> = map.keys.toList().iterator()
    val keySet: Set<String> get() = map.keys
    fun length(): Int = map.size
    fun remove(key: String): Any? = map.remove(key)
    fun toStringMap(): Map<String, String> = map.mapValues { (_, v) -> stringOf(v, "") }

    fun optString(key: String, fallback: String = ""): String = stringOf(map[key], fallback)
    fun optInt(key: String, fallback: Int = 0): Int = numberOf(map[key])?.toInt() ?: fallback
    fun optLong(key: String, fallback: Long = 0L): Long = numberOf(map[key])?.toLong() ?: fallback
    fun optDouble(key: String, fallback: Double = Double.NaN): Double = numberOf(map[key])?.toDouble() ?: fallback
    fun optBoolean(key: String, fallback: Boolean = false): Boolean = booleanOf(map[key]) ?: fallback
    fun optJSONObject(key: String): JSONObject? = map[key] as? JSONObject
    fun optJSONArray(key: String): JSONArray? = map[key] as? JSONArray

    fun getString(key: String): String = map[key]?.let { stringOf(it, "") } ?: throw NoSuchElementException(key)
    fun getInt(key: String): Int = numberOf(map[key])?.toInt() ?: throw NoSuchElementException(key)
    fun getJSONObject(key: String): JSONObject = optJSONObject(key) ?: throw NoSuchElementException(key)
    fun getJSONArray(key: String): JSONArray = optJSONArray(key) ?: throw NoSuchElementException(key)

    fun toElement(): JsonObject = JsonObject(map.mapValues { (_, v) -> toElement(v) })

    override fun toString(): String = SportsJson.stringify(toElement())

    private fun stringOf(v: Any?, fallback: String): String = when (v) {
        null -> fallback
        is String -> v
        is Number -> v.toString()
        is Boolean -> v.toString()
        else -> fallback
    }

    private fun numberOf(v: Any?): Number? = when (v) {
        is Number -> v
        is String -> v.toDoubleOrNull()
        else -> null
    }

    private fun booleanOf(v: Any?): Boolean? = when (v) {
        is Boolean -> v
        is String -> when (v.lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }
        is Number -> v != 0.0
        else -> null
    }

    private fun wrap(v: Any?): Any? = when (v) {
        is JSONObject, is JSONArray -> v
        is Map<*, *> -> JSONObject(v)
        is Iterable<*> -> JSONArray(v)
        is Boolean, is Number, is String, null -> v
        else -> v.toString()
    }

    private fun fromElement(e: JsonElement): Any? = when (e) {
        is JsonPrimitive -> when (e.content) {
            "true" -> true
            "false" -> false
            else -> try { e.content.toLong() } catch (_: Exception) { e.content.toDoubleOrNull()?.toLong() ?: e.content.toDoubleOrNull() ?: e.content as String }
        }
        is JsonObject -> JSONObject(e)
        is JsonArray -> JSONArray(e)
        is JsonNull -> JsonNullValue
        else -> null
    }

    private fun toElement(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is JSONObject -> v.toElement()
        is JSONArray -> v.toElement()
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is String -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }
}

internal class JSONArray {
    private val list = java.util.ArrayList<Any?>()

    constructor()
    constructor(elements: Iterable<*>) {
        elements.forEach { list.add(if (it is JsonElement) fromElement(it) else wrap(it)) }
    }
    constructor(text: String) : this(SportsJson.parseToJsonElement(text) as JsonArray)

    fun put(value: Any?): JSONArray { list.add(wrap(value)); return this }
    fun put(index: Int, value: Any?) { list.add(index, wrap(value)) }
    fun opt(index: Int): Any? = list.getOrNull(index)
    fun optString(index: Int, fallback: String = ""): String = stringOf(list.getOrNull(index), fallback)
    fun optInt(index: Int, fallback: Int = 0): Int = numberOf(list.getOrNull(index))?.toInt() ?: fallback
    fun optLong(index: Int, fallback: Long = 0L): Long = numberOf(list.getOrNull(index))?.toLong() ?: fallback
    fun optBoolean(index: Int, fallback: Boolean = false): Boolean = booleanOf(list.getOrNull(index)) ?: fallback
    fun optJSONObject(index: Int): JSONObject? = list.getOrNull(index) as? JSONObject
    fun optJSONArray(index: Int): JSONArray? = list.getOrNull(index) as? JSONArray
    fun length(): Int = list.size
    fun remove(index: Int): Any? = list.removeAt(index)

    fun strings(): List<String> = list.mapNotNull { it as? String }
    fun toList(): List<Any?> = list

    fun toElement(): JsonArray = JsonArray(list.map { toElement(it) })

    override fun toString(): String = SportsJson.stringify(toElement())

    private fun stringOf(v: Any?, fallback: String): String = when (v) {
        null -> fallback
        is String -> v
        is Number -> v.toString()
        is Boolean -> v.toString()
        else -> fallback
    }
    private fun numberOf(v: Any?): Number? = when (v) {
        is Number -> v
        is String -> v.toDoubleOrNull()
        else -> null
    }
    private fun booleanOf(v: Any?): Boolean? = when (v) {
        is Boolean -> v
        is String -> when (v.lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }
        is Number -> v != 0.0
        else -> null
    }
    private fun wrap(v: Any?): Any? = when (v) {
        is JSONObject, is JSONArray -> v
        is Map<*, *> -> JSONObject(v)
        is Iterable<*> -> JSONArray(v)
        is Boolean, is Number, is String, null -> v
        else -> v.toString()
    }
    private fun fromElement(e: JsonElement): Any? = when (e) {
        is JsonPrimitive -> when (e.content) {
            "true" -> true
            "false" -> false
            else -> try { e.content.toLong() } catch (_: Exception) { e.content.toDoubleOrNull()?.toLong() ?: e.content.toDoubleOrNull() ?: e.content as String }
        }
        is JsonObject -> JSONObject(e)
        is JsonArray -> JSONArray(e)
        is JsonNull -> JsonNullValue
        else -> null
    }
    private fun toElement(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is JSONObject -> v.toElement()
        is JSONArray -> v.toElement()
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is String -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }
}

internal object SportsJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseToJsonElement(text: String): JsonElement = json.parseToJsonElement(text)
    fun stringify(e: JsonElement): String = json.encodeToString(e)
}