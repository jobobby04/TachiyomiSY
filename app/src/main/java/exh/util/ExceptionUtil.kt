package exh.util

/**
 * SY-specific file
 */
inline fun <T> ignore(expr: () -> T): T? {
    return try {
        expr()
    } catch (t: Throwable) {
        null
    }
}

fun <T : Throwable> T.withRootCause(cause: Throwable): T {
    val curCause = this.cause

    if (curCause == null) {
        this.initCause(cause)
    } else {
        curCause.withRootCause(cause)
    }

    return this
}
