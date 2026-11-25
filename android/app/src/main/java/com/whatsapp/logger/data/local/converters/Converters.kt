package com.whatsapp.logger.data.local.converters

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromBoolean(value: Boolean?): Int = if (value == true) 1 else 0

    @TypeConverter
    fun toBoolean(value: Int): Boolean = value != 0
}