package dev.mokkery.test

interface BaseInterface {

    val baseInterfaceProperty: String
    fun baseInterfaceMethod()

    fun callWithDefault(value: Int): Int = value + 1
    suspend fun fetchWithDefault(value: Int): Int = value + 1
}
