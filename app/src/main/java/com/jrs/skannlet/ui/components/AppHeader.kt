package com.jrs.skannlet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.R

@Composable
fun AppHeader(
    title: String,
    activeUserName: String?,
    onChangeUserClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = onChangeUserClick,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier
                .height(32.dp)
                .widthIn(max = 128.dp),
        ) {
            Text(
                text = activeUserName ?: "Velg bruker",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Image(
            painter = painterResource(R.drawable.omflogo),
            contentDescription = "OM Fjeld",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(start = 8.dp)
                .width(80.dp)
                .height(32.dp),
        )
    }
}
