/*
 * =====================================================================================
 * FILE: SyncProvider.kt
 * DESCRIPTION: Implementation of the CloudStorageProvider for Sync.com.
 * NOTE: This implementation uses direct REST API calls. You will need an HTTP client
 * library like OkHttp: implementation("com.squareup.okhttp3:okhttp:4.9.3")
 * and a JSON parsing library like Gson: implementation("com.google.code.gson:gson:2.8.9")
 * =====================================================================================
 */
package com.costheta.cortexa.util.storage.cloud

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

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
 * Concrete implementation of CloudStorageProvider for interacting with Sync.com.
 * This class requires a Sync.com API v2 Token.
 */
class SyncProvider(
    private val context: Context,
    private val apiToken: String
) : CloudStorageProvider {

    private val TAG = "SyncProvider"
    private val CORTEXA_ROOT_FOLDER_NAME = "cortexa"
    private val LOCAL_PARENT_DIR = "cortexa"
    private val SYNC_ROOT_FOLDER_ID = "0"

    private val httpClient = OkHttpClient()
    private val gson = Gson()

    // --- App-Specific 'cortexa' Implementations ---

    override suspend fun initializeCortexaStructure(): Boolean {
        Log.d(TAG, "Initializing 'cortexa' folder structure in Sync.com...")
        val cortexaRootId = getOrCreateFolder(SYNC_ROOT_FOLDER_ID, CORTEXA_ROOT_FOLDER_NAME)
        if (cortexaRootId == null) {
            Log.e(TAG, "Failed to create or find the main 'cortexa' root folder.")
            return false
        }

        for (folder in CortexaFolder.entries) {
            if (getOrCreateFolder(cortexaRootId, folder.path) == null) {
                Log.e(TAG, "Failed to create or find sub-folder: ${folder.path}")
                return false
            }
        }
        Log.d(TAG, "'cortexa' structure initialization successful in Sync.com.")
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

    private suspend fun getOrCreateFolder(parentId: String, folderName: String): String? {
        // Check if folder exists
        val folderId = getFolderId(parentId, folderName)
        return folderId ?: createFolder(parentId, folderName)
    }

    private suspend fun getFolderId(parentId: String, folderName: String): String? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.sync.com")
            .addPathSegment("v2")
            .addPathSegment("files")
            .addPathSegment("list-folder")
            .addQueryParameter("parent_id", parentId)
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiToken")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error listing folder contents: ${response.body?.string()}")
                return null
            }

            val responseBody = response.body?.string()
            val itemType = object : TypeToken<List<SyncItem>>() {}.type
            val items: List<SyncItem> = gson.fromJson(responseBody, itemType)

            return items.find { it.type == "d" && it.name == folderName }?.id
        } catch (e: IOException) {
            Log.e(TAG, "Network error getting folder ID for '$folderName'", e)
            return null
        }
    }

    override suspend fun createFolder(parentFolderId: String, folderName: String): String? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.sync.com")
            .addPathSegment("v2")
            .addPathSegment("files")
            .addPathSegment("create-folder")
            .build()

        val formBody = FormBody.Builder()
            .add("parent_id", parentFolderId)
            .add("name", folderName)
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiToken")
            .post(formBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error creating folder: ${response.body?.string()}")
                return null
            }
            val responseBody = response.body?.string()
            val createdFolder: SyncItem = gson.fromJson(responseBody, SyncItem::class.java)
            Log.d(TAG, "Folder '$folderName' created with ID: ${createdFolder.id}")
            return createdFolder.id
        } catch (e: IOException) {
            Log.e(TAG, "Network error creating folder '$folderName'", e)
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
            .host("api.sync.com")
            .addPathSegment("v2")
            .addPathSegment("files")
            .addPathSegment("upload")
            .build()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("parent_id", parentFolderId)
            .addFormDataPart(
                "file",
                localFile.name,
                localFile.asRequestBody("application/octet-stream".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiToken")
            .post(requestBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error uploading file: ${response.body?.string()}")
                return null
            }
            val responseBody = response.body?.string()
            val uploadedFile: SyncItem = gson.fromJson(responseBody, SyncItem::class.java)
            Log.d(TAG, "File '${uploadedFile.name}' uploaded with ID: ${uploadedFile.id}")
            return uploadedFile.id
        } catch (e: IOException) {
            Log.e(TAG, "Network error uploading file", e)
            return null
        }
    }

    override suspend fun saveFileByPath(fullRemotePath: String, localFilePath: String): String? {
        val pathSegments = fullRemotePath.removePrefix("/").split('/').filter { it.isNotEmpty() }
        var currentParentId = SYNC_ROOT_FOLDER_ID

        for (segment in pathSegments.dropLast(1)) {
            val folderId = getOrCreateFolder(currentParentId, segment)
            if (folderId != null) {
                currentParentId = folderId
            } else {
                Log.e(TAG, "Could not find or create parent path segment '$segment' for '$fullRemotePath'")
                return null
            }
        }
        return saveFile(currentParentId, localFilePath)
    }

    override suspend fun downloadFile(remoteFilePath: String): String? {
        // Step 1: Resolve path to file ID
        val fileId = resolvePathToFileId(remoteFilePath)
        if (fileId == null) {
            Log.e(TAG, "Could not resolve path to file ID for: $remoteFilePath")
            return null
        }

        // Step 2: Get a download link for the file
        val downloadUrl = getDownloadLink(fileId)
        if (downloadUrl == null) {
            Log.e(TAG, "Could not get download link for file ID: $fileId")
            return null
        }

        // Step 3: Download the file content
        val fileName = remoteFilePath.substringAfterLast('/')
        val localDir = File(context.filesDir, "$LOCAL_PARENT_DIR/${remoteFilePath.substringBeforeLast('/')}")
        if (!localDir.exists()) {
            localDir.mkdirs()
        }
        val localFile = File(localDir, fileName)

        val request = Request.Builder().url(downloadUrl).build() // No auth needed for the temporary link
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

    private suspend fun resolvePathToFileId(path: String): String? {
        val pathSegments = path.removePrefix("/").split('/').filter { it.isNotEmpty() }
        var currentParentId = SYNC_ROOT_FOLDER_ID
        var targetId: String? = null

        for (segment in pathSegments) {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.sync.com")
                .addPathSegment("v2")
                .addPathSegment("files")
                .addPathSegment("list-folder")
                .addQueryParameter("parent_id", currentParentId)
                .build()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiToken")
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) return null
                val responseBody = response.body?.string()
                val itemType = object : TypeToken<List<SyncItem>>() {}.type
                val items: List<SyncItem> = gson.fromJson(responseBody, itemType)

                val foundItem = items.find { it.name == segment }
                if (foundItem != null) {
                    targetId = foundItem.id
                    currentParentId = foundItem.id // For the next iteration
                } else {
                    return null // Path segment not found
                }
            } catch (e: IOException) {
                return null
            }
        }
        return targetId
    }

    private suspend fun getDownloadLink(fileId: String): String? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.sync.com")
            .addPathSegment("v2")
            .addPathSegment("files")
            .addPathSegment("get-link")
            .build()

        val formBody = FormBody.Builder().add("id", fileId).build()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiToken")
            .post(formBody)
            .build()
        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val responseBody = response.body?.string()
            val downloadLink: SyncDownloadLink = gson.fromJson(responseBody, SyncDownloadLink::class.java)
            return downloadLink.url
        } catch (e: IOException) {
            return null
        }
    }

    // Helper data classes for parsing Sync.com JSON responses
    private data class SyncItem(
        val id: String,
        val type: String, // "d" for directory, "f" for file
        val name: String
    )

    private data class SyncDownloadLink(
        val url: String
    )
}
