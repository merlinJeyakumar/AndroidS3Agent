# S3Uploader Kotlin Library

A powerful, coroutine-based AWS S3 client library for Kotlin with builder pattern support, flow integration, and comprehensive file operations.

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)
![AWS SDK](https://img.shields.io/badge/AWS%20SDK-2.20.0-orange.svg)
![Coroutines](https://img.shields.io/badge/Coroutines-1.7.3-green.svg)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## 📦 Features

- ✅ **Builder Pattern** - Fluent API for easy configuration
- ✅ **Coroutine Support** - Async operations with structured concurrency
- ✅ **Flow Integration** - Reactive progress tracking and real-time updates
- ✅ **Multiple Auth Methods** - Environment variables, AWS config, or programmatic credentials
- ✅ **Comprehensive Operations** - Upload, delete, rename, ACL management, and more
- ✅ **Progress Tracking** - Real-time upload progress with Flow
- ✅ **Batch Operations** - Process multiple files efficiently
- ✅ **Error Handling** - Comprehensive result types with proper error information
- ✅ **Folder Support** - Create, list, and delete folders recursively

## 🚀 Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("software.amazon.awssdk:s3:2.20.0")
    implementation("software.amazon.awssdk:auth:2.20.0")
    implementation("software.amazon.awssdk:regions:2.20.0")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3'
    implementation 'software.amazon.awssdk:s3:2.20.0'
    implementation 'software.amazon.awssdk:auth:2.20.0'
    implementation 'software.amazon.awssdk:regions:2.20.0'
}
```

## 🔐 Authentication Setup

### Method 1: Environment Variables (Recommended)

```bash
# Linux/macOS
export AWS_ACCESS_KEY_ID=your_access_key_id
export AWS_SECRET_ACCESS_KEY=your_secret_access_key
export AWS_REGION=us-west-2

# Windows (Command Prompt)
set AWS_ACCESS_KEY_ID=your_access_key_id
set AWS_SECRET_ACCESS_KEY=your_secret_access_key
set AWS_REGION=us-west-2

# Windows (PowerShell)
$env:AWS_ACCESS_KEY_ID="your_access_key_id"
$env:AWS_SECRET_ACCESS_KEY="your_secret_access_key"
$env:AWS_REGION="us-west-2"
```

### Method 2: AWS Credentials File

Create `~/.aws/credentials`:
```ini
[default]
aws_access_key_id = your_access_key_id
aws_secret_access_key = your_secret_access_key
```

Create `~/.aws/config`:
```ini
[default]
region = us-west-2
```

### Method 3: Programmatic Credentials (Development Only)

```kotlin
val s3Uploader = S3Uploader.builder()
    .bucket("my-bucket")
    .region(Region.US_WEST_2)
    .credentials(
        accessKeyId = "AKIAIOSFODNN7EXAMPLE",
        secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
    )
    .build()
```

## 📖 Quick Start

### Basic File Upload

```kotlin
import com.nativedevps.s3agent.s3.S3Uploader
import software.amazon.awssdk.regions.Region

suspend fun main() {
    val s3Uploader = S3Uploader.builder()
        .bucket("your-bucket-name")
        .region(Region.US_WEST_2)
        .build()

    val result = s3Uploader.uploadFile(
        file = File("document.pdf"),
        key = "documents/report.pdf",
        contentType = "application/pdf"
    )

    if (result.success) {
        println("✅ Upload successful! ETag: ${result.eTag}")
    } else {
        println("❌ Upload failed: ${result.error?.message}")
    }
}
```

### Upload with Progress Tracking

```kotlin
s3Uploader.uploadWithProgress(
    inputStream = file.inputStream(),
    contentLength = file.length(),
    key = "videos/my-video.mp4",
    contentType = "video/mp4"
).collect { progress ->
    when (progress) {
        is UploadProgress.InProgress -> {
            val percentage = (progress.bytesWritten * 100) / progress.totalBytes
            println("📈 Progress: $percentage%")
        }
        is UploadProgress.Completed -> {
            println("✅ Upload completed! ETag: ${progress.eTag}")
        }
        is UploadProgress.Failed -> {
            println("❌ Upload failed: ${progress.error.message}")
        }
    }
}
```

## 🎯 API Reference

### Core Methods

#### File Operations
```kotlin
// Basic upload
suspend fun uploadFile(file: File, key: String, contentType: String?, metadata: Map<String, String>): UploadResult

// Async upload
fun uploadFileAsync(file: File, key: String, contentType: String?, metadata: Map<String, String>): Flow<UploadResult>

// Upload with progress
fun uploadWithProgress(inputStream: InputStream, contentLength: Long, key: String, contentType: String?, metadata: Map<String, String>): Flow<UploadProgress>
```

#### Folder Operations
```kotlin
// Create folder
suspend fun createFolder(folderPath: String): OperationResult

// Delete folder (recursive)
suspend fun deleteFolder(folderPath: String): FolderResult

// List folder contents
suspend fun listFolder(folderPath: String): Flow<S3Object>
```

#### ACL Management
```kotlin
// Make object public
suspend fun makePublic(key: String): OperationResult

// Make object private  
suspend fun makePrivate(key: String): OperationResult

// Get ACL information
suspend fun getAcl(key: String): Flow<Grant>
```

#### Utility Methods
```kotlin
// Check if object exists
suspend fun objectExists(key: String): Boolean

// Generate URLs
fun getObjectUrl(key: String): String
fun getPublicObjectUrl(key: String): String

// Rename/move objects
suspend fun renameObject(oldKey: String, newKey: String): OperationResult
suspend fun moveObject(sourceKey: String, destinationKey: String): OperationResult
```

### Result Types

```kotlin
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

sealed class UploadProgress {
    data class InProgress(val bytesWritten: Long, val totalBytes: Long) : UploadProgress()
    data class Completed(val eTag: String?, val versionId: String?) : UploadProgress()
    data class Failed(val error: Throwable) : UploadProgress()
}
```

## 🏗️ Architecture

The library follows these design principles:

- **Builder Pattern** for fluent and type-safe configuration
- **Coroutine-first** design for async operations
- **Flow integration** for reactive programming
- **Multiple authentication** methods for flexibility
- **Immutability** with data classes for results
- **Proper error handling** with detailed error information
- **Resource safety** with automatic client cleanup

## 📚 Examples

### User Profile Picture Upload

```kotlin
suspend fun uploadUserProfile(userId: String, imageFile: File) {
    val s3Uploader = S3Uploader.builder()
        .bucket("user-profiles")
        .region(Region.US_WEST_2)
        .build()

    val result = s3Uploader.uploadFile(
        file = imageFile,
        key = "users/$userId/profile.jpg",
        contentType = "image/jpeg",
        metadata = mapOf("user_id" to userId)
    )

    if (result.success) {
        s3Uploader.makePublic("users/$userId/profile.jpg")
        val url = s3Uploader.getPublicObjectUrl("users/$userId/profile.jpg")
        println("Profile picture available at: $url")
    }
}
```

### Batch Document Processing

```kotlin
suspend fun processDocuments(documents: List<File>) {
    val s3Uploader = S3Uploader.builder()
        .bucket("company-documents")
        .region(Region.EU_WEST_1)
        .build()

    val date = java.time.LocalDate.now().toString()
    s3Uploader.createFolder("uploads/$date/")

    documents.forEach { document ->
        s3Uploader.uploadFileAsync(
            file = document,
            key = "uploads/$date/${document.name}",
            contentType = "application/pdf"
        ).collect { result ->
            if (result.success) {
                println("✅ ${document.name} uploaded successfully")
            }
        }
    }
}
```

### Secure File Management

```kotlin
class SecureFileManager {
    private val s3Uploader = S3Uploader.builder()
        .bucket("secure-files")
        .region(Region.US_GOV_EAST_1)
        .build()

    suspend fun uploadPrivateDocument(file: File, documentId: String): String {
        val key = "private/documents/$documentId"
        
        val result = s3Uploader.uploadFile(
            file = file,
            key = key,
            contentType = "application/pdf"
        )

        if (result.success) {
            s3Uploader.makePrivate(key)
            return key
        } else {
            throw Exception("Upload failed: ${result.error?.message}")
        }
    }
}
```

## 🔒 Security Best Practices

1. **✅ Use environment variables** for production deployments
2. **✅ Implement IAM roles** on AWS infrastructure (EC2, ECS, Lambda)
3. **✅ Set appropriate bucket policies** for your use case
4. **✅ Use private ACL** for sensitive files
5. **✅ Rotate credentials** regularly
6. **❌ Never hardcode credentials** in source code
7. **❌ Never commit secrets** to version control
8. **❌ Avoid programmatic credentials** in production

## 🐛 Troubleshooting

### Common Issues

1. **Credentials not found**: Ensure AWS credentials are properly set up
2. **Bucket access denied**: Check bucket policies and IAM permissions
3. **Region mismatch**: Ensure bucket and client are in the same region
4. **Network issues**: Check internet connectivity and AWS service status

### Debug Mode

Enable AWS SDK logging:
```bash
export AWS_LOG_LEVEL=DEBUG
```

### Error Handling

```kotlin
val result = s3Uploader.uploadFile(file, key, contentType)
when {
    result.success -> println("Success: ${result.eTag}")
    result.error is AmazonS3Exception -> println("S3 error: ${result.error.message}")
    result.error is IOException -> println("Network error: ${result.error.message}")
    else -> println("Unknown error: ${result.error?.message}")
}
```

## 📊 Performance Tips

1. **Use async operations** for large files or batch processing
2. **Reuse S3Uploader instances** when possible
3. **Implement retry logic** for transient failures
4. **Use appropriate chunk sizes** for large uploads
5. **Monitor memory usage** when processing large files

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/new-feature`
3. Commit changes: `git commit -am 'Add new feature'`
4. Push to branch: `git push origin feature/new-feature`
5. Submit a pull request

### Development Setup

```bash
# Clone the repository
git clone https://github.com/your-username/s3-uploader-kotlin.git

# Open in IntelliJ IDEA or Android Studio
# Ensure Kotlin and AWS SDK dependencies are configured
```

## 📄 License

```text
MIT License

Copyright (c) 2024 NativeDevPS

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR ANY COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 📞 Support

- 📧 **Email**: support@nativedevps.com
- 🐛 **Issues**: [GitHub Issues](https://github.com/nativedevps/s3-uploader-kotlin/issues)
- 💬 **Discussions**: [GitHub Discussions](https://github.com/nativedevps/s3-uploader-kotlin/discussions)
- 📚 **Documentation**: [AWS S3 Developer Guide](https://docs.aws.amazon.com/s3/)

## 🔗 Related Projects

- [AWS SDK for Java](https://github.com/aws/aws-sdk-java-v2)
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Kotlin Flow](https://kotlinlang.org/docs/flow.html)

## 🚀 Changelog

### v1.0.0
- Initial release with comprehensive S3 operations
- Builder pattern implementation
- Coroutine and Flow support
- Multiple authentication methods
- Progress tracking functionality

---

**⭐ If you find this library useful, please give it a star on GitHub!**

For more information, visit our [GitHub repository](https://github.com/nativedevps/s3-uploader-kotlin) or check the [AWS S3 documentation](https://docs.aws.amazon.com/s3/).