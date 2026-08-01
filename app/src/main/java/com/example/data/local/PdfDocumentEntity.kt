package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val filePath: String,
    val fileSize: Long,
    val pageCount: Int,
    val lastViewedTimestamp: Long,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val tags: String = "", // Comma-separated tags
    val notes: String = "",
    val progressPage: Int = 0, // Last read page index
    val isSample: Boolean = false
)
