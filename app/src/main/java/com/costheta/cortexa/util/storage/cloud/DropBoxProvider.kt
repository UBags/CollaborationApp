/*
 * =====================================================================================
 * FILE: DropBoxProvider.kt
 * DESCRIPTION: Implementation of the CloudStorageProvider for Dropbox.
 * =====================================================================================
 */
package com.costheta.cortexa.util.storage.cloud

import android.content.Context
import android.util.Log
import com.costheta.cortexa.util.storage.CloudStorageProvider
import com.costheta.cortexa.util.storage.CortexaFolder
import com.dropbox.core.DbxException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.CreateFolderErrorException
import com.dropbox.core.v2.files.DownloadErrorException
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.WriteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Concrete implementation of CloudStorageProvider for interacting with Dropbox.
 * Assumes a DbxClientV2 instance is provided after user authentication.
 */
class DropBoxProvider(
    private val context: Context,
    private val dbxClient: DbxClientV2?
) : CloudStorageProvider {

    private val TAG = "DropBoxProvider"
    private val CORTEXA_ROOT_FOLDER_NAME = "cortexa"
    private val LOCAL_PARENT_DIR = "cortexa"

    // --- App-Specific 'cortexa' Implementations ---

    /**
     * Ensures the entire 'cortexa' folder structure exists in Dropbox.
     * It creates the root 'cortexa' folder and all its sub-folders.
     */
    override suspend fun initializeCortexaStructure(): Boolean {
        Log.d(TAG, "Initializing 'cortexa' folder structure in Dropbox...")
        // In Dropbox, paths are prefixed with a forward slash
        val cortexaRootPath = "/$CORTEXA_ROOT_FOLDER_NAME"
        if (getOrCreateFolderPath(cortexaRootPath) == null) {
            Log.e(TAG, "Failed to create or find the main 'cortexa' root folder.")
            return false
        }

        // Create all sub-folders defined in the CortexaFolder enum
        for (folder in CortexaFolder.entries) {
            val subFolderPath = "$cortexaRootPath/${folder.path}"
            if (getOrCreateFolderPath(subFolderPath) == null) {
                Log.e(TAG, "Failed to create or find sub-folder: ${folder.path}")
                return false // Abort if any sub-folder creation fails
            }
        }
        Log.d(TAG, "'cortexa' structure initialization successful in Dropbox.")
        return true
    }

    /**
     * Saves a local file to a specific sub-folder within the 'cortexa' structure in Dropbox.
     */
    override suspend fun saveFileToCortexa(
        folderType: CortexaFolder,
        specificSubFolderName: String,
        localFilePath: String
    ): String? {
        val fullRemotePath = "/$CORTEXA_ROOT_FOLDER_NAME/${folderType.path}/$specificSubFolderName"
        return saveFileByPath(fullRemotePath, localFilePath)
    }

    /**
     * Downloads a file from a specific sub-folder within the 'cortexa' structure in Dropbox.
     */
    override suspend fun downloadFileFromCortexa(
        folderType: CortexaFolder,
        specificSubFolderName: String,
        fileName: String
    ): String? {
        val remoteFilePath = "/$CORTEXA_ROOT_FOLDER_NAME/${folderType.path}/$specificSubFolderName/$fileName"
        return downloadFile(remoteFilePath)
    }


    // --- Generic Implementations ---

    /**
     * A helper function to check if a folder exists at a given path and create it if it doesn't.
     * This is now a suspend function to handle network operations correctly.
     * @param path The full path of the folder in Dropbox (e.g., "/cortexa/Assignments").
     * @return The FolderMetadata if successful, or null on failure.
     */
    private suspend fun getOrCreateFolderPath(path: String): FolderMetadata? {
        if (dbxClient == null) {
            Log.e(TAG, "Dropbox client is not initialized.")
            return null
        }
        return try {
            // Try to get metadata for the path. If it's a folder, return it.
            dbxClient.files().getMetadata(path) as FolderMetadata
        } catch (e: DbxException) {
            // If getMetadata fails, it's likely because the folder does not exist.
            Log.w(TAG, "Folder '$path' not found, attempting to create it.")
            try {
                // We attempt to create the folder directly and return its metadata.
                val folderResult = dbxClient.files().createFolderV2(path)
                folderResult.metadata
            } catch (createException: CreateFolderErrorException) {
                // This can happen in a race condition. If it's a conflict, the folder now exists.
                if (createException.errorValue.isPath && createException.errorValue.pathValue.isConflict) {
                    Log.w(TAG, "Folder '$path' was created by another process, fetching metadata again.")
                    try {
                        dbxClient.files().getMetadata(path) as FolderMetadata
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to get metadata for conflicting folder '$path'", e2)
                        null
                    }
                } else {
                    Log.e(TAG, "Error creating folder '$path'", createException)
                    null
                }
            } catch (e2: DbxException) {
                Log.e(TAG, "Another Dropbox error occurred while creating folder '$path'", e2)
                null
            }
        } catch (e: ClassCastException) {
            Log.e(TAG, "An item exists at '$path', but it is not a folder.")
            null
        }
    }

    /**
     * Creates a folder in Dropbox. Since Dropbox is path-based, parentFolderId is interpreted as a parent path.
     * @param parentFolderId The path of the parent folder. Use "" for the root.
     * @param folderName The name of the new folder.
     * @return The path of the created folder, or null on failure.
     */
    override suspend fun createFolder(parentFolderId: String, folderName: String): String? {
        if (dbxClient == null) {
            Log.e(TAG, "Dropbox client is not initialized.")
            return null
        }
        // Construct the full path for the new folder
        val fullPath = if (parentFolderId.isEmpty() || parentFolderId == "/") "/$folderName" else "$parentFolderId/$folderName"

        return try {
            val folder = dbxClient.files().createFolderV2(fullPath)
            Log.d(TAG, "Folder '${folder.metadata.name}' created at path: ${folder.metadata.pathDisplay}")
            folder.metadata.pathDisplay // Return the display path as the "ID"
        } catch (e: CreateFolderErrorException) {
            // This error can occur if the folder already exists.
            if (e.errorValue.isPath && e.errorValue.pathValue.isConflict) {
                Log.w(TAG, "Folder '$fullPath' already exists.")
                // If it already exists, we can consider it a success and return its path.
                return fullPath
            }
            Log.e(TAG, "Error creating folder '$fullPath'", e)
            null
        } catch (e: DbxException) {
            Log.e(TAG, "Generic Dropbox error creating folder '$fullPath'", e)
            null
        }
    }

    /**
     * Saves a local file to a specified folder path in Dropbox.
     * @param parentFolderId The full path of the cloud folder where the file will be saved.
     * @param localFilePath The absolute path to the local file on the device.
     * @return The path of the uploaded file, or null on failure.
     */
    override suspend fun saveFile(parentFolderId: String, localFilePath: String): String? {
        if (dbxClient == null) {
            Log.e(TAG, "Dropbox client is not initialized.")
            return null
        }
        val localFile = File(localFilePath)
        if (!localFile.exists()) {
            Log.e(TAG, "Local file does not exist: $localFilePath")
            return null
        }

        val remotePath = "$parentFolderId/${localFile.name}"

        return try {
            withContext(Dispatchers.IO) {
                FileInputStream(localFile).use { inputStream ->
                    val metadata: FileMetadata = dbxClient.files().uploadBuilder(remotePath)
                        .withMode(WriteMode.OVERWRITE) // Overwrite if file with same name exists
                        .uploadAndFinish(inputStream)
                    Log.d(TAG, "File '${metadata.name}' uploaded to path: ${metadata.pathDisplay}")
                    metadata.pathDisplay // Return the path as the identifier
                }
            }
        } catch (e: DbxException) {
            Log.e(TAG, "Error uploading file to '$remotePath'", e)
            null
        } catch (e: IOException) {
            Log.e(TAG, "Error reading local file for upload: $localFilePath", e)
            null
        }
    }

    /**
     * Saves a local file to a full path in Dropbox, creating parent directories as needed.
     */
    override suspend fun saveFileByPath(fullRemotePath: String, localFilePath: String): String? {
        val parentPath = fullRemotePath.substringBeforeLast('/', "")
        // Ensure the parent directory structure exists first
        if (getOrCreateFolderPath(parentPath) != null) {
            return saveFile(parentPath, localFilePath)
        } else {
            Log.e(TAG, "Could not find or create parent path for '$fullRemotePath'")
            return null
        }
    }

    /**
     * Downloads a file from Dropbox to the local 'cortexa' directory.
     */
    override suspend fun downloadFile(remoteFilePath: String): String? {
        if (dbxClient == null) {
            Log.e(TAG, "Dropbox client is not initialized.")
            return null
        }
        val fileName = remoteFilePath.substringAfterLast('/')
        // Create the local directory structure based on the remote path
        val localDir = File(context.filesDir, "$LOCAL_PARENT_DIR/${remoteFilePath.substringBeforeLast('/')}")
        if (!localDir.exists()) {
            localDir.mkdirs()
        }
        val localFile = File(localDir, fileName)

        return try {
            withContext(Dispatchers.IO) {
                FileOutputStream(localFile).use { outputStream ->
                    dbxClient.files().download(remoteFilePath).download(outputStream)
                }
            }
            Log.d(TAG, "File downloaded to: ${localFile.absolutePath}")
            localFile.absolutePath
        } catch (e: DownloadErrorException) {
            Log.e(TAG, "Error downloading file: Remote file not found or access denied.", e)
            null
        } catch (e: DbxException) {
            Log.e(TAG, "Generic Dropbox error downloading file '$remoteFilePath'", e)
            null
        } catch (e: IOException) {
            Log.e(TAG, "Error writing downloaded file to local storage", e)
            null
        }
    }
}
