import com.android.build.gradle.tasks.BundleAar

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("dagger.hilt.android.plugin")
    id("kotlin-kapt")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nativedevps.s3"
    compileSdk = Configs.compileSdkVersion
    defaultConfig {
        minSdk = Configs.minSdkVersion
        targetSdk = Configs.targetSdkVersion
        buildConfigField("int", "DB_VERSION", "1")
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }
    flavorDimensions("development")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        debug {
            isShrinkResources = false
            isMinifyEnabled = false
        }
        release {
            isShrinkResources = false
            isMinifyEnabled = false
        }
    }
    productFlavors {
        create("development") {

        }
        create("production") {

        }
    }
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}

kapt {
    correctErrorTypes = true
    generateStubs = true
}

dependencies {
    implementation(project(ProjectRootLibraries.support))
    requiredLibraries()
    supportLibraries()
    networkLibraries()
    implementation("software.amazon.awssdk:s3:2.20.0")
    implementation("software.amazon.awssdk:auth:2.20.0")
    implementation("software.amazon.awssdk:regions:2.20.0")
}

tasks.withType<BundleAar> {
    val targetDirectory = "D:\\Android\\nativedevps-admin\\modules\\S3Agent" //admin
    // Set the archive base name directly as a string
    //archiveFileName.set("nativedevps.aar")
    destinationDirectory.set(file(targetDirectory))

    doFirst {
        val destinationDir = file(targetDirectory)
        if (destinationDir.exists()) {
            destinationDir.listFiles { file -> file.extension == "aar" }?.forEach { aarFile ->
                if (aarFile.delete()) {
                    println("Deleted: ${aarFile.name}")
                } else {
                    println("Failed to delete: ${aarFile.name}")
                }
            }
        }
    }
}
