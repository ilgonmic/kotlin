/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.time

import kotlin.math.abs

private val storageUnit = DurationUnit.NANOSECONDS

@Suppress("NON_PUBLIC_PRIMARY_CONSTRUCTOR_OF_INLINE_CLASS")
public inline class Duration internal constructor(internal val _value: Double) : Comparable<Duration> {
// TODO: backend fails on init block, wait for KT-28055

//    init {
//        require(_value.isNaN().not())
//    }

    companion object {
        val ZERO: Duration = Duration(0.0)
        val MAX_VALUE: Duration = Duration(Double.MAX_VALUE)
        val MIN_VALUE: Duration = Duration(-Double.MAX_VALUE)
        val INFINITE: Duration = Duration(Double.POSITIVE_INFINITY)


        // constructing from number of units
        // option 1: static-like constructor functions

        fun days(value: Int) = Duration(value, DurationUnit.DAYS)
        fun days(value: Long) = Duration(value, DurationUnit.DAYS)
        fun days(value: Double) = Duration(value, DurationUnit.DAYS)
        fun hours(value: Int) = Duration(value, DurationUnit.HOURS)
        fun hours(value: Long) = Duration(value, DurationUnit.HOURS)
        fun hours(value: Double) = Duration(value, DurationUnit.HOURS)
        fun minutes(value: Int) = Duration(value, DurationUnit.MINUTES)
        fun minutes(value: Long) = Duration(value, DurationUnit.MINUTES)
        fun minutes(value: Double) = Duration(value, DurationUnit.MINUTES)
        fun seconds(value: Int) = Duration(value, DurationUnit.SECONDS)
        fun seconds(value: Long) = Duration(value, DurationUnit.SECONDS)
        fun seconds(value: Double) = Duration(value, DurationUnit.SECONDS)
        fun milliseconds(value: Int) = Duration(value, DurationUnit.MILLISECONDS)
        fun milliseconds(value: Long) = Duration(value, DurationUnit.MILLISECONDS)
        fun milliseconds(value: Double) = Duration(value, DurationUnit.MILLISECONDS)
        fun microseconds(value: Int) = Duration(value, DurationUnit.MICROSECONDS)
        fun microseconds(value: Long) = Duration(value, DurationUnit.MICROSECONDS)
        fun microseconds(value: Double) = Duration(value, DurationUnit.MICROSECONDS)
        fun nanoseconds(value: Int) = Duration(value, DurationUnit.NANOSECONDS)
        fun nanoseconds(value: Long) = Duration(value, DurationUnit.NANOSECONDS)
        fun nanoseconds(value: Double) = Duration(value, DurationUnit.NANOSECONDS)
    }


    // constructing from number of units
    // option 0: constructors

    // unit is lost after construction

    constructor(value: Int, unit: DurationUnit) : this(convertDurationUnit(value.toDouble(), unit, storageUnit))
    constructor(value: Long, unit: DurationUnit) : this(convertDurationUnit(value.toDouble(), unit, storageUnit))
    constructor(value: Double, unit: DurationUnit) : this(convertDurationUnit(value, unit, storageUnit))


    // arithmetic operators

    operator fun unaryMinus(): Duration = Duration(-_value)
    operator fun plus(other: Duration): Duration = Duration(_value + other._value)
    operator fun minus(other: Duration): Duration = Duration(_value - other._value)

    // should we declare symmetric extension operators?

    operator fun times(scale: Int): Duration = Duration(_value * scale)
    operator fun times(scale: Double): Duration = Duration(_value * scale)

    operator fun div(scale: Int): Duration = Duration(_value / scale)
    operator fun div(scale: Double): Duration = Duration(_value / scale)

    operator fun div(other: Duration): Double = this._value / other._value

    fun isNegative(): Boolean = _value < 0
    fun isInfinite(): Boolean = _value.isInfinite()
    fun isFinite(): Boolean = _value.isFinite()

    fun absoluteValue(): Duration = if (isNegative()) -this else this


    override fun compareTo(other: Duration): Int = this._value.compareTo(other._value)


    // splitting to components

    // problem: withComponents can be confused with 'wither' function
    // perhaps better name would be 'letComponents'

    inline fun <T> withComponents(action: (hours: Int, minutes: Int, seconds: Int, nanoseconds: Int) -> T): T =
        action(inHours.toInt(), minutesComponent, secondsComponent, nanosecondsComponent)

    inline fun <T> withComponents(action: (days: Int, hours: Int, minutes: Int, seconds: Int, nanoseconds: Int) -> T): T =
        action(inDays.toInt(), hoursComponent, minutesComponent, secondsComponent, nanosecondsComponent)


    @PublishedApi
    internal val hoursComponent: Int get() = (inHours % 24).toInt()
    @PublishedApi
    internal val minutesComponent: Int get() = (inMinutes % 60).toInt()
    @PublishedApi
    internal val secondsComponent: Int get() = (inSeconds % 60).toInt()
    @PublishedApi
    internal val nanosecondsComponent: Int get() = (inNanoseconds % 1e9).toInt()


    // conversion to units

    // option 1: in- properties

    val inDays: Double get() = inUnits(DurationUnit.DAYS)
    val inHours: Double get() = inUnits(DurationUnit.HOURS)
    val inMinutes: Double get() = inUnits(DurationUnit.MINUTES)
    val inSeconds: Double get() = inUnits(DurationUnit.SECONDS)
    val inMilliseconds: Double get() = inUnits(DurationUnit.MILLISECONDS)
    val inMicroseconds: Double get() = inUnits(DurationUnit.MICROSECONDS)
    val inNanoseconds: Double get() = inUnits(DurationUnit.NANOSECONDS)

    fun inUnits(unit: DurationUnit) = convertDurationUnit(_value, storageUnit, unit)

    // option 2: to- functions

    fun toDays(): Double = toUnits(DurationUnit.DAYS)
    fun toHours(): Double = toUnits(DurationUnit.HOURS)
    fun toMinutes(): Double = toUnits(DurationUnit.MINUTES)
    fun toSeconds(): Double = toUnits(DurationUnit.SECONDS)
    fun toMilliseconds(): Double = toUnits(DurationUnit.MILLISECONDS)
    fun toMicroseconds(): Double = toUnits(DurationUnit.MICROSECONDS)
    fun toNanoseconds(): Double = toUnits(DurationUnit.NANOSECONDS)

    fun toUnits(unit: DurationUnit): Double = convertDurationUnit(_value, storageUnit, unit)


    override fun toString(): String = buildString {
        if (isInfinite()) {
            append(_value)
        } else {
            val absNs = absoluteValue().inNanoseconds
            var scientific = false
            var maxDecimals = 0
            val unit = when {
                absNs == 0.0 -> return "0s"
                absNs < 1e-6 -> DurationUnit.SECONDS.also { scientific = true }
                absNs < 1 -> DurationUnit.NANOSECONDS.also { maxDecimals = 7 }
                absNs < 1e3 -> DurationUnit.NANOSECONDS
                absNs < 1e6 -> DurationUnit.MICROSECONDS
                absNs < 1e9 -> DurationUnit.MILLISECONDS
                absNs < 1000e9 -> DurationUnit.SECONDS
                absNs < 60_000e9 -> DurationUnit.MINUTES
                absNs < 3600_000e9 -> DurationUnit.HOURS
                absNs < 86400e9 * 1e7 -> DurationUnit.DAYS
                else -> DurationUnit.DAYS.also { scientific = true }
            }
            val value = inUnits(unit)
            return when {
                scientific -> formatScientific(value)
                maxDecimals > 0 -> formatUpToDecimals(value, maxDecimals)
                else -> formatToExactDecimals(value, precision(abs(value)))
            } + unit.shortName()
        }
    }

    private fun precision(value: Double): Int = when {
        value < 1 -> 3
        value < 10 -> 2
        value < 100 -> 1
        else -> 0
    }

    fun toString(unit: DurationUnit, decimals: Int = 0): String {
        require(decimals >= 0) { "decimals must be not negative, but was $decimals" }
        if (isInfinite()) return _value.toString()
        return formatToExactDecimals(inUnits(unit), decimals) + unit.shortName()
    }


    fun toIsoString(): String = buildString {
        if (isNegative()) append('-')
        append('P')
        absoluteValue().withComponents { days, hours, minutes, seconds, nanoseconds ->
            if (days != 0)
                append(days).append('D')


            if (days == 0 || seconds != 0 || nanoseconds != 0 || minutes != 0 || hours != 0) {
                append('T')
                val hasHours = hours != 0 || days != 0
                val hasSeconds = seconds != 0 || nanoseconds != 0
                val hasMinutes = minutes != 0 || (hasSeconds && hasHours)
                if (hasHours) {
                    append(hours).append('H')
                }
                if (hasMinutes) {
                    append(minutes).append('M')
                }
                if (hasSeconds || (!hasHours && !hasMinutes)) {
                    append(seconds)
                    if (nanoseconds != 0) {
                        append('.')
                        val nss = nanoseconds.toString().padStart(9, '0')
                        when {
                            nanoseconds % 1_000_000 == 0 -> append(nss, 0, 3)
                            nanoseconds % 1_000 == 0 -> append(nss, 0, 6)
                            else -> append(nss)
                        }
                    }
                    append('S')
                }
            }
        }
    }


}

// constructing from number of units
// option 2: constructor extension functions


val Int.nanoseconds get() = Duration(this, DurationUnit.NANOSECONDS)
val Long.nanoseconds get() = Duration(this, DurationUnit.NANOSECONDS)
val Double.nanoseconds get() = Duration(this, DurationUnit.NANOSECONDS)

val Int.microseconds get() = Duration(this, DurationUnit.MICROSECONDS)
val Long.microseconds get() = Duration(this, DurationUnit.MICROSECONDS)
val Double.microseconds get() = Duration(this, DurationUnit.MICROSECONDS)

val Int.milliseconds get() = Duration(this, DurationUnit.MILLISECONDS)
val Long.milliseconds get() = Duration(this, DurationUnit.MILLISECONDS)
val Double.milliseconds get() = Duration(this, DurationUnit.MILLISECONDS)

val Int.seconds get() = Duration(this, DurationUnit.SECONDS)
val Long.seconds get() = Duration(this, DurationUnit.SECONDS)
val Double.seconds get() = Duration(this, DurationUnit.SECONDS)

val Int.minutes get() = Duration(this, DurationUnit.MINUTES)
val Long.minutes get() = Duration(this, DurationUnit.MINUTES)
val Double.minutes get() = Duration(this, DurationUnit.MINUTES)

val Int.hours get() = Duration(this, DurationUnit.HOURS)
val Long.hours get() = Duration(this, DurationUnit.HOURS)
val Double.hours get() = Duration(this, DurationUnit.HOURS)

val Int.days get() = Duration(this, DurationUnit.DAYS)
val Long.days get() = Duration(this, DurationUnit.DAYS)
val Double.days get() = Duration(this, DurationUnit.DAYS)



internal expect fun formatToExactDecimals(value: Double, decimals: Int): String
internal expect fun formatUpToDecimals(value: Double, decimals: Int): String
internal expect fun formatScientific(value: Double): String