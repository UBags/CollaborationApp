package com.costheta.cortexa.util

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Type converters for Room database to handle Java 8 Date and Time API types (java.time.*).
 * Room does not natively support these types, so they need to be converted to and from
 * types that Room can understand (e.g., String for dates/times, Int for enums).
 */
class Converters {

    // --- LocalDate Converters ---
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // e.g., "2023-07-18"
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME // New formatter for LocalDateTime
    // --- LocalTime Converters ---
    private val timeFormatter = DateTimeFormatter.ISO_LOCAL_TIME // e.g., "10:30:00" or "10:30"

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? {
        return date?.format(dateFormatter)
    }

    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDate? {
        return dateString?.let { LocalDate.parse(it, dateFormatter) }
    }

    @TypeConverter
    fun fromLocalTime(time: LocalTime?): String? {
        return time?.format(timeFormatter)
    }

    @TypeConverter
    fun toLocalTime(timeString: String?): LocalTime? {
        return timeString?.let { LocalTime.parse(it, timeFormatter) }
    }

    // --- DayOfWeek Converters ---
    @TypeConverter
    fun fromDayOfWeek(day: DayOfWeek?): String? {
        return day?.name // Store as String (e.g., "MONDAY", "TUESDAY")
    }

    @TypeConverter
    fun toDayOfWeek(dayString: String?): DayOfWeek? {
        return dayString?.let { DayOfWeek.valueOf(it) }
    }

    // --- List<String> Converters (for selectedDaysOfWeek in Engagement) ---
    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.joinToString(",") // Convert list to a single comma-separated string
    }

    @TypeConverter
    fun toStringList(data: String?): List<String>? {
        return data?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } // Convert string back to list
    }

    // New Type Converters for LocalDateTime for the Attachment's timestamp
    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): String? {
        return dateTime?.format(dateTimeFormatter)
    }

    @TypeConverter
    fun toLocalDateTime(dateTimeString: String?): LocalDateTime? {
        return dateTimeString?.let { LocalDateTime.parse(it, dateTimeFormatter) } // <--- THIS WAS MISSING!
    }
}