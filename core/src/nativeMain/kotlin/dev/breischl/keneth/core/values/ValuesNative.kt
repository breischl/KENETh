package dev.breischl.keneth.core.values

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable(with = AmountSerializer::class)
actual value class Amount(actual val value: Double) {
    actual companion object {
        actual const val TYPE_ID: Int = TypeIds.AMOUNT
    }
}

@Serializable(with = BinarySerializer::class)
actual value class Binary(actual val bytes: ByteArray) {
    actual companion object {
        actual const val TYPE_ID: Int = TypeIds.BINARY
    }
}

@Serializable(with = CurrencySerializer::class)
actual value class Currency(actual val code: String) {
    actual companion object {
        actual const val TYPE_ID: Int = TypeIds.CURRENCY
    }
}

@Serializable(with = CurrentSerializer::class)
actual value class Current(actual val amperes: Double) {
    actual companion object {
        actual const val TYPE_ID: Int = TypeIds.CURRENT
    }
}

@Serializable(with = DurationSerializer::class)
actual value class Duration(actual val millis: Long) {
    actual companion object {
        actual const val TYPE_ID: Int = TypeIds.DURATION
    }
}

@Serializable(with = EnergySerializer::class)
actual value class Energy(actual val wattHours: Double) {
    actual companion object {
        actual const val TYPE_ID: Int = TypeIds.ENERGY
    }
}

@Serializable(with = FlagSerializer::class)
actual value class Flag(actual val value: Boolean) {
    actual companion object {
        actual const val TYPE_ID: Int = TypeIds.FLAG
    }
}

@Serializable(with = PercentageSerializer::class)
actual value class Percentage(actual val percent: Double) {
    actual companion object {
        actual const val TYPE_ID: Int = TypeIds.PERCENTAGE
    }
}

@Serializable(with = PowerSerializer::class)
actual value class Power(actual val watts: Double) {
    actual companion object {
        actual const val TYPE_ID: Int = TypeIds.POWER
    }
}

@Serializable(with = ResistanceSerializer::class)
actual value class Resistance(actual val ohms: Double) {
    actual companion object {
        actual const val TYPE_ID: Int = TypeIds.RESISTANCE
    }
}

@Serializable(with = TextSerializer::class)
actual value class Text(actual val value: String) {
    actual companion object {
        actual const val TYPE_ID: Int = TypeIds.TEXT
    }
}

@Serializable(with = TimestampSerializer::class)
actual value class Timestamp(actual val instant: Instant) {
    actual companion object {
        actual const val TYPE_ID: Int = TypeIds.TIMESTAMP
    }
}

@Serializable(with = VoltageSerializer::class)
actual value class Voltage(actual val volts: Double) {
    actual companion object {
        actual const val TYPE_ID: Int = TypeIds.VOLTAGE
    }
}
