package com.example.universal.edge.data.converter

import androidx.room.TypeConverter

class FloatArrayConverter {
    @TypeConverter
    fun fromFloatArray(value: FloatArray?): String? {
        return value?.joinToString(",") { it.toString() }
    }

    @TypeConverter
    fun toFloatArray(value: String?): FloatArray? {
        if (value.isNullOrBlank()) return null
        return value.split(",").map { it.toFloat() }.toFloatArray()
    }
}
