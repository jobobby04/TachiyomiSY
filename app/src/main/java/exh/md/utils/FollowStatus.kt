package exh.md.utils

import java.util.Locale

enum class FollowStatus(val int: Long) {
    UNFOLLOWED(0),
    READING(1),
    COMPLETED(2),
    ON_HOLD(3),
    PLAN_TO_READ(4),
    DROPPED(5),
    RE_READING(6),
    ;

    fun toDex(): String = this.name.lowercase(Locale.US)

    companion object {
        fun fromDex(
            value: String?,
        ): FollowStatus = entries.firstOrNull { it.name.lowercase(Locale.US) == value } ?: UNFOLLOWED
        fun fromInt(value: Long): FollowStatus = entries.firstOrNull { it.int == value } ?: UNFOLLOWED
    }
}
