package exh.util

/**
 * SY-specific file
 */
fun <C : Collection<R>, R> C.nullIfEmpty() = ifEmpty { null }
