package com.jrs.skannlet.ui.profile.printer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.R
import com.jrs.skannlet.printer.LabelContentLayout
import com.jrs.skannlet.printer.LabelFormat
import com.jrs.skannlet.printer.LabelMediaTracking
import com.jrs.skannlet.printer.isBuiltIn

@Composable
internal fun LabelFormatCard(
    format: LabelFormat,
    selected: Boolean,
    onSelect: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = format.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = format.dimensionsDescription(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = format.contentLayoutDescription(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (format.isBuiltIn) {
                TextButton(onClick = onCopy) {
                    Text("Kopier")
                }
            } else {
                IconButton(onClick = onEdit) {
                    Icon(
                        painter = painterResource(R.drawable.edit_24px),
                        contentDescription = "Rediger ${format.name}",
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.delete_24px),
                        contentDescription = "Slett ${format.name}",
                    )
                }
            }
        }
    }
}

private fun LabelFormat.dimensionsDescription(): String {
    val tracking = when (mediaTracking) {
        LabelMediaTracking.Gap -> "mellomrom ${trackingHeightMm.toEditableNumber()} mm"
        LabelMediaTracking.BlackMark -> "svartmerke ${trackingHeightMm.toEditableNumber()} mm"
        LabelMediaTracking.Continuous -> "kontinuerlig"
    }
    return "${widthMm.toEditableNumber()} × ${heightMm.toEditableNumber()} mm · $tracking"
}

private fun LabelFormat.contentLayoutDescription(): String = when (contentLayout) {
    LabelContentLayout.CenteredBarcodeValue ->
        "Code 128 med verdien sentrert under strekkoden"

    LabelContentLayout.CompactBarcodeWithText ->
        "Code 128 med separat formatert verdi under strekkoden"
}
