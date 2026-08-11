package com.qrgenie.app.data

/**
 * Simple data model for a scan history entry. We avoid Room in this minimal implementation
 * to keep the build toolchain simple and avoid annotation-processing compatibility issues.
 */
data class ScanHistoryItem(
    val id: Long = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** "scanned" or "generated" */
    val source: String = "scanned",
    val isFavorite: Boolean = false
)

