package dev.breischl.keneth.core.values

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable(with = AmountSerializer::class)
actual value class Amount(actual val value: Double) {
    actual companion object {
        actual const val TYPE_ID: Int = 0x02
    }
}

@Serializable(with = BinarySerializer::class)
actual value class Binary(actual val bytes: ByteArray) {
    actual companion object {
        actual const val TYPE_ID: Int = 0x04
    }
}

@Serializable(with = CurrencySerializer::class)
actual value class Currency(actual val code: String) {
    actual companion object {
        actual const val TYPE_ID: Int = 0x05
    }
}

@Serializable(with = CurrentSerializer::class)
actual value class Current(actual val amperes: Double) {
    actual companion object {
        actual const val TYPE_ID: Int = 0x11
    }
}

@Serializable(with = DurationSerializer::class)
actual value class Duration(actual val millis: Long) {
    actual companion object {
        actual const val TYPE_ID: Int = 0x06
    }
}

@Serializable(with = EnergySerializer::class)
actual value class Energy(actual val wattHours: Double) {
    actual companion object {
        actual const val TYPE_ID: Int = 0x13
    }
}

@Serializable(with = FlagSerializer::class)
actual value class Flag(actual val value: Boolean) {
    actual companion object {
        actual const val TYPE_ID: Int = 0x01
    }
}

@Serializable(with = PercentageSerializer::class)
actual value class Percentage(actual val percent: Double) {
    actual companion object {
        actual const val TYPE_ID: Int = 0x14
    }
}

@Serializable(with = PowerSerializer::class)
actual value class Power(actual val watts: Double) {
    actual companion object {
        actual const val TYPE_ID: Int = 0x12
    }
}

@Serializable(with = ResistanceSerializer::class)
actual value class Resistance(actual val ohms: Double) {
    actual companion object {
        actual const val TYPE_ID: Int = 0x15
    }
}

@Serializable(with = TextSerializer::class)
actual value class Text(actual val value: String) {
    actual companion object {
        actual const val TYPE_ID: Int = 0x00
    }
}

@Serializable(with = TimestampSerializer::class)
actual value class Timestamp(actual val instant: Instant) {
    actual companion object {
        actual const val TYPE_ID: Int = 0x03
    }
}

@Serializable(with = VoltageSerializer::class)
actual value class Voltage(actual val volts: Double) {
    actual companion object {
        actual const val TYPE_ID: Int = 0x10
    }
}
