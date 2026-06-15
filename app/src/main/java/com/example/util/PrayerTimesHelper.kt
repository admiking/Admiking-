package com.example.util

import java.text.SimpleDateFormat
import java.util.*

data class PrayerTime(val name: String, val arabicName: String, val time: String)

object PrayerTimesHelper {
    val cities = listOf("مكة المكرمة", "الرياض", "القاهرة", "دبي", "القدس الشريف", "عمان", "بغداد")

    val cityCoordinates = mapOf(
        "مكة المكرمة" to Pair(21.3891, 39.8579),
        "الرياض" to Pair(24.7136, 46.6753),
        "القاهرة" to Pair(30.0444, 31.2357),
        "دبي" to Pair(25.2048, 55.2708),
        "القدس الشريف" to Pair(31.7683, 35.2137),
        "عمان" to Pair(31.9454, 35.9284),
        "بغداد" to Pair(33.3152, 44.3661)
    )

    fun getPrayerTimesForCoordinates(latitude: Double, longitude: Double, date: Date = Date()): List<PrayerTime> {
        // Find closest reference city using Euclidean distance
        var nearestCityName = "مكة المكرمة"
        var minDistance = Double.MAX_VALUE
        cityCoordinates.forEach { (city, coords) ->
            val dist = java.lang.Math.sqrt(
                java.lang.Math.pow(latitude - coords.first, 2.0) +
                java.lang.Math.pow(longitude - coords.second, 2.0)
            )
            if (dist < minDistance) {
                minDistance = dist
                nearestCityName = city
            }
        }

        // Fetch standard times for the closest reference city
        val baseTimes = getPrayerTimesForCity(nearestCityName, date)

        // Longitudinal shift offset: 1 degree difference equals 4 minutes of solar time adjustment
        // east is earlier (-), west is later (+)
        val refCoords = cityCoordinates[nearestCityName] ?: Pair(21.3891, 39.8579)
        val longitudeDifference = longitude - refCoords.second
        val longitudeTimeShift = -kotlin.math.round(longitudeDifference * 4.0).toInt()

        val sdf = SimpleDateFormat("HH:mm", Locale.US)

        return baseTimes.map { prayer ->
            try {
                val parts = prayer.time.split(":")
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()

                val cal = Calendar.getInstance().apply {
                    this.time = date
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }
                cal.add(Calendar.MINUTE, longitudeTimeShift)
                val finalTimeStr = sdf.format(cal.time)
                prayer.copy(time = finalTimeStr)
            } catch (e: Exception) {
                prayer
            }
        }
    }

    fun getPrayerTimesForCity(cityName: String, date: Date = Date()): List<PrayerTime> {
        val calendar = Calendar.getInstance().apply { time = date }
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        
        // Add a seasonal offset using sine wave to simulate real seasonal daylight changes
        val seasonalShiftMinutes = (kotlin.math.sin(dayOfYear * 2 * Math.PI / 365.1) * 35).toInt()

        val baseTimes = when (cityName) {
            "مكة المكرمة" -> listOf(
                Pair("Fajr", "04:52"),
                Pair("Shorouk", "06:10"),
                Pair("Dhuhr", "12:25"),
                Pair("Asr", "15:40"),
                Pair("Maghrib", "18:41"),
                Pair("Isha", "20:11")
            )
            "الرياض" -> listOf(
                Pair("Fajr", "04:30"),
                Pair("Shorouk", "05:52"),
                Pair("Dhuhr", "11:55"),
                Pair("Asr", "15:10"),
                Pair("Maghrib", "17:58"),
                Pair("Isha", "19:28")
            )
            "القاهرة" -> listOf(
                Pair("Fajr", "04:10"),
                Pair("Shorouk", "05:42"),
                Pair("Dhuhr", "11:50"),
                Pair("Asr", "15:25"),
                Pair("Maghrib", "17:58"),
                Pair("Isha", "19:30")
            )
            "دبي" -> listOf(
                Pair("Fajr", "04:40"),
                Pair("Shorouk", "06:00"),
                Pair("Dhuhr", "12:10"),
                Pair("Asr", "15:30"),
                Pair("Maghrib", "18:20"),
                Pair("Isha", "19:50")
            )
            "القدس الشريف" -> listOf(
                Pair("Fajr", "04:15"),
                Pair("Shorouk", "05:50"),
                Pair("Dhuhr", "11:52"),
                Pair("Asr", "15:32"),
                Pair("Maghrib", "17:55"),
                Pair("Isha", "19:25")
            )
            "عمان" -> listOf(
                Pair("Fajr", "04:18"),
                Pair("Shorouk", "05:53"),
                Pair("Dhuhr", "11:55"),
                Pair("Asr", "15:33"),
                Pair("Maghrib", "17:57"),
                Pair("Isha", "19:27")
            )
            "بغداد" -> listOf(
                Pair("Fajr", "04:10"),
                Pair("Shorouk", "05:45"),
                Pair("Dhuhr", "11:55"),
                Pair("Asr", "15:36"),
                Pair("Maghrib", "18:02"),
                Pair("Isha", "19:32")
            )
            else -> listOf(
                Pair("Fajr", "04:30"),
                Pair("Shorouk", "05:50"),
                Pair("Dhuhr", "12:00"),
                Pair("Asr", "15:20"),
                Pair("Maghrib", "18:10"),
                Pair("Isha", "19:40")
            )
        }

        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        
        return baseTimes.map { (name, timeStr) ->
            val parts = timeStr.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            
            val tempCal = Calendar.getInstance().apply {
                this.time = date
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }
            
            tempCal.add(Calendar.MINUTE, seasonalShiftMinutes)
            val finalTimeStr = sdf.format(tempCal.time)
            
            val arabicName = when (name) {
                "Fajr" -> "الفجر"
                "Shorouk" -> "الشروق"
                "Dhuhr" -> "الظهر"
                "Asr" -> "العصر"
                "Maghrib" -> "المغرب"
                "Isha" -> "العشاء"
                else -> name
            }
            
            PrayerTime(name, arabicName, finalTimeStr)
        }
    }
}
