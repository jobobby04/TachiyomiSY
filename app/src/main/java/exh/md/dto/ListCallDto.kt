package exh.md.dto

/**
 * SY-specific file
 */
interface ListCallDto<T> {
    val limit: Int
    val offset: Int
    val total: Int
    val data: List<T>
}
