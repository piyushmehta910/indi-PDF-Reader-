package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    // --- PDF Documents ---
    @Query("SELECT * FROM pdf_documents ORDER BY isPinned DESC, lastViewedTimestamp DESC")
    fun getAllPdfs(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE isFavorite = 1 ORDER BY lastViewedTimestamp DESC")
    fun getFavoritePdfs(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id")
    suspend fun getPdfById(id: Int): PdfDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdf(pdf: PdfDocumentEntity): Long

    @Update
    suspend fun updatePdf(pdf: PdfDocumentEntity)

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    suspend fun deletePdfById(id: Int)

    @Query("UPDATE pdf_documents SET progressPage = :progressPage, lastViewedTimestamp = :timestamp WHERE id = :id")
    suspend fun updateReadingProgress(id: Int, progressPage: Int, timestamp: Long)

    @Query("UPDATE pdf_documents SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Int, isFavorite: Boolean)

    @Query("UPDATE pdf_documents SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinnedStatus(id: Int, isPinned: Boolean)

    @Query("UPDATE pdf_documents SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: Int, tags: String)

    @Query("UPDATE pdf_documents SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Int, notes: String)

    // --- Annotations ---
    @Query("SELECT * FROM annotations WHERE pdfId = :pdfId ORDER BY pageIndex ASC, timestamp ASC")
    fun getAnnotationsForPdf(pdfId: Int): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteAnnotationById(id: Int)

    @Query("DELETE FROM annotations WHERE pdfId = :pdfId")
    suspend fun clearAnnotationsForPdf(pdfId: Int)

    // --- Signatures ---
    @Query("SELECT * FROM signatures ORDER BY isDefault DESC, timestamp DESC")
    fun getAllSignatures(): Flow<List<SignatureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignature(signature: SignatureEntity): Long

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun deleteSignatureById(id: Int)

    @Query("UPDATE signatures SET isDefault = 0")
    suspend fun clearDefaultSignatures()

    @Transaction
    suspend fun setDefaultSignature(id: Int) {
        clearDefaultSignatures()
        updateSignatureDefault(id, true)
    }

    @Query("UPDATE signatures SET isDefault = :isDefault WHERE id = :id")
    suspend fun updateSignatureDefault(id: Int, isDefault: Boolean)
}
