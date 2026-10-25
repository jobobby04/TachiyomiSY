package tachiyomi.domain.release.service

import tachiyomi.domain.release.model.Release

interface ReleaseService {

    // SY -->
    suspend fun latest(repository: String): Release
    // SY <--
}
