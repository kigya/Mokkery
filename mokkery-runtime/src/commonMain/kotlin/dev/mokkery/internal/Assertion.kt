package dev.mokkery.internal

internal fun failAssertion(block: StringBuilder.() -> Unit): Nothing {
    throw AssertionError(StringBuilder().apply(block).toString())
}
