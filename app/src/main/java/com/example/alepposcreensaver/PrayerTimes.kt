package com.example.alepposcreensaver

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class PrayerTime(val name: String, val instant: Instant, val isPrayer: Boolean = true)

object PrayerTimes {
    private const val LATITUDE = 36.2021
    private const val LONGITUDE = 37.1343
    private const val FAJR_ANGLE = 18.0
    private const val ISHA_ANGLE = 17.0
    private const val SUNRISE_ALTITUDE = -0.833

    fun forDate(date: LocalDate, zone: ZoneId): List<PrayerTime> {
        val dayOfYear = date.dayOfYear.toDouble()
        val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1.0)
        val equationOfTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2.0 * gamma) -
                0.040849 * sin(2.0 * gamma)
            )
        val declination =
            0.006918 -
                0.399912 * cos(gamma) +
                0.070257 * sin(gamma) -
                0.006758 * cos(2.0 * gamma) +
                0.000907 * sin(2.0 * gamma) -
                0.002697 * cos(3.0 * gamma) +
                0.00148 * sin(3.0 * gamma)

        val solarNoonUtcMinutes = 720.0 - 4.0 * LONGITUDE - equationOfTime
        val dayStartUtc = date.atStartOfDay(ZoneId.of("UTC")).toInstant()

        fun timeAtSolarOffset(offsetMinutes: Double): Instant =
            dayStartUtc.plusMillis((offsetMinutes * 60_000.0).toLong())

        fun hourAngle(altitudeDegrees: Double): Double {
            val latitudeRadians = Math.toRadians(LATITUDE)
            val altitudeRadians = Math.toRadians(altitudeDegrees)
            val cosine =
                (sin(altitudeRadians) - sin(latitudeRadians) * sin(declination)) /
                    (cos(latitudeRadians) * cos(declination))
            return Math.toDegrees(acos(cosine.coerceIn(-1.0, 1.0))) * 4.0
        }

        val asrAltitude = Math.toDegrees(
            atan(1.0 / (1.0 + tan(Math.abs(Math.toRadians(LATITUDE) - declination))))
        )

        return listOf(
            PrayerTime("الفجر", timeAtSolarOffset(solarNoonUtcMinutes - hourAngle(-FAJR_ANGLE))),
            PrayerTime(
                "الشروق",
                timeAtSolarOffset(solarNoonUtcMinutes - hourAngle(SUNRISE_ALTITUDE)),
                isPrayer = false
            ),
            PrayerTime("الظهر", timeAtSolarOffset(solarNoonUtcMinutes + 2.0)),
            PrayerTime("العصر", timeAtSolarOffset(solarNoonUtcMinutes + hourAngle(asrAltitude))),
            PrayerTime("المغرب", timeAtSolarOffset(solarNoonUtcMinutes + hourAngle(SUNRISE_ALTITUDE))),
            PrayerTime("العشاء", timeAtSolarOffset(solarNoonUtcMinutes + hourAngle(-ISHA_ANGLE)))
        ).map { it.copy(instant = it.instant.atZone(zone).toInstant()) }
    }
}
