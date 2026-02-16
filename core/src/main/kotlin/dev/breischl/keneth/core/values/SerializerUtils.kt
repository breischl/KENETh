package dev.breischl.keneth.core.values

import kotlinx.serialization.KSerializer
import net.orandja.obor.codec.Cbor
import net.orandja.obor.data.*

/**
 * Common CBOR utilities for EnergyNet Protocol value types using OBOR's CborObject.
 *
 * Value types in the EnergyNet Protocol are serialized as CBOR maps with a single entry,
 * where the key is the type ID (as a plain CBOR unsigned integer) and the value is the
 * actual data. For example, a Voltage value of 824V is serialized as `{ 0x10: 824 }`.
 *
 * OBOR's CborObject handles flexible numeric types natively — any CBOR numeric type
 * (int, uint, float16, float32, float64) can be decoded and converted to the target type.
 */
internal object SerializerUtils {

    /** Extract a numeric CborObject value as Double, regardless of CBOR encoding. */
    fun CborObject.toDoubleValue(): Double = when (this) {
        is CborPositive -> value.toDouble()
        is CborNegative -> (-1.0 - value.toDouble())
        is CborFloat -> value
        else -> error("Not a numeric CborObject: ${this::class.simpleName}")
    }

    /** Extract a numeric CborObject value as Long, regardless of CBOR encoding. */
    fun CborObject.toLongValue(): Long = when (this) {
        is CborPositive -> value.toLong()
        is CborNegative -> (-1L - value.toLong())
        is CborFloat -> value.toLong()
        else -> error("Not a numeric CborObject: ${this::class.simpleName}")
    }

    /** Look up a value in a CborMap by integer key. */
    fun CborMap.getByIntKey(key: Int): CborObject? {
        val cborKey = CborObject.positive(key)
        return asMap[cborKey]
    }

    /** Build a single-entry CBOR map with an int key and a Double value. */
    fun buildDoubleMap(typeId: Int, value: Double): CborMap {
        return CborObject.buildMap {
            put(CborObject.positive(typeId), CborObject.value(value))
        }
    }

    /** Build a single-entry CBOR map with an int key and a Long value. */
    fun buildLongMap(typeId: Int, value: Long): CborMap {
        return CborObject.buildMap {
            put(CborObject.positive(typeId), CborObject.value(value))
        }
    }

    /** Build a single-entry CBOR map with an int key and a String value. */
    fun buildStringMap(typeId: Int, value: String): CborMap {
        return CborObject.buildMap {
            put(CborObject.positive(typeId), CborObject.value(value))
        }
    }

    /** Build a single-entry CBOR map with an int key and a Boolean value. */
    fun buildBooleanMap(typeId: Int, value: Boolean): CborMap {
        return CborObject.buildMap {
            put(CborObject.positive(typeId), CborObject.value(value))
        }
    }

    /** Build a single-entry CBOR map with an int key and a ByteArray value. */
    fun buildByteArrayMap(typeId: Int, value: ByteArray): CborMap {
        return CborObject.buildMap {
            put(CborObject.positive(typeId), CborObject.value(value))
        }
    }

    /** Decode a CborMap from a CborObject, extracting the single-entry map's value by int key. */
    fun CborObject.asCborMap(): CborMap {
        return this as? CborMap ?: error("Expected CborMap, got ${this::class.simpleName}")
    }

    /** Extract a String value from a CborObject. */
    fun CborObject.toStringValue(): String {
        return (this as? CborText)?.value ?: error("Expected CborText, got ${this::class.simpleName}")
    }

    /** Extract a Boolean value from a CborObject. */
    fun CborObject.toBooleanValue(): Boolean {
        return (this as? CborBoolean)?.value ?: error("Expected CborBoolean, got ${this::class.simpleName}")
    }

    /** Extract a ByteArray value from a CborObject. */
    fun CborObject.toByteArrayValue(): ByteArray {
        return (this as? CborBytes)?.value ?: error("Expected CborBytes, got ${this::class.simpleName}")
    }

    private val cbor = Cbor { ingnoreUnknownKeys = true }

    /** Convert a value to its CborObject representation using the given serializer. */
    fun <T> toCborObject(serializer: KSerializer<T>, value: T): CborObject {
        val bytes = cbor.encodeToByteArray(serializer, value)
        return cbor.decodeFromByteArray(CborObject.serializer(), bytes)
    }

    /** Convert a CborObject back to a value using the given serializer. */
    fun <T> fromCborObject(serializer: KSerializer<T>, obj: CborObject): T {
        val bytes = cbor.encodeToByteArray(CborObject.serializer(), obj)
        return cbor.decodeFromByteArray(serializer, bytes)
    }
}
