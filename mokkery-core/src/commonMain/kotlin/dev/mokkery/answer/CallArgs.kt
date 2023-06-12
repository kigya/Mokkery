package dev.mokkery.answer

@Suppress("UNCHECKED_CAST")
public class CallArgs(public val args: List<Any?>) {

    override fun toString(): String = args.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CallArgs

        return args == other.args
    }

    override fun hashCode(): Int {
        return args.hashCode()
    }

    public operator fun <T> component1(): T = args[0] as T
    public operator fun <T> component2(): T = args[1] as T
    public operator fun <T> component3(): T = args[2] as T
    public operator fun <T> component4(): T = args[3] as T
    public operator fun <T> component5(): T = args[4] as T
    public operator fun <T> component6(): T = args[5] as T
    public operator fun <T> component7(): T = args[6] as T
}
