package tachiyomi.domain.track.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.track.model.Track

/**
 * SY-specific file
 */
@Inject
class IsTrackUnfollowed {

    fun await(track: Track) =
        // TrackManager.MDLIST
        track.trackerId == 60L &&
            // FollowStatus.UNFOLLOWED
            track.status == 0L
}
