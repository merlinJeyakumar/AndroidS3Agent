package com.nativedevps.s3.utility

import com.nativedevps.s3.S3Uploader
import com.nativedevps.s3.UploadProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client

// Extension properties
val S3Uploader.OperationResult.isSuccessful: Boolean
    get() = success

val S3Uploader.FolderResult.deletedCount: Int
    get() = deletedObjects.size

val UploadProgress.InProgress.percentage: Int
    get() = if (totalBytes > 0) ((bytesWritten * 100) / totalBytes).toInt() else 0

// Extension functions for common operations
suspend fun S3Uploader.uploadText(
    text: String,
    key: String,
    contentType: String = "text/plain"
): S3Uploader.UploadResult {
    return uploadBytes(text.toByteArray(), key, contentType)
}

suspend fun S3Uploader.uploadBytes(
    bytes: ByteArray,
    key: String,
    contentType: String? = null
): S3Uploader.UploadResult {
    val s3Client = S3Client.builder().region(region).build()
    val request = buildPutObjectRequest(key, contentType, emptyMap())

    return try {
        val response = s3Client.putObject(request, RequestBody.fromBytes(bytes))
        S3Uploader.UploadResult(
            success = true,
            eTag = response.eTag(),
            versionId = response.versionId()
        )
    } catch (e: Exception) {
        S3Uploader.UploadResult(success = false, error = e)
    }
}

// Batch operations
suspend fun S3Uploader.makePublicAllInFolder(folderPath: String): S3Uploader.OperationResult {
    val normalizedPath = if (folderPath.endsWith("/")) folderPath else "$folderPath/"
    val objects = mutableListOf<S3Object>()

    listFolder(normalizedPath).collect { objects.add(it) }

    if (objects.isEmpty()) {
        return S3Uploader.OperationResult(success = true, message = "No objects found in folder")
    }

    val results = objects.map { obj ->
        makePublic(obj.key())
    }

    val successful = results.count { it.success }
    return S3Uploader.OperationResult(
        success = successful == objects.size,
        message = "Made $successful/${objects.size} objects public"
    )
}

// Flow transformations
fun Flow<UploadProgress>.onProgressChanged(
    onProgress: (Int) -> Unit
): Flow<UploadProgress> = this.map { progress ->
    when (progress) {
        is UploadProgress.InProgress -> {
            onProgress(progress.percentage)
            progress
        }
        else -> progress
    }
}