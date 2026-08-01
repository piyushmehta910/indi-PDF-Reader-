package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "annotations")
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pdfId: Int,
    val pageIndex: Int,
    val type: String, // "highlight", "underline", "strike", "text", "drawing"
    val color: Int,
    val rectsJson: String, // Coordinates of highlight/drawings as JSON representation
    val text: String? = null, // Sticky note content or typed text
    val timestamp: Long = System.currentTimeMillis()
)
