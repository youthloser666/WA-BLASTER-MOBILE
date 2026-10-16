package com.example.wablaster.data.local

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class Converters {

    @TypeConverter
    fun fromMapToString(map: Map<String, String>?): String {
        if (map.isNullOrEmpty()) return "{}"
        val json = JSONObject()
        for ((key, value) in map) {
            json.put(key, value)
        }
        return json.toString()
    }

    @TypeConverter
    fun fromStringToMap(jsonString: String?): Map<String, String> {
        if (jsonString.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(jsonString)
            val result = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.optString(key, "")
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    @TypeConverter
    fun fromListToString(list: List<String>?): String {
        if (list.isNullOrEmpty()) return "[]"
        val sb = StringBuilder("[")
        for (i in list.indices) {
            val item = list[i]
            sb.append("\"")
            for (ch in item) {
                when (ch) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> sb.append(ch)
                }
            }
            sb.append("\"")
            if (i < list.size - 1) sb.append(",")
        }
        sb.append("]")
        return sb.toString()
    }

    @TypeConverter
    fun fromStringToList(jsonString: String?): List<String> {
        if (jsonString.isNullOrBlank()) return emptyList()
        val trimmed = jsonString.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val content = trimmed.substring(1, trimmed.length - 1).trim()
        if (content.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var escaping = false

        for (ch in content) {
            if (escaping) {
                when (ch) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> sb.append(ch)
                }
                escaping = false
            } else if (ch == '\\') {
                escaping = true
            } else if (ch == '"') {
                inQuotes = !inQuotes
                if (!inQuotes) {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
            } else if (inQuotes) {
                sb.append(ch)
            }
        }
        return result
    }
}
