package com.jrs.skannlet.data.model

import kotlinx.serialization.Serializable

const val MAX_SCAN_ROW_COMMENT_LENGTH = 200

@Serializable
data class AppUser(
    val id: String,
    val name: String,
    val createdAt: Long,
)

@Serializable
data class ScanCollection(
    val id: String,
    val projectNumber: Int = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isLocked: Boolean = false,
    val creatorName: String? = null,
    val isReturn: Boolean = false,
)

@Serializable
data class ScanRow(
    val id: String,
    val collectionId: String,
    val barcode: String,
    val productName: String,
    val quantity: Float = 1f,
    val quantityLocked: Boolean,
    val createdAt: Long,
    val comment: String = "",
)

@Serializable
data class Product(
    val barcode: String,
    val productName: String,
)

@Serializable
data class StoredAppState(
    val activeUserId: String? = null,
    val activeCollectionId: String? = null,
    val nextCollectionProjectNumber: Int = 1,
)
