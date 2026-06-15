package com.example.util

import java.text.SimpleDateFormat
import java.util.*

data class PrayerTime(val name: String, val arabicName: String, val time: String)

object PrayerTimesHelper {
    val cities = listOf("مكة المكرمة", "الرياض", "القاهرة", "دبي", "القدس الشريف", "عمان", "بغداد")

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
