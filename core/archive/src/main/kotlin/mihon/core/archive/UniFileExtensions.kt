package mihon.core.archive

import android.content.Context
import android.os.ParcelFileDescriptor
import com.hippo.unifile.UniFile

internal fun UniFile.openFileDescriptor(context: Context, mode: String): ParcelFileDescriptor =
    context.contentResolver.openFileDescriptor(uri, mode) ?: error("Failed to open file descriptor: ${filePath ?: uri}")

fun UniFile.archiveReader(
    context: Context,
    // SY -->
    getDecryptedPassword: (() -> ByteArray)?,
    // SY <--
) = openFileDescriptor(context, "r").use { ArchiveReader(it, /* SY --> */ getDecryptedPassword /* SY <-- */) }

fun UniFile.epubReader(
    context: Context,
    // SY -->
    getDecryptedPassword: (() -> ByteArray)?,
    // SY <--
) = EpubReader(archiveReader(context, /* SY --> */ getDecryptedPassword /* SY <-- */))
