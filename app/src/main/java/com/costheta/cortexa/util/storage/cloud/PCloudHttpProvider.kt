/*
 * =====================================================================================
 * FILE: PCloudProvider.kt
 * DESCRIPTION: Implementation of the CloudStorageProvider for pCloud.
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
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Concrete implementation of CloudStorageProvider for interacting with pCloud.
 * This class requires a pCloud authentication token.
 */
class PCloudHttpProvider(
    private val context: Context,
    private val authToken: String
) : CloudStorageProvider {

    private val TAG = "PCloudProvider"
    private val CORTEXA_ROOT_FOLDER_NAME = "cortexa"
    private val LOCAL_PARENT_DIR = "cortexa"
    private val PCLOUD_ROOT_FOLDER_ID = 0L // pCloud uses Long for IDs

    private val httpClient = OkHttpClient()
    private val gson = Gson()

    // --- App-Specific 'cortexa' Implementations ---

    override suspend fun initializeCortexaStructure(): Boolean {
        Log.d(TAG, "Initializing 'cortexa' folder structure in pCloud...")
        val cortexaRootId = getOrCreateFolder(PCLOUD_ROOT_FOLDER_ID, CORTEXA_ROOT_FOLDER_NAME)
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
        Log.d(TAG, "'cortexa' structure initialization successful in pCloud.")
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

    private suspend fun getOrCreateFolder(parentId: Long, folderName: String): Long? {
        // Check if folder exists
        val folderId = getFolderId(parentId, folderName)
        return folderId ?: createFolder(parentId.toString(), folderName)?.toLongOrNull()
    }

    private suspend fun getFolderId(parentId: Long, folderName: String): Long? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.pcloud.com")
            .addPathSegment("listfolder")
            .addQueryParameter("auth", authToken)
            .addQueryParameter("folderid", parentId.toString())
            .build()

        val request = Request.Builder().url(url).build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error listing folder: ${response.body.string()}")
                return null
            }

            val responseBody = response.body.string()
            val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
            val metadata = jsonObject.getAsJsonObject("metadata")
            val contents = metadata.getAsJsonArray("contents")

            val folderType = object : TypeToken<List<PCloudItem>>() {}.type
            val items: List<PCloudItem> = gson.fromJson(contents, folderType)

            return items.find { it.isfolder && it.name == folderName }?.folderid
        } catch (e: IOException) {
            Log.e(TAG, "Network error getting folder ID for '$folderName'", e)
            return null
        }
    }

    override suspend fun createFolder(parentFolderId: String, folderName: String): String? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.pcloud.com")
            .addPathSegment("createfolder")
            .addQueryParameter("auth", authToken)
            .addQueryParameter("folderid", parentFolderId)
            .addQueryParameter("name", folderName)
            .build()

        val request = Request.Builder().url(url).build()
        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error creating folder: ${response.body.string()}")
                return null
            }
            val responseBody = response.body.string()
            val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
            val metadata = jsonObject.getAsJsonObject("metadata")
            val newFolderId = metadata.get("folderid").asLong
            Log.d(TAG, "Folder '$folderName' created with ID: $newFolderId")
            return newFolderId.toString()
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
            .host("api.pcloud.com")
            .addPathSegment("uploadfile")
            .build()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("auth", authToken)
            .addFormDataPart("folderid", parentFolderId)
            .addFormDataPart(
                "file",
                localFile.name,
                localFile.asRequestBody("application/octet-stream".toMediaType())
            )
            .build()

        val request = Request.Builder().url(url).post(requestBody).build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error uploading file: ${response.body.string()}")
                return null
            }
            val responseBody = response.body.string()
            val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
            val metadata = jsonObject.getAsJsonArray("metadata").get(0).asJsonObject
            val fileId = metadata.get("fileid").asLong
            Log.d(TAG, "File '${localFile.name}' uploaded with ID: $fileId")
            return fileId.toString()
        } catch (e: IOException) {
            Log.e(TAG, "Network error uploading file", e)
            return null
        }
    }

    override suspend fun saveFileByPath(fullRemotePath: String, localFilePath: String): String? {
        val pathSegments = fullRemotePath.removePrefix("/").split('/').filter { it.isNotEmpty() }
        var currentParentId = PCLOUD_ROOT_FOLDER_ID

        for (segment in pathSegments.dropLast(1)) {
            val folderId = getOrCreateFolder(currentParentId, segment)
            if (folderId != null) {
                currentParentId = folderId
            } else {
                Log.e(TAG, "Could not find or create parent path segment '$segment' for '$fullRemotePath'")
                return null
            }
        }
        return saveFile(currentParentId.toString(), localFilePath)
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

        val request = Request.Builder().url(downloadUrl).build()
        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download file content: ${response.message}")
                return null
            }
            response.body.byteStream().use { input ->
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

    private suspend fun resolvePathToFileId(path: String): Long? {
        val pathSegments = path.removePrefix("/").split('/').filter { it.isNotEmpty() }
        var currentParentId = PCLOUD_ROOT_FOLDER_ID
        var targetId: Long? = null

        for (segment in pathSegments) {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.pcloud.com")
                .addPathSegment("listfolder")
                .addQueryParameter("auth", authToken)
                .addQueryParameter("folderid", currentParentId.toString())
                .build()
            val request = Request.Builder().url(url).build()
            try {
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) return null
                val responseBody = response.body.string()
                val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
                val metadata = jsonObject.getAsJsonObject("metadata")
                val contents = metadata.getAsJsonArray("contents")
                val itemType = object : TypeToken<List<PCloudItem>>() {}.type
                val items: List<PCloudItem> = gson.fromJson(contents, itemType)

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

    private suspend fun getDownloadLink(fileId: Long): String? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.pcloud.com")
            .addPathSegment("getfilelink")
            .addQueryParameter("auth", authToken)
            .addQueryParameter("fileid", fileId.toString())
            .build()
        val request = Request.Builder().url(url).build()
        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val responseBody = response.body.string()
            val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
            val hosts = jsonObject.getAsJsonArray("hosts")
            if (hosts.size() == 0) return null
            val bestHost = hosts[0].asString
            val path = jsonObject.get("path").asString
            return "https://$bestHost$path"
        } catch (e: IOException) {
            return null
        }
    }

    // Helper data class for parsing pCloud JSON responses
    private data class PCloudItem(
        val name: String,
        val isfolder: Boolean,
        val id: Long,
        val fileid: Long?,
        val folderid: Long?
    )
}