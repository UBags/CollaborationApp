/*
 * =====================================================================================
 * FILE: GoogleDriveProvider.kt
 * DESCRIPTION: Implementation of the CloudStorageProvider for Google Drive.
 * =====================================================================================
 */
package com.costheta.cortexa.util.storage.cloud

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.content.Context
import android.util.Log
import com.costheta.cortexa.util.storage.CloudStorageProvider
import com.costheta.cortexa.util.storage.CortexaFolder
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Concrete implementation of CloudStorageProvider for interacting with Google Drive.
 */
class GoogleDriveProvider(
    private val context: Context,
    private val driveService: Drive?
) : CloudStorageProvider {

    private val TAG = "GoogleDriveProvider"
    private val CORTEXA_ROOT_FOLDER_NAME = "cortexa"
    private val LOCAL_PARENT_DIR = "cortexa"

    // --- App-Specific 'cortexa' Implementations ---

    override suspend fun initializeCortexaStructure(): Boolean {
        Log.d(TAG, "Initializing 'cortexa' folder structure...")
        val cortexaRootId = getOrCreateFolderPath(CORTEXA_ROOT_FOLDER_NAME)
        if (cortexaRootId == null) {
            Log.e(TAG, "Failed to create or find the main 'cortexa' root folder.")
            return false
        }

        for (folder in CortexaFolder.values()) {
            val subFolderPath = "$CORTEXA_ROOT_FOLDER_NAME/${folder.path}"
            if (getOrCreateFolderPath(subFolderPath) == null) {
                Log.e(TAG, "Failed to create or find sub-folder: ${folder.path}")
                return false // Abort on first failure
            }
        }
        Log.d(TAG, "'cortexa' structure initialization successful.")
        return true
    }

    override suspend fun saveFileToCortexa(folderType: CortexaFolder, specificSubFolderName: String, localFilePath: String): String? {
        val fullRemotePath = "$CORTEXA_ROOT_FOLDER_NAME/${folderType.path}/$specificSubFolderName"
        return saveFileByPath(fullRemotePath, localFilePath)
    }

    override suspend fun downloadFileFromCortexa(folderType: CortexaFolder, specificSubFolderName: String, fileName: String): String? {
        val remoteFilePath = "$CORTEXA_ROOT_FOLDER_NAME/${folderType.path}/$specificSubFolderName/$fileName"
        return downloadFile(remoteFilePath)
    }


    // --- Generic Implementations ---

    private suspend fun getOrCreateFolderPath(path: String): String? {
        if (driveService == null) {
            Log.e(TAG, "Drive service is not initialized.")
            return null
        }
        val folderNames = path.split('/').filter { it.isNotEmpty() }
        var currentParentId = "root"

        for (folderName in folderNames) {
            try {
                val query = "mimeType='application/vnd.google-apps.folder' and name='$folderName' and '$currentParentId' in parents and trashed=false"
                val files = driveService.files().list().setQ(query).setSpaces("drive").setFields("files(id)").execute().files
                currentParentId = if (files.isNotEmpty()) {
                    files[0].id
                } else {
                    createFolder(currentParentId, folderName) ?: return null
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error processing path segment '$folderName'", e)
                return null
            }
        }
        return currentParentId
    }

    override suspend fun createFolder(parentFolderId: String, folderName: String): String? {
        if (driveService == null) {
            Log.e(TAG, "Drive service is not initialized.")
            return null
        }
        val fileMetadata = File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf(parentFolderId)
        }
        return try {
            val file = driveService.files().create(fileMetadata).setFields("id").execute()
            Log.d(TAG, "Folder '$folderName' created with ID: ${file.id}")
            file.id
        } catch (e: Exception) {
            Log.e(TAG, "Error creating folder '$folderName'", e)
            null
        }
    }

    override suspend fun saveFile(parentFolderId: String, localFilePath: String): String? {
        if (driveService == null) {
            Log.e(TAG, "Drive service is not initialized.")
            return null
        }
        val localFile = java.io.File(localFilePath)
        if (!localFile.exists()) {
            Log.e(TAG, "Local file does not exist: $localFilePath")
            return null
        }
        val fileMetadata = File().apply {
            name = localFile.name
            parents = listOf(parentFolderId)
        }
        val mimeType = context.contentResolver.getType(android.net.Uri.fromFile(localFile)) ?: "application/octet-stream"
        val mediaContent = FileContent(mimeType, localFile)
        return try {
            val file = driveService.files().create(fileMetadata, mediaContent).setFields("id, name").execute()
            Log.d(TAG, "File '${file.name}' uploaded with ID: ${file.id}")
            file.id
        } catch (e: IOException) {
            Log.e(TAG, "Error uploading file to folder ID '$parentFolderId'", e)
            null
        }
    }

    override suspend fun saveFileByPath(fullRemotePath: String, localFilePath: String): String? {
        val parentPath = fullRemotePath.substringBeforeLast('/', "")
        val finalParentId = getOrCreateFolderPath(parentPath)
        return if (finalParentId != null) {
            saveFile(finalParentId, localFilePath)
        } else {
            Log.e(TAG, "Could not find or create parent path for '$fullRemotePath'")
            null
        }
    }

    override suspend fun downloadFile(remoteFilePath: String): String? {
        if (driveService == null) {
            Log.e(TAG, "Drive service is not initialized.")
            return null
        }
        val fileName = remoteFilePath.substringAfterLast('/')
        val query = "name='$fileName' and trashed=false" // Simplification
        val fileId = try {
            driveService.files().list().setQ(query).setSpaces("drive").setFields("files(id)").execute().files.firstOrNull()?.id
        } catch (e: IOException) {
            Log.e(TAG, "Error searching for remote file: $remoteFilePath", e)
            null
        }
        if (fileId == null) {
            Log.e(TAG, "Could not find remote file: $remoteFilePath")
            return null
        }
        val localDir = java.io.File(context.filesDir, LOCAL_PARENT_DIR + java.io.File.separator + remoteFilePath.substringBeforeLast('/'))
        if (!localDir.exists()) {
            localDir.mkdirs()
        }
        val localFile = java.io.File(localDir, fileName)
        return try {
            val outputStream: OutputStream = FileOutputStream(localFile)
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.close()
            Log.d(TAG, "File downloaded to: ${localFile.absolutePath}")
            localFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Error downloading file", e)
            null
        }
    }
}