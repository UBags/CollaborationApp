/*
 * =====================================================================================
 * FILE: LocalFileSystemProvider.kt
 * DESCRIPTION: Implementation of the CloudStorageProvider for the local device storage.
 * =====================================================================================
 */
package com.costheta.cortexa.util.storage.local

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.content.Context
import android.util.Log
import com.costheta.cortexa.R
import com.costheta.cortexa.util.storage.CloudStorageProvider
import com.costheta.cortexa.util.storage.CortexaFolder
import java.io.File
import java.io.IOException

/**
 * Concrete implementation of CloudStorageProvider for interacting with the local file system.
 * All paths are relative to the application's private files directory.
 */
class LocalFileSystemProvider(private val context: Context) : CloudStorageProvider {

    private val TAG = "LocalFileSystemProvider"
    private val CORTEXA_ROOT_FOLDER_NAME = context.getString(R.string.cortexa_folder_name)
    private val rootDir = File(context.filesDir, CORTEXA_ROOT_FOLDER_NAME)

    override suspend fun initializeCortexaStructure(): Boolean {
        try {
            if (!rootDir.exists()) {
                rootDir.mkdirs()
            }
            for (folder in CortexaFolder.entries) {
                val subDir = File(rootDir, folder.path)
                if (!subDir.exists()) {
                    subDir.mkdirs()
                }
            }
            Log.d(TAG, "'cortexa' structure initialization successful on local storage.")
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to create 'cortexa' structure due to security exception.", e)
            return false
        }
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

    override suspend fun createFolder(parentFolderId: String, folderName: String): String? {
        try {
            // The parentFolderPath is relative to the app's files directory.
            val newDir = File(File(context.filesDir, parentFolderId), folderName)
            if (!newDir.exists()) {
                newDir.mkdirs()
            }
            Log.d(TAG, "Local folder created at: ${newDir.absolutePath}")
            // Return the path relative to the app's files directory.
            return newDir.relativeTo(context.filesDir).path
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to create folder '$folderName'", e)
            return null
        }
    }

    override suspend fun saveFile(parentFolderId: String, localFilePath: String): String? {
        val sourceFile = File(localFilePath)
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file does not exist: $localFilePath")
            return null
        }
        val destDir = File(context.filesDir, parentFolderId)
        val destFile = File(destDir, sourceFile.name)

        return try {
            sourceFile.copyTo(destFile, overwrite = true)
            Log.d(TAG, "File copied to: ${destFile.absolutePath}")
            destFile.relativeTo(context.filesDir).path // Return relative path as "ID"
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy file to '$parentFolderId'", e)
            null
        }
    }

    override suspend fun saveFileByPath(fullRemotePath: String, localFilePath: String): String? {
        val parentPath = fullRemotePath.substringBeforeLast('/', "")
        val destDir = File(context.filesDir, parentPath)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        return saveFile(parentPath, localFilePath)
    }

    override suspend fun downloadFile(remoteFilePath: String): String? {
        val file = File(context.filesDir, remoteFilePath)
        return if (file.exists() && file.isFile) {
            Log.d(TAG, "File found at local path: ${file.absolutePath}")
            file.absolutePath
        } else {
            Log.e(TAG, "File does not exist at local path: $remoteFilePath")
            null
        }
    }
}
