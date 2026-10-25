package exh.ui.metadata.adapters

import android.content.Context
import androidx.annotation.ColorInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gowtham.ratingbar.ComposeStars
import com.gowtham.ratingbar.RatingBarStyle
import eu.kanade.tachiyomi.util.system.copyToClipboard
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Info
import mihon.icons.materialsymbols.rounded.Person
import mihon.icons.sy.materialsymbols.SYMaterialSymbols
import mihon.icons.sy.materialsymbols.rounded.PersonOff
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * SY-specific file
 *
 * Lines of a metadata description, stacked with the panel's line spacing.
 */
@Composable
fun MetadataDescription(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/**
 * A single line of a metadata description, with the "More info" button at its end when [openMetadataViewer] is set.
 */
@Composable
fun MetadataRow(
    modifier: Modifier = Modifier,
    openMetadataViewer: (() -> Unit)? = null,
    spacing: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = if (openMetadataViewer != null) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        if (openMetadataViewer != null) {
            MetadataMoreInfoButton(onClick = openMetadataViewer)
        }
    }
}

class MetadataFactsScope internal constructor() {
    internal val facts = mutableListOf<@Composable () -> Unit>()

    fun fact(content: @Composable () -> Unit) {
        facts += content
    }
}

/**
 * A line of facts separated by "·", with the "More info" button at its end when [openMetadataViewer] is set.
 */
@Composable
fun MetadataFacts(
    modifier: Modifier = Modifier,
    openMetadataViewer: (() -> Unit)? = null,
    content: MetadataFactsScope.() -> Unit,
) {
    val facts = MetadataFactsScope().apply(content).facts
    MetadataRow(
        modifier = modifier,
        openMetadataViewer = openMetadataViewer,
        spacing = 6.dp,
    ) {
        facts.forEachIndexed { index, fact ->
            if (index > 0) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            fact()
        }
    }
}

@Composable
private fun MetadataMoreInfoButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = stringResource(SYMR.strings.more_info))
    }
}

/**
 * Secondary text of a metadata description, copied to the clipboard on long press when [copyable].
 */
@Composable
fun MetadataText(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    copyable: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier.metadataClickable(context, text, copyable, onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = iconTint,
            )
        }
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Uploader shown as a link that runs [onClick], or as a muted label when the uploader is disowned. [emphasized] shows
 * it like a link without one.
 */
@Composable
fun MetadataUploader(
    uploader: String,
    disowned: Boolean = false,
    onClick: (() -> Unit)? = null,
    emphasized: Boolean = onClick != null,
    copyable: Boolean = true,
) {
    if (disowned) {
        MetadataText(
            text = stringResource(SYMR.strings.disowned),
            icon = SYMaterialSymbols.Rounded.PersonOff,
            iconTint = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.outline,
            copyable = false,
        )
    } else {
        MetadataText(
            text = uploader,
            icon = MaterialSymbols.Rounded.Person,
            style = if (emphasized) {
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            copyable = copyable,
            onClick = onClick,
        )
    }
}

/**
 * Compact label, outlined unless [containerColor] is set, copied to the clipboard on long press when [copyable].
 */
@Composable
fun MetadataChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    small: Boolean = false,
    copyable: Boolean = true,
) {
    val context = LocalContext.current
    val horizontalPadding = if (small) 8.dp else 10.dp
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (small) 6.dp else 8.dp),
        color = containerColor ?: Color.Transparent,
        contentColor = contentColor,
        border = if (containerColor == null) {
            BorderStroke(
                width = 1.dp,
                color = if (small) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outline,
            )
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .metadataClickable(context, text, copyable = copyable, onClick = null)
                .height(if (small) 24.dp else 28.dp)
                .padding(start = if (icon != null) 6.dp else horizontalPadding, end = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (containerColor == null) MaterialTheme.colorScheme.onSurfaceVariant else contentColor,
                )
            }
            Text(
                text = text,
                style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Genre chip on the source's genre color, or on the highest surface container when the genre is unknown. Text on a
 * genre color is light on darker colors and dark on lighter ones.
 */
@Composable
fun MetadataGenre(
    text: String,
    @ColorInt color: Int?,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    copyable: Boolean = true,
) {
    MetadataChip(
        text = text,
        modifier = modifier,
        containerColor = color?.let(::Color) ?: MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = color?.let(::genreContentColor) ?: MaterialTheme.colorScheme.onSurface,
        small = small,
        copyable = copyable,
    )
}

private fun genreContentColor(@ColorInt color: Int): Color {
    return if (Color(color).luminance() < GENRE_LIGHT_CONTENT_MAX_LUMINANCE) GenreLightContent else GenreDarkContent
}

private const val GENRE_LIGHT_CONTENT_MAX_LUMINANCE = 0.32f

private val GenreLightContent = Color.White
private val GenreDarkContent = Color(0xFF1B1B21)

/**
 * Five star [rating] in half star steps, followed by the [score] and its [label].
 */
@Composable
fun MetadataRating(
    rating: Float,
    score: Float?,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.padding(end = 4.dp)) {
            MetadataStars(rating = (rating * 2).roundToInt() / 2f, spaceBetween = 2.dp)
        }
        if (score != null) {
            Text(
                text = (round(score * 100.0) / 100.0).toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * Five stars in the theme colors.
 */
@Composable
fun MetadataStars(
    rating: Float,
    size: Dp = 20.dp,
    spaceBetween: Dp = 0.dp,
) {
    ComposeStars(
        value = rating,
        numOfStars = 5,
        size = size,
        spaceBetween = spaceBetween,
        hideInactiveStars = false,
        style = RatingBarStyle.Fill(
            activeColor = MaterialTheme.colorScheme.primary,
            inActiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        painterEmpty = null,
        painterFilled = null,
    )
}

private fun Modifier.metadataClickable(
    context: Context,
    text: String,
    copyable: Boolean,
    onClick: (() -> Unit)?,
): Modifier {
    if (!copyable && onClick == null) return this
    return combinedClickable(
        onClick = { onClick?.invoke() },
        onLongClick = if (copyable) {
            { context.copyToClipboard(text, text) }
        } else {
            null
        },
    )
}
