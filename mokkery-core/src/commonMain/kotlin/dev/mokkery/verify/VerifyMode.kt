package dev.mokkery.verify

public sealed class VerifyMode {

    internal object Not : VerifyMode()
    internal object Order : VerifyMode()
    internal object ExhaustiveOrder : VerifyMode()
    internal object ExhaustiveSoft : VerifyMode()
    internal data class Soft(val atLeast: Int, val atMost: Int) : VerifyMode()

    public companion object {

        public val not: VerifyMode = Not
        public val order: VerifyMode = Order
        public val exhaustiveOrder: VerifyMode = ExhaustiveOrder
        public val exhaustive: VerifyMode = ExhaustiveSoft
        public val soft: VerifyMode = Soft(atLeast = 1, atMost = Int.MAX_VALUE)
        public fun atLeast(value: Int): VerifyMode = Soft(atLeast = value, atMost = Int.MAX_VALUE)
        public fun atMost(value: Int): VerifyMode = Soft(atLeast = 1, atMost = value)
        public fun exactly(value: Int): VerifyMode = Soft(atLeast = value, atMost = value)
    }
}
