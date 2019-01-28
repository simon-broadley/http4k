package org.http4k.format

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.lens.BiDiBodyLensSpec
import org.http4k.lens.BiDiMapping
import org.http4k.lens.BiDiWsMessageLensSpec
import org.http4k.lens.ContentNegotiation
import org.http4k.lens.string
import org.http4k.websocket.WsMessage
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass

open class ConfigurableMoshi(builder: Moshi.Builder) : AutoMarshallingJson() {

    private val moshi: Moshi = builder.build()

    private fun <T> adapterFor(c: Class<T>): JsonAdapter<T> = moshi.adapter(c).failOnUnknown()

    override fun asJsonString(a: Any): String = adapterFor(a.javaClass).toJson(a)

    fun <T : Any> asJsonString(t: T, c: KClass<T>): String = adapterFor(c.java).toJson(t)

    override fun <T : Any> asA(s: String, c: KClass<T>): T = adapterFor(c.java).fromJson(s)!!

    inline fun <reified T : Any> asA(s: String): T = asA(s, T::class)

    inline fun <reified T : Any> Body.Companion.auto(description: String? = null, contentNegotiation: ContentNegotiation = ContentNegotiation.None): BiDiBodyLensSpec<T> =
        Body.string(ContentType.APPLICATION_JSON, description, contentNegotiation).map({ asA(it, T::class) }, { asJsonString(it) })

    inline fun <reified T : Any> WsMessage.Companion.auto(): BiDiWsMessageLensSpec<T> = WsMessage.string().map({ it.asA(T::class) }, { asJsonString(it) })
}

fun Moshi.Builder.asConfigurable() = object : AutoMappingConfiguration<Moshi.Builder> {
    override fun <OUT> number(mapping: BiDiMapping<BigInteger, OUT>) = adapter(mapping, { value(it) }, { nextLong().toBigInteger() })

    override fun <OUT> decimal(mapping: BiDiMapping<BigDecimal, OUT>) = adapter(mapping, { value(it) }, { nextDouble().toBigDecimal() })

    override fun <OUT> boolean(mapping: BiDiMapping<Boolean, OUT>) = adapter(mapping, { value(it) }, { nextBoolean() })

    override fun <OUT> text(mapping: BiDiMapping<String, OUT>) = adapter(mapping, { value(it) }, { nextString() })

    private fun <IN, OUT> adapter(mapping: BiDiMapping<IN, OUT>, write: JsonWriter.(IN) -> Unit, read: JsonReader.() -> IN) =
        apply {
            add(mapping.clazz, object : JsonAdapter<OUT>() {
                override fun fromJson(reader: JsonReader) = mapping.invoke(reader.read())

                override fun toJson(writer: JsonWriter, value: OUT?) {
                    value?.let { writer.write(mapping(it)) } ?: writer.nullValue()
                }
            })
        }

    // add the Kotlin adapter last, as it will hjiack our custom mappings otherwise
    override fun done(): Moshi.Builder = this@asConfigurable.add(KotlinJsonAdapterFactory())
}