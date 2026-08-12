package com.jrs.skannlet.ui.profile.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jrs.skannlet.R

@Composable
internal fun ProfileHeader(
    canDeleteUser: Boolean,
    onAddUserClick: () -> Unit,
    onDeleteUserClick: () -> Unit,
    onSetProjectNumberClick: () -> Unit,
    onSupportClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }

    fun selectMenuItem(action: () -> Unit) {
        menuExpanded = false
        action()
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Profil",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Image(
            painter = painterResource(R.drawable.omflogo),
            contentDescription = "OM Fjeld",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .width(80.dp)
                .height(32.dp),
        )
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.more_vert_24px),
                    contentDescription = "Flere valg",
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                ProfileMenuItem("Ny bruker") {
                    selectMenuItem(onAddUserClick)
                }
                ProfileMenuItem(
                    text = "Slett bruker",
                    enabled = canDeleteUser,
                    onClick = {
                        selectMenuItem(onDeleteUserClick)
                    },
                )
                ProfileMenuItem("Sett løpenummer") {
                    selectMenuItem(onSetProjectNumberClick)
                }
                ProfileMenuItem("Support") {
                    selectMenuItem(onSupportClick)
                }
                ProfileMenuItem("Om applikasjonen") {
                    selectMenuItem(onAboutClick)
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        enabled = enabled,
        onClick = onClick,
    )
}
