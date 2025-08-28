# S3 Agent Kotlin

A Kotlin library for AWS S3 operations using builder pattern, coroutines, and flows.

## Features

- ✅ File upload with progress tracking
- ✅ Async operations with Flow
- ✅ Delete operations (single and batch)
- ✅ Rename/Move operations
- ✅ ACL management (public/private)
- ✅ Folder operations (create, delete, list)
- ✅ Builder pattern for easy configuration
- ✅ Coroutine support for async operations
- ✅ Comprehensive error handling

## Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("software.amazon.awssdk:s3:2.20.0")
}