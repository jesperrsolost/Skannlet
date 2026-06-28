package com.jrs.skannlet.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DateFormatter = DateTimeFormatter
    .ofPattern("dd.MM.yyyy HH:mm")
    .withZone(ZoneId.systemDefault())

fun formatDateTime(epochMillis: Long): String = DateFormatter.format(Instant.ofEpochMilli(epochMillis))
