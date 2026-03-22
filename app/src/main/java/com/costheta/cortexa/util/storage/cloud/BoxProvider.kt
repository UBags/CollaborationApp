/*
 * =====================================================================================
 * FILE: BoxProvider.kt
 * DESCRIPTION: Implementation of the CloudStorageProvider for the Box Platform.
 * =====================================================================================
 */
package com.costheta.cortexa.util.storage.cloud

import android.content.Context
import android.util.Log
import com.box.androidsdk.content.BoxApiFile
import com.box.androidsdk.content.BoxApiFolder
import com.box.androidsdk.content.BoxException
import com.box.androidsdk.content.models.BoxFolder
import com.box.androidsdk.content.models.BoxItem
import com.box.androidsdk.content.requests.BoxRequestsFile
import com.box.androidsdk.content.requests.BoxRequestsFolder
import com.costheta.cortexa.util.storage.CloudStorageProvider
import com.costheta.cortexa.util.storage.CortexaFolder
import java.io.File
import java.io.IOException

/**
 * Concrete implementation of CloudStorageProvider for interacting with Box.
 * This class requires an authenticated BoxSession.
 */
class BoxProvider(
    private val context: Context,
    private val boxApiFolder: BoxApiFolder,
    private val boxApiFile: BoxApiFile
) : CloudStorageProvider {

    private val TAG = "BoxProvider"
    private val CORTEXA_ROOT_FOLDER_NAME = "cortexa"
    private val LOCAL_PARENT_DIR = "cortexa"
    private val BOX_ROOT_FOLDER_ID = "0"

    // --- App-Specific 'cortexa' Implementations ---

    override suspend fun initializeCortexaStructure(): Boolean {
        Log.d(TAG, "Initializing 'cortexa' folder structure in Box...")
        val cortexaRootId = getOrCreateFolder(BOX_ROOT_FOLDER_ID, CORTEXA_ROOT_FOLDER_NAME)
        if (cortexaRootId == null) {
            Log.e(TAG, "Failed to create or find the main 'cortexa' root folder.")
            return false
        }

        for (folder in CortexaFolder.entries) {
            if (getOrCreateFolder(cortexaRootId, folder.path) == null) {
                Log.e(TAG, "Failed to create or find sub-folder: ${folder.path}")
                return false // Abort on first failure
            }
        }
        Log.d(TAG, "'cortexa' structure initialization successful in Box.")
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
        return try {
            // Check if folder exists
            val folderItems = boxApiFolder.getItemsRequest(parentId).send()
            val existingFolder = folderItems.find { it is BoxFolder && it.name == folderName }

            if (existingFolder != null) {
                existingFolder.id
            } else {
                // If not, create it
                createFolder(parentId, folderName)
            }
        } catch (e: BoxException) {
            Log.e(TAG, "Error getting or creating folder '$folderName'", e)
            null
        }
    }

    override suspend fun createFolder(parentFolderId: String, folderName: String): String? {
        return try {
            val createRequest: BoxRequestsFolder.CreateFolder = boxApiFolder.getCreateRequest(parentFolderId, folderName)
            val folder: BoxFolder = createRequest.send()
            Log.d(TAG, "Folder '$folderName' created with ID: ${folder.id}")
            folder.id
        } catch (e: BoxException) {
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
            val uploadRequest: BoxRequestsFile.UploadFile = boxApiFile.getUploadRequest(localFile, parentFolderId)
            val file = uploadRequest.send()
            Log.d(TAG, "File '${file.name}' uploaded with ID: ${file.id}")
            file.id
        } catch (e: BoxException) {
            Log.e(TAG, "Error uploading file to folder ID '$parentFolderId'", e)
            null
        } catch (e: IOException) {
            Log.e(TAG, "Error reading local file for upload: $localFilePath", e)
            null
        }
    }

    override suspend fun saveFileByPath(fullRemotePath: String, localFilePath: String): String? {
        val pathSegments = fullRemotePath.removePrefix("/").split('/').filter { it.isNotEmpty() }
        var currentParentId = BOX_ROOT_FOLDER_ID

        // Traverse the path and create folders as needed
        // We drop the last segment because that will be the file name, not a folder
        for (segment in pathSegments.dropLast(1)) {
            val folderId = getOrCreateFolder(currentParentId, segment)
            if (folderId != null) {
                currentParentId = folderId
            } else {
                Log.e(TAG, "Could not find or create parent path segment '$segment' for '$fullRemotePath'")
                return null
            }
        }

        // Now, get the file name from the local path and use it for the upload
        val localFile = File(localFilePath)
        if (!localFile.exists()) {
            Log.e(TAG, "Local file does not exist: $localFilePath")
            return null
        }

        // Upload the file to the final folder
        return saveFile(currentParentId, localFilePath)
    }

    override suspend fun downloadFile(remoteFilePath: String): String? {
        val pathSegments = remoteFilePath.removePrefix("/").split('/').filter { it.isNotEmpty() }
        val fileName = pathSegments.last()
        var currentParentId = BOX_ROOT_FOLDER_ID
        var targetItem: BoxItem? = null

        // Traverse the path to find the file
        try {
            for (segment in pathSegments) {
                val items = boxApiFolder.getItemsRequest(currentParentId).send()
                targetItem = items.find { it.name == segment }
                if (targetItem != null) {
                    currentParentId = targetItem.id
                } else {
                    Log.e(TAG, "Could not find item '$segment' in path '$remoteFilePath'")
                    return null
                }
            }
        } catch (e: BoxException) {
            Log.e(TAG, "Error traversing path to find file '$remoteFilePath'", e)
            return null
        }

        if (targetItem == null || targetItem !is com.box.androidsdk.content.models.BoxFile) {
            Log.e(TAG, "Remote path does not point to a file: $remoteFilePath")
            return null
        }

        val localDir = File(context.filesDir, "$LOCAL_PARENT_DIR/${pathSegments.dropLast(1).joinToString("/")}")
        if (!localDir.exists()) {
            localDir.mkdirs()
        }
        val localFile = File(localDir, fileName)

        return try {
            // CORRECTED: The arguments were swapped. It should be (destinationFile, fileId).
            val downloadRequest: BoxRequestsFile.DownloadFile = boxApiFile.getDownloadRequest(localFile, targetItem.id)
            downloadRequest.send()
            Log.d(TAG, "File downloaded to: ${localFile.absolutePath}")
            localFile.absolutePath
        } catch (e: BoxException) {
            Log.e(TAG, "Error downloading file", e)
            null
        } catch (e: IOException) {
            Log.e(TAG, "Error writing downloaded file to local storage", e)
            null
        }
    }
}
