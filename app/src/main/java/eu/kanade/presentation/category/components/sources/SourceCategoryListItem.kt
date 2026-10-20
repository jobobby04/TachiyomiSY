package eu.kanade.presentation.category.components.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.Label
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.Edit
import tachiyomi.presentation.core.components.material.padding

@Composable
fun SourceCategoryListItem(
    modifier: Modifier,
    category: String,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRename() }
                .padding(
                    start = MaterialTheme.padding.medium,
                    top = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.medium,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = MaterialSymbols.AutoMirroredRounded.Label, contentDescription = "")
            Text(text = category, modifier = Modifier.padding(start = MaterialTheme.padding.medium))
        }
        Row {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onRename) {
                Icon(imageVector = MaterialSymbols.Rounded.Edit, contentDescription = "")
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = MaterialSymbols.Rounded.Delete, contentDescription = "")
            }
        }
    }
}
