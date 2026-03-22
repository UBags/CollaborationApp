/*
 * =====================================================================================
 * FILE: CantoProvider.kt
 * DESCRIPTION: Implementation of the CloudStorageProvider for Canto.
 * NOTE: This implementation uses direct REST API calls. You will need an HTTP client
 * library like OkHttp: implementation("com.squareup.okhttp3:okhttp:4.9.3")
 * and a JSON parsing library like Gson: implementation("com.google.code.gson:gson:2.8.9")
 * Canto's API uses 'albums' which will be treated as folders.
 * =====================================================================================
 */
package com.costheta.cortexa.util.storage.cloud

import android.content.Context
import android.util.Log
import com.costheta.cortexa.util.storage.CloudStorageProvider
import com.costheta.cortexa.util.storage.CortexaFolder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Concrete implementation of CloudStorageProvider for interacting with Canto.
 * This class requires a Canto API Access Token and the user's Canto domain.
 */
class CantoProvider(
    private val context: Context,
    private val accessToken: String,
    private val cantoDomain: String // e.g., "yourcompany.canto.com"
) : CloudStorageProvider {

    private val TAG = "CantoProvider"
    private val CORTEXA_ROOT_FOLDER_NAME = "cortexa"
    private val LOCAL_PARENT_DIR = "cortexa"

    private val httpClient = OkHttpClient()
    private val gson = Gson()

    // --- App-Specific 'cortexa' Implementations ---

    override suspend fun initializeCortexaStructure(): Boolean {
        Log.d(TAG, "Initializing 'cortexa' album structure in Canto...")
        val cortexaRootId = getOrCreateAlbum(CORTEXA_ROOT_FOLDER_NAME)
        if (cortexaRootId == null) {
            Log.e(TAG, "Failed to create or find the main 'cortexa' root album.")
            return false
        }

        for (folder in CortexaFolder.entries) {
            if (getOrCreateAlbum(folder.path, cortexaRootId) == null) {
                Log.e(TAG, "Failed to create or find sub-album: ${folder.path}")
                return false
            }
        }
        Log.d(TAG, "'cortexa' structure initialization successful in Canto.")
        return true
    }

    override suspend fun saveFileToCortexa(
        folderType: CortexaFolder,
        specificSubFolderName: String,
        localFilePath: String
    ): String? {
        val fullRemotePath = "/$CORTEXA_ROOT_FOLDER_NAME/${folderType.path}/$specificSubFolderName"
        return saveFileByPath(fullRemotePath, localFilePath)
    }

    override suspend fun downloadFileFromCortexa(
        folderType: CortexaFolder,
        specificSubFolderName: String,
        fileName: String
    ): String? {
        val remoteFilePath = "/$CORTEXA_ROOT_FOLDER_NAME/${folderType.path}/$specificSubFolderName/$fileName"
        return downloadFile(remoteFilePath)
    }

    // --- Generic Implementations ---

    private suspend fun getOrCreateAlbum(albumName: String, parentId: String? = null): String? {
        val albumId = getAlbumId(albumName, parentId)
        return albumId ?: createFolder(parentId ?: "", albumName)
    }

    private suspend fun getAlbumId(albumName: String, parentId: String? = null): String? {
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host(cantoDomain)
            .addPathSegment("api")
            .addPathSegment("v1")
            .addPathSegment("album")

        val url = urlBuilder.build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error listing albums: ${response.body?.string()}")
                return null
            }

            val responseBody = response.body?.string()
            val albumType = object : TypeToken<List<CantoAlbum>>() {}.type
            val albums: List<CantoAlbum> = gson.fromJson(responseBody, albumType)

            // Canto doesn't have a strict parent-child API for albums, so we simulate it by naming convention if needed.
            // For this implementation, we assume a flat structure is acceptable or managed by naming.
            return albums.find { it.name == albumName }?.id
        } catch (e: IOException) {
            Log.e(TAG, "Network error getting album ID for '$albumName'", e)
            return null
        }
    }

    override suspend fun createFolder(parentFolderId: String, folderName: String): String? {
        // In Canto, creating a folder is creating an album.
        // The parentFolderId is not directly used in the Canto API for album creation.
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(cantoDomain)
            .addPathSegment("api")
            .addPathSegment("v1")
            .addPathSegment("album")
            .build()

        val formBody = FormBody.Builder()
            .add("name", folderName)
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(formBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error creating album: ${response.body?.string()}")
                return null
            }
            val responseBody = response.body?.string()
            val createdAlbum: CantoAlbum = gson.fromJson(responseBody, CantoAlbum::class.java)
            Log.d(TAG, "Album '$folderName' created with ID: ${createdAlbum.id}")
            return createdAlbum.id
        } catch (e: IOException) {
            Log.e(TAG, "Network error creating album '$folderName'", e)
            return null
        }
    }

    override suspend fun saveFile(parentFolderId: String, localFilePath: String): String? {
        val localFile = File(localFilePath)
        if (!localFile.exists()) {
            Log.e(TAG, "Local file does not exist: $localFilePath")
            return null
        }

        val url = HttpUrl.Builder()
            .scheme("https")
            .host(cantoDomain)
            .addPathSegment("api")
            .addPathSegment("v1")
            .addPathSegment("upload")
            .addQueryParameter("album", parentFolderId)
            .build()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                localFile.name,
                localFile.asRequestBody("application/octet-stream".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(requestBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error uploading file: ${response.body?.string()}")
                return null
            }
            val responseBody = response.body?.string()
            val uploadedFile: CantoFile = gson.fromJson(responseBody, CantoFile::class.java)
            Log.d(TAG, "File '${uploadedFile.name}' uploaded with ID: ${uploadedFile.id}")
            return uploadedFile.id
        } catch (e: IOException) {
            Log.e(TAG, "Network error uploading file", e)
            return null
        }
    }

    override suspend fun saveFileByPath(fullRemotePath: String, localFilePath: String): String? {
        val pathSegments = fullRemotePath.removePrefix("/").split('/').filter { it.isNotEmpty() }
        var currentAlbumId: String? = null

        // Canto uses a flat album structure, so we create nested albums by name and get the ID of the final one.
        for (segment in pathSegments.dropLast(1)) {
            currentAlbumId = getOrCreateAlbum(segment, currentAlbumId)
            if (currentAlbumId == null) {
                Log.e(TAG, "Could not find or create album segment '$segment' for '$fullRemotePath'")
                return null
            }
        }

        if (currentAlbumId == null) {
            Log.e(TAG, "Failed to resolve a parent album for the upload.")
            return null
        }

        return saveFile(currentAlbumId, localFilePath)
    }

    override suspend fun downloadFile(remoteFilePath: String): String? {
        val pathSegments = remoteFilePath.removePrefix("/").split('/').filter { it.isNotEmpty() }
        val fileName = pathSegments.last()
        val albumPath = pathSegments.dropLast(1)
        var targetAlbumId: String? = null

        // Find the target album
        for (segment in albumPath) {
            targetAlbumId = getAlbumId(segment, targetAlbumId)
            if (targetAlbumId == null) {
                Log.e(TAG, "Could not find album '$segment' in path '$remoteFilePath'")
                return null
            }
        }

        if (targetAlbumId == null) {
            Log.e(TAG, "Could not resolve album for file: $remoteFilePath")
            return null
        }

        // Find the file within the album
        val fileToDownload = getFileInAlbum(targetAlbumId, fileName)
        if (fileToDownload?.url == null) {
            Log.e(TAG, "Could not find file '$fileName' in album ID '$targetAlbumId'")
            return null
        }

        // Download the file from its direct URL
        val localDir = File(context.filesDir, "$LOCAL_PARENT_DIR/${albumPath.joinToString("/")}")
        if (!localDir.exists()) {
            localDir.mkdirs()
        }
        val localFile = File(localDir, fileName)

        val request = Request.Builder().url(fileToDownload.url).build()
        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download file content: ${response.message}")
                return null
            }
            response.body?.byteStream()?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "File downloaded to: ${localFile.absolutePath}")
            return localFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Network error downloading file content", e)
            return null
        }
    }

    private suspend fun getFileInAlbum(albumId: String, fileName: String): CantoFile? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(cantoDomain)
            .addPathSegment("api")
            .addPathSegment("v1")
            .addPathSegment("album")
            .addPathSegment(albumId)
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val responseBody = response.body?.string()
            val albumDetails: CantoAlbumDetails = gson.fromJson(responseBody, CantoAlbumDetails::class.java)
            return albumDetails.documents.find { it.name == fileName }
        } catch (e: IOException) {
            return null
        }
    }

    // Helper data classes for parsing Canto JSON responses
    private data class CantoAlbum(val id: String, val name: String)
    private data class CantoFile(val id: String, val name: String, val url: String?)
    private data class CantoAlbumDetails(val documents: List<CantoFile>)
}
