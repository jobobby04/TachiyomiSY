package exh.eh

/**
 * SY-specific file
 */
class GalleryNotUpdatedException(val network: Boolean, cause: Throwable) : RuntimeException(cause)
