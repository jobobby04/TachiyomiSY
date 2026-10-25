package mihon.core.archive

class ArchiveEntry(
    val name: String,
    val isFile: Boolean,
    // SY -->
    val isEncrypted: Boolean,
    // SY <--
)
