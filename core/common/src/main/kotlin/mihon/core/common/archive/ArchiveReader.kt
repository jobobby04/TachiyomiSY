package mihon.core.common.archive

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.hippo.unifile.UniFile
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveException
import tachiyomi.core.common.storage.openFileDescriptor
import java.io.Closeable
import java.io.InputStream

class ArchiveReader(pfd: ParcelFileDescriptor) : Closeable {
    val size = pfd.statSize
    val address = Os.mmap(0, size, OsConstants.PROT_READ, OsConstants.MAP_PRIVATE, pfd.fileDescriptor, 0)

    // SY -->
    val encrypted by lazy { isEncrypted() }
    val wrongPassword by lazy { isPasswordIncorrect() }
    val archiveHashCode = pfd.hashCode()
    // SY <--

    inline fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T =
        ArchiveInputStream(address, size).use { block(generateSequence { it.getNextEntry() }) }

    fun getInputStream(entryName: String): InputStream? {
        val archive = ArchiveInputStream(address, size)
        try {
            while (true) {
                val entry = archive.getNextEntry() ?: break
                if (entry.name == entryName) {
                    return archive
                }
            }
        } catch (e: ArchiveException) {
            archive.close()
            throw e
        }
        archive.close()
        return null
    }

    // SY -->
    private fun isEncrypted(): Boolean {
        val archive = Archive.readNew()
        try {
            Archive.setCharset(archive, Charsets.UTF_8.name().toByteArray())
            Archive.readSupportFilterAll(archive)
            Archive.readSupportFormatAll(archive)
            Archive.readOpenMemoryUnsafe(archive, address, size)
            return Archive.readHasEncryptedEntries(archive) != 0
                .also { Archive.readFree(archive) }
        } catch (e: ArchiveException) {
            Archive.readFree(archive)
            throw e
        }
    }

    private fun isPasswordIncorrect(): Boolean? {
        if (!encrypted) return null
        val archive = ArchiveInputStream(address, size)
        try {
            while (true) {
                val entry = archive.getNextEntry() ?: break
                if (entry.isEncrypted) {
                    archive.read()
                    break
                }
            }
        } catch (e: ArchiveException) {
            if (e.message == "Incorrect passphrase") return true
            archive.close()
            throw e
        }
        archive.close()
        return false
    }
    // SY <--

    override fun close() {
        Os.munmap(address, size)
    }
}

fun UniFile.archiveReader(context: Context) = openFileDescriptor(context, "r").use { ArchiveReader(it) }
