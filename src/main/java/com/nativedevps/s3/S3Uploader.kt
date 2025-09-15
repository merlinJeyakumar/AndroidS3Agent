package com.nativedevps.s3agent.s3

import android.os.Build
import androidx.annotation.RequiresApi
import com.nativedevps.s3.ProgressAsyncRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.GetObjectAclRequest
import software.amazon.awssdk.services.s3.model.Grant
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ObjectCannedACL
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import software.amazon.awssdk.services.s3.model.PutObjectAclRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Object
import java.io.File
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

class S3Uploader private constructor(
    private val bucketName: String,
    val region: Region,
    override val coroutineContext: CoroutineContext
) : CoroutineScope {

    data class UploadResult(
        val success: Boolean,
        val eTag: String? = null,
        val versionId: String? = null,
        val error: Throwable? = null
    )

    data class OperationResult(
        val success: Boolean,
        val message: String? = null,
        val error: Throwable? = null
    )

    data class FolderResult(
        val success: Boolean,
        val createdObjects: List<String> = emptyList(),
        val deletedObjects: List<String> = emptyList(),
        val error: Throwable? = null
    )

    class Builder {
        private var bucketName: String = ""
        private var region: Region = Region.US_EAST_1
        private var coroutineContext: CoroutineContext = Dispatchers.IO

        fun bucket(bucketName: String) = apply { this.bucketName = bucketName }
        fun region(region: Region) = apply { this.region = region }
        fun region(region: String) = apply { this.region = Region.of(region) }
        fun coroutineContext(context: CoroutineContext) = apply { this.coroutineContext = context }

        fun build(): S3Uploader {
            require(bucketName.isNotBlank()) { "Bucket name must be specified" }

            return S3Uploader(
                bucketName = bucketName,
                region = region,
                coroutineContext = coroutineContext
            )
        }
    }

    companion object {
        fun builder() = Builder()
    }

    // Upload functions
    suspend fun uploadFile(
        file: File,
        key: String,
        contentType: String? = null,
        metadata: Map<String, String> = emptyMap()
    ): UploadResult = withContext(coroutineContext) {
        try {
            val s3Client = S3Client.builder().region(region).build()
            val request = buildPutObjectRequest(key, contentType, metadata)
            val response = s3Client.putObject(request, RequestBody.fromFile(file))

            UploadResult(
                success = true,
                eTag = response.eTag(),
                versionId = response.versionId()
            )
        } catch (e: Exception) {
            UploadResult(success = false, error = e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun uploadFileAsync(
        file: File,
        key: String,
        contentType: String? = null,
        metadata: Map<String, String> = emptyMap()
    ): Flow<UploadResult> = flow {
        val s3Client = S3AsyncClient.builder().region(region).build()
        val request = buildPutObjectRequest(key, contentType, metadata)

        val future: CompletableFuture<PutObjectResponse> =
            s3Client.putObject(request, AsyncRequestBody.fromFile(file))

        try {
            val response = future.await()
            emit(
                UploadResult(
                    success = true,
                    eTag = response.eTag(),
                    versionId = response.versionId()
                )
            )
        } catch (e: Exception) {
            emit(UploadResult(success = false, error = e))
        } finally {
            s3Client.close()
        }
    }.flowOn(coroutineContext)

    fun uploadWithProgress(
        inputStream: InputStream,
        contentLength: Long,
        key: String,
        contentType: String? = null,
        metadata: Map<String, String> = emptyMap()
    ): Flow<UploadProgress> = flow {
        val s3Client = S3AsyncClient.builder().region(region).build()
        val request = buildPutObjectRequest(key, contentType, metadata)

        // Use bytes approach instead of problematic InputStream with progress
        val bytes = inputStream.readBytes()

        // Create a mutable reference to track progress
        var currentProgress: UploadProgress? = null

        val progressRequestBody = ProgressAsyncRequestBody(
            AsyncRequestBody.fromInputStream(
                inputStream,
                contentLength,
                Executors.newCachedThreadPool()
            ),
            onProgress = { bytesWritten, totalBytes ->
                currentProgress = UploadProgress.InProgress(bytesWritten, totalBytes)
            }
        )

        try {
            val response = s3Client.putObject(request, progressRequestBody).await()
            emit(
                UploadProgress.Completed(
                    eTag = response.eTag(),
                    versionId = response.versionId()
                )
            )
        } catch (e: Exception) {
            emit(UploadProgress.Failed(e))
        } finally {
            s3Client.close()
            inputStream.close()
        }
    }.flowOn(coroutineContext)

    // DELETE operations
    suspend fun deleteObject(key: String): OperationResult = withContext(coroutineContext) {
        try {
            val s3Client = S3Client.builder().region(region).build()
            val request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            s3Client.deleteObject(request)
            OperationResult(success = true, message = "Object '$key' deleted successfully")
        } catch (e: Exception) {
            OperationResult(success = false, error = e)
        }
    }

    suspend fun deleteObjects(keys: List<String>): FolderResult = withContext(coroutineContext) {
        try {
            val s3Client = S3Client.builder().region(region).build()

            val objectsToDelete = keys.map { key ->
                ObjectIdentifier.builder().key(key).build()
            }

            val request = DeleteObjectsRequest.builder()
                .bucket(bucketName)
                .delete { delete ->
                    delete.objects(objectsToDelete)
                    delete.quiet(true)
                }
                .build()

            val response = s3Client.deleteObjects(request)
            FolderResult(
                success = true,
                deletedObjects = response.deleted().map { it.key() }
            )
        } catch (e: Exception) {
            FolderResult(success = false, error = e)
        }
    }

    // RENAME/MOVE operations
    suspend fun renameObject(oldKey: String, newKey: String): OperationResult =
        withContext(coroutineContext) {
            try {
                val s3Client = S3Client.builder().region(region).build()

                // Copy object to new key
                val copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(oldKey)
                    .destinationBucket(bucketName)
                    .destinationKey(newKey)
                    .build()

                s3Client.copyObject(copyRequest)

                // Delete original object
                val deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(oldKey)
                    .build()

                s3Client.deleteObject(deleteRequest)

                OperationResult(
                    success = true,
                    message = "Object renamed from '$oldKey' to '$newKey'"
                )
            } catch (e: Exception) {
                OperationResult(success = false, error = e)
            }
        }

    suspend fun moveObject(sourceKey: String, destinationKey: String): OperationResult {
        return renameObject(sourceKey, destinationKey)
    }

    // ACL operations
    suspend fun makePublic(key: String): OperationResult = withContext(coroutineContext) {
        try {
            val s3Client = S3Client.builder().region(region).build()

            val request = PutObjectAclRequest.builder()
                .bucket(bucketName)
                .key(key)
                .acl(ObjectCannedACL.PUBLIC_READ)
                .build()

            s3Client.putObjectAcl(request)
            OperationResult(success = true, message = "Object '$key' is now public")
        } catch (e: Exception) {
            OperationResult(success = false, error = e)
        }
    }

    suspend fun makePrivate(key: String): OperationResult = withContext(coroutineContext) {
        try {
            val s3Client = S3Client.builder().region(region).build()

            val request = PutObjectAclRequest.builder()
                .bucket(bucketName)
                .key(key)
                .acl(ObjectCannedACL.PRIVATE)
                .build()

            s3Client.putObjectAcl(request)
            OperationResult(success = true, message = "Object '$key' is now private")
        } catch (e: Exception) {
            OperationResult(success = false, error = e)
        }
    }

    suspend fun getAcl(key: String): Flow<Grant> = flow {
        val s3Client = S3Client.builder().region(region).build()

        val request = GetObjectAclRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()

        val response = s3Client.getObjectAcl(request)
        response.grants().forEach { grant ->
            emit(grant)
        }
    }.flowOn(coroutineContext)

    // FOLDER operations
    suspend fun createFolder(folderPath: String): OperationResult = withContext(coroutineContext) {
        try {
            val normalizedPath = if (folderPath.endsWith("/")) folderPath else "$folderPath/"

            val s3Client = S3Client.builder().region(region).build()
            val request = buildPutObjectRequest(normalizedPath, null, emptyMap())

            s3Client.putObject(request, RequestBody.empty())

            OperationResult(
                success = true,
                message = "Folder '$normalizedPath' created successfully"
            )
        } catch (e: Exception) {
            OperationResult(success = false, error = e)
        }
    }

    suspend fun deleteFolder(folderPath: String): FolderResult = withContext(coroutineContext) {
        try {
            val normalizedPath = if (folderPath.endsWith("/")) folderPath else "$folderPath/"

            val s3Client = S3Client.builder().region(region).build()

            val listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(normalizedPath)
                .build()

            val objects = s3Client.listObjectsV2(listRequest).contents()
            val keysToDelete = objects.map { it.key() }

            if (keysToDelete.isEmpty()) {
                return@withContext FolderResult(success = true)
            }

            val deleteResult = deleteObjects(keysToDelete)

            FolderResult(
                success = deleteResult.success,
                deletedObjects = keysToDelete,
                error = deleteResult.error
            )
        } catch (e: Exception) {
            FolderResult(success = false, error = e)
        }
    }

    suspend fun listFolder(folderPath: String): Flow<S3Object> = flow {
        val s3Client = S3Client.builder().region(region).build()

        val normalizedPath = if (folderPath.endsWith("/")) folderPath else "$folderPath/"

        var continuationToken: String? = null
        do {
            val listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(normalizedPath)
                .continuationToken(continuationToken)
                .build()

            val response = s3Client.listObjectsV2(listRequest)
            response.contents().forEach { objectSummary ->
                emit(objectSummary)
            }

            continuationToken = response.continuationToken()
        } while (response.isTruncated())
    }.flowOn(coroutineContext)

    // Utility functions
    suspend fun objectExists(key: String): Boolean = withContext(coroutineContext) {
        try {
            val s3Client = S3Client.builder().region(region).build()

            val request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            s3Client.headObject(request)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getObjectUrl(key: String): String {
        return "https://${bucketName}.s3.${region.id()}.amazonaws.com/$key"
    }

    fun getPublicObjectUrl(key: String): String {
        return getObjectUrl(key)
    }

    private fun buildPutObjectRequest(
        key: String,
        contentType: String? = null,
        metadata: Map<String, String> = emptyMap()
    ): PutObjectRequest {
        return PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .apply {
                contentType?.let { contentType(it) }
                if (metadata.isNotEmpty()) {
                    metadata(metadata)
                }
            }
            .build()
    }
}

// Progress tracking sealed class
sealed class UploadProgress {
    data class InProgress(val bytesWritten: Long, val totalBytes: Long) : UploadProgress()
    data class Completed(val eTag: String?, val versionId: String?) : UploadProgress()
    data class Failed(val error: Throwable) : UploadProgress()
}