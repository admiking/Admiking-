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

    fun fetchPrayerTimesFromApi(
        latitude: Double?,
        longitude: Double?,
        cityName: String?,
        countryName: String?
    ): List<PrayerTime>? {
        // Try Aladhan API first (Standard Public Open API)
        try {
            val urlString = if (latitude != null && longitude != null) {
                "https://api.aladhan.com/v1/timings?latitude=$latitude&longitude=$longitude"
            } else if (!cityName.isNullOrBlank()) {
                val encCity = java.net.URLEncoder.encode(cityName, "UTF-8")
                val encCountry = java.net.URLEncoder.encode(countryName ?: "", "UTF-8")
                "https://api.aladhan.com/v1/timingsByCity?city=$encCity&country=$encCountry"
            } else {
                null
            }

            if (urlString != null) {
                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 6000
                connection.readTimeout = 6000
                connection.useCaches = false
                
                val responseCode = connection.responseCode
                if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                    val result = parsePrayerTimesFromJson(responseStr)
                    if (result != null && result.isNotEmpty()) {
                        return result
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to original custom API
        var connection: java.net.HttpURLConnection? = null
        try {
            val urlBuilder = java.lang.StringBuilder("https://quran.yousefheiba.com/api/getPrayerTimes")
            var hasQuery = false
            if (latitude != null && longitude != null) {
                urlBuilder.append("?latitude=").append(latitude)
                    .append("&longitude=").append(longitude)
                    .append("&lat=").append(latitude)
                    .append("&lng=").append(longitude)
                    .append("&lon=").append(longitude)
                hasQuery = true
            }
            if (!cityName.isNullOrBlank()) {
                val prefix = if (hasQuery) "&" else "?"
                urlBuilder.append(prefix).append("city=").append(java.net.URLEncoder.encode(cityName, "UTF-8"))
                if (!countryName.isNullOrBlank()) {
                    urlBuilder.append("&country=").append(java.net.URLEncoder.encode(countryName, "UTF-8"))
                }
            }

            val url = java.net.URL(urlBuilder.toString())
            connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            connection.useCaches = false
            
            val responseCode = connection.responseCode
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                return parsePrayerTimesFromJson(responseStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
        return null
    }

    fun parsePrayerTimesFromJson(jsonString: String): List<PrayerTime>? {
        try {
            val json = org.json.JSONObject(jsonString)
            var timingsObject: org.json.JSONObject? = null
            
            if (json.has("Fajr") || json.has("fajr")) {
                timingsObject = json
            } else if (json.has("data")) {
                val dataObj = json.optJSONObject("data")
                if (dataObj != null) {
                    if (dataObj.has("timings")) {
                        timingsObject = dataObj.optJSONObject("timings")
                    } else {
                        timingsObject = dataObj
                    }
                }
            } else if (json.has("timings")) {
                timingsObject = json.optJSONObject("timings")
            }

            if (timingsObject != null) {
                val prayerList = mutableListOf<PrayerTime>()
                val possibleNames = listOf(
                    Triple("Fajr", "الفجر", listOf("fajr", "Fajr", "fajr_time")),
                    Triple("Shorouk", "الشروق", listOf("shorouk", "Shorouk", "sunrise", "Sunrise", "shuruq")),
                    Triple("Dhuhr", "الظهر", listOf("dhuhr", "Dhuhr", "duhr", "Duhr", "dohr")),
                    Triple("Asr", "العصر", listOf("asr", "Asr")),
                    Triple("Maghrib", "المغرب", listOf("maghrib", "Maghrib")),
                    Triple("Isha", "العشاء", listOf("isha", "Isha"))
                )
                
                for ((engName, araName, keys) in possibleNames) {
                    var foundTime: String? = null
                    for (k in keys) {
                        if (timingsObject.has(k)) {
                            val raw = timingsObject.optString(k, "")
                            if (raw.isNotBlank()) {
                                val matcher = java.util.regex.Pattern.compile("(\\d{2}:\\d{2})").matcher(raw)
                                if (matcher.find()) {
                                    foundTime = matcher.group(1)
                                    break
                                }
                            }
                        }
                    }
                    if (foundTime != null) {
                        prayerList.add(PrayerTime(engName, araName, foundTime))
                    }
                }
                
                if (prayerList.size >= 5) {
                    return prayerList
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
