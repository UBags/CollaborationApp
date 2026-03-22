/*
 * =====================================================================================
 * FILE: OneDriveHttpProvider.kt
 * DESCRIPTION: Implementation of the CloudStorageProvider for Microsoft OneDrive
 * using direct HTTP REST API calls.
 * NOTE: This implementation requires an HTTP client like OkHttp and a JSON parser like Gson.
 * =====================================================================================
 */
package com.costheta.cortexa.util.storage.cloud

import android.content.Context
import android.util.Log
import com.costheta.cortexa.util.storage.CloudStorageProvider
import com.costheta.cortexa.util.storage.CortexaFolder
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Concrete implementation of CloudStorageProvider for interacting with OneDrive via HTTP.
 * This class requires a Microsoft Graph API access token.
 */
class OneDriveHttpProvider(
    private val context: Context,
    private val accessToken: String
) : CloudStorageProvider {

    private val TAG = "OneDriveHttpProvider"
    private val CORTEXA_ROOT_FOLDER_NAME = "cortexa"
    private val LOCAL_PARENT_DIR = "cortexa"
    private val GRAPH_API_BASE_URL = "https://graph.microsoft.com/v1.0/me/drive"

    private val httpClient = OkHttpClient()
    private val gson = Gson()

    // --- App-Specific 'cortexa' Implementations ---

    override suspend fun initializeCortexaStructure(): Boolean {
        Log.d(TAG, "Initializing 'cortexa' folder structure in OneDrive...")
        val cortexaRootId = getOrCreateFolder("root", CORTEXA_ROOT_FOLDER_NAME)
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
        Log.d(TAG, "'cortexa' structure initialization successful in OneDrive.")
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
        val folderId = getFolderId(parentId, folderName)
        return folderId ?: createFolder(parentId, folderName)
    }

    private suspend fun getFolderId(parentId: String, folderName: String): String? {
        val url = "$GRAPH_API_BASE_URL/items/$parentId/children".toHttpUrlOrNull()!!
            .newBuilder()
            .addQueryParameter("\$filter", "name eq '$folderName'")
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error listing folder contents: ${response.body?.string()}")
                return null
            }

            val responseBody = response.body?.string()
            val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
            val items = jsonObject.getAsJsonArray("value")
            if (items.size() > 0) {
                return items[0].asJsonObject.get("id").asString
            }
            return null
        } catch (e: IOException) {
            Log.e(TAG, "Network error getting folder ID for '$folderName'", e)
            return null
        }
    }

    override suspend fun createFolder(parentFolderId: String, folderName: String): String? {
        val url = "$GRAPH_API_BASE_URL/items/$parentFolderId/children"
        val json = """
            {
              "name": "$folderName",
              "folder": { },
              "@microsoft.graph.conflictBehavior": "rename"
            }
        """.trimIndent()

        val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(requestBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error creating folder: ${response.body?.string()}")
                return null
            }
            val responseBody = response.body?.string()
            val createdFolder = gson.fromJson(responseBody, OneDriveItem::class.java)
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
        val fileName = localFile.name
        val url = "$GRAPH_API_BASE_URL/items/$parentFolderId:/$fileName:/content"

        val requestBody = localFile.asRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .put(requestBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "API Error uploading file: ${response.body?.string()}")
                return null
            }
            val responseBody = response.body?.string()
            val uploadedFile = gson.fromJson(responseBody, OneDriveItem::class.java)
            Log.d(TAG, "File '${uploadedFile.name}' uploaded with ID: ${uploadedFile.id}")
            return uploadedFile.id
        } catch (e: IOException) {
            Log.e(TAG, "Network error uploading file", e)
            return null
        }
    }

    override suspend fun saveFileByPath(fullRemotePath: String, localFilePath: String): String? {
        val pathSegments = fullRemotePath.removePrefix("/").split('/').filter { it.isNotEmpty() }
        var currentParentId = "root"

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
        val url = "$GRAPH_API_BASE_URL/root:${remoteFilePath}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        try {
            // First, get the file metadata to find the download URL
            val metaResponse = httpClient.newCall(request).execute()
            if (!metaResponse.isSuccessful) {
                Log.e(TAG, "API Error getting file metadata: ${metaResponse.body?.string()}")
                return null
            }
            val metaBody = metaResponse.body?.string()
            val jsonObject = gson.fromJson(metaBody, JsonObject::class.java)
            val downloadUrl = jsonObject.get("@microsoft.graph.downloadUrl").asString

            // Now, download the actual content
            val downloadRequest = Request.Builder().url(downloadUrl).build()
            val contentResponse = httpClient.newCall(downloadRequest).execute()
            if (!contentResponse.isSuccessful) {
                Log.e(TAG, "Failed to download file content: ${contentResponse.message}")
                return null
            }

            val fileName = remoteFilePath.substringAfterLast('/')
            val localDir = File(context.filesDir, "$LOCAL_PARENT_DIR/${remoteFilePath.substringBeforeLast('/')}")
            if (!localDir.exists()) {
                localDir.mkdirs()
            }
            val localFile = File(localDir, fileName)

            contentResponse.body?.byteStream()?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "File downloaded to: ${localFile.absolutePath}")
            return localFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Network error during download", e)
            return null
        }
    }

    // Helper data class for parsing OneDrive JSON responses
    private data class OneDriveItem(val id: String, val name: String)
}
