package com.jrs.skannlet.ui.profile.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.R
import com.jrs.skannlet.ui.profile.components.ProfileSubpageHeader

private const val APP_VERSION = "1.1.0"
private const val APP_LICENSE = "Apache 2.0"
private const val SOURCE_CODE_TEXT = "kildekode"
internal const val SOURCE_CODE_URL = "https://github.com/jesperrsolost/Skannlet"

@Composable
internal fun AboutApplicationScreen(
    onBackClick: () -> Unit,
    onSourceCodeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appName = stringResource(R.string.app_name)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileSubpageHeader(
            title = "Om applikasjonen",
            onBackClick = onBackClick,
        )

        Image(
            painter = painterResource(R.drawable.omflogo),
            contentDescription = "OM Fjeld",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(160.dp)
                .height(64.dp),
        )

        Text(
            text = appName,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Versjon: $APP_VERSION",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Lisens: $APP_LICENSE",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = SOURCE_CODE_TEXT,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = TextDecoration.Underline,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(
                    onClickLabel = "Åpne kildekode",
                    role = Role.Button,
                    onClick = onSourceCodeClick,
                ),
            )
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Lokal skanning for prosjekter",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Appen brukes til å registrere varer med strekkode, organisere skanninger per prosjekt og eksportere prosjektdata til CSV og PDF.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Data",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Brukere, produktliste og prosjekter lagres lokalt på enheten.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Text(
            text = "Utviklet for Ø. M. Fjeld Prosjektservice av Jesper Ruud Soløst",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
