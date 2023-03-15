package tachiyomi.presentation.core.components.material

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.presentation.core.components.Pill

@Composable
fun TabIndicator(currentTabPosition: TabPosition) {
    TabRowDefaults.Indicator(
        Modifier
            .tabIndicatorOffset(currentTabPosition)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
    )
}

@Composable
fun TabText(
    text: String,
    badgeCount: Int? = null,
) {
    val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f

    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            // SY -->
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // SY <--
        )
        if (badgeCount != null) {
            Pill(
                text = "$badgeCount",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = pillAlpha),
                fontSize = 10.sp,
            )
        }
    }
}
