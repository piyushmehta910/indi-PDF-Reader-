package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val pointsJson: String, // SVG-like simplified point coordinate JSON string
    val isDefault: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
