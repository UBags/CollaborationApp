/*
 * =====================================================================================
 * FILE: PCloudProvider.kt
 * DESCRIPTION: Implementation of the CloudStorageProvider for pCloud using the official SDK.
 * NOTE: You will need to add the pCloud KTX dependency to your build.gradle file:
 * implementation("com.pcloud.sdk:core-ktx:1.11.0")
 * =====================================================================================
 */
package com.costheta.cortexa.util.storage.cloud

import android.content.Context
import android.util.Log
import com.costheta.cortexa.util.storage.CloudStorageProvider
import com.costheta.cortexa.util.storage.CortexaFolder
import com.pcloud.sdk.*
import java.io.File
import java.io.IOException

/**
 * Concrete implementation of CloudStorageProvider for interacting with pCloud using the official SDK.
 * This class requires an authenticated pCloud ApiClient.
 */
class PCloudProvider(
    private val context: Context,
    private val apiClient: ApiClient
) : CloudStorageProvider {

    private val TAG = "PCloudProvider"
    private val CORTEXA_ROOT_FOLDER_NAME = "cortexa"
    private val LOCAL_PARENT_DIR = "cortexa"
    private val PCLOUD_ROOT_FOLDER_ID = 0L // pCloud's root folder ID is 0

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
        return try {
            val parentFolderContent: RemoteFolder = apiClient.listFolder(parentId).await()
            val existingFolder = parentFolderContent.children()
                .filterIsInstance<RemoteFolder>()
                .find { it.name() == folderName }

            existingFolder?.folderId() ?: createFolder(parentId.toString(), folderName)?.toLongOrNull()
        } catch (e: IOException) {
            Log.e(TAG, "Error getting or creating folder '$folderName'", e)
            null
        }
    }

    override suspend fun createFolder(parentFolderId: String, folderName: String): String? {
        return try {
            val createdFolder: RemoteFolder = apiClient.createFolder(parentFolderId.toLong(), folderName).await()
            Log.d(TAG, "Folder '$folderName' created with ID: ${createdFolder.folderId()}")
            createdFolder.folderId().toString()
        } catch (e: IOException) {
            Log.e(TAG, "Error creating folder '$folderName'", e)
            null
        }
    }

    override suspend fun saveFile(parentFolderId: String, localFilePath: String): String? {
        val localFile = File(localFilePath)
        if (!localFile.exists()) {
            Log.e(TAG, "Local file does not exist: $localFilePath")
            return null
        }

        return try {
            val dataSource = DataSource.create(localFile)
            val uploadedFile: RemoteFile = apiClient.createFile(parentFolderId.toLong(), localFile.name, dataSource).await()
            Log.d(TAG, "File '${uploadedFile.name()}' uploaded with ID: ${uploadedFile.fileId()}")
            uploadedFile.fileId().toString()
        } catch (e: IOException) {
            Log.e(TAG, "Error uploading file to folder ID '$parentFolderId'", e)
            null
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
        try {
            val remoteFile: RemoteFile? = apiClient.loadFile(remoteFilePath).await()
            if (remoteFile == null) {
                Log.e(TAG, "Remote file not found at path: $remoteFilePath")
                return null
            }

            val fileName = remoteFile.name()
            val localDir = File(context.filesDir, "$LOCAL_PARENT_DIR/${remoteFilePath.substringBeforeLast('/')}")
            if (!localDir.exists()) {
                localDir.mkdirs()
            }
            val localFile = File(localDir, fileName)

            // CORRECTED: The download process is two steps.
            // 1. Create a FileLink for the remote file.
            val fileLink: FileLink = apiClient.createFileLink(remoteFile, DownloadOptions.DEFAULT).await()

            // 2. Use the created link to download the content to a DataSink.
            val dataSink = DataSink.create(localFile)
            apiClient.download(fileLink, dataSink).await()
            Log.d(TAG, "File downloaded to: ${localFile.absolutePath}")
            return localFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Error downloading file from path '$remoteFilePath'", e)
            return null
        }
    }
}
