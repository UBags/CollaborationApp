/*
 * =====================================================================================
 * FILE: StorageManager.kt
 * DESCRIPTION: Factory class to get a specific cloud storage provider instance.
 * =====================================================================================
 */
package com.costheta.cortexa.util.storage

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.content.Context
import android.util.Log
import com.box.androidsdk.content.BoxApiFile
import com.box.androidsdk.content.BoxApiFolder
import com.costheta.cortexa.util.storage.cloud.BoxProvider
import com.costheta.cortexa.util.storage.cloud.CantoProvider
import com.costheta.cortexa.util.storage.cloud.DropBoxProvider
import com.costheta.cortexa.util.storage.cloud.GoogleDriveProvider
import com.costheta.cortexa.util.storage.local.LocalFileSystemProvider
import com.costheta.cortexa.util.storage.cloud.OneDriveHttpProvider
import com.costheta.cortexa.util.storage.cloud.OneDriveProvider
import com.costheta.cortexa.util.storage.cloud.PCloudHttpProvider
import com.costheta.cortexa.util.storage.cloud.PCloudProvider
import com.costheta.cortexa.util.storage.cloud.SyncProvider
import com.google.api.services.drive.Drive

/**
 * Enum representing the supported cloud storage services.
 */
enum class CloudService {
    GOOGLE_DRIVE,
    DROPBOX, // Placeholder
    ONE_DRIVE,
    BOX,
    PCLOUD,
    SYNC,
    CANTO,
    LOCAL_STORAGE
}

/**
 * A factory object responsible for creating and providing instances of CloudStorageProvider.
 */
object CloudStorageManager {
    fun getProvider(
        service: CloudService,
        context: Context,
        driveService: Drive? = null,
        dbxClient: com.dropbox.core.v2.DbxClientV2? = null, // Add this parameter
        boxApiFolder: BoxApiFolder? = null,
        boxApiFile: BoxApiFile? = null,
        pCloudAuthToken: String? = null,
        pCloudApiClient: com.pcloud.sdk.ApiClient? = null,
        syncApiToken: String? = null,
        cantoAccessToken: String? = null,
        cantoDomain: String? = null,
        oneDriveToken: String? = null, // Add OneDrive token parameter
        oneDriveGraphClient: com.microsoft.graph.serviceclient.GraphServiceClient? = null
    ): CloudStorageProvider? {
        return when (service) {
            CloudService.GOOGLE_DRIVE -> {
                if (driveService == null) {
                    Log.e("CloudStorageManager", "Google Drive provider requires an authenticated Drive service instance.")
                    return null
                }
                GoogleDriveProvider(context, driveService)
            }
            CloudService.DROPBOX -> {
                // This is the new code block
                if (dbxClient == null) {
                    Log.e("CloudStorageManager", "Dropbox provider requires an authenticated DbxClientV2 instance.")
                    return null
                }
                DropBoxProvider(context, dbxClient)
            }
            CloudService.BOX -> {
                if (boxApiFolder == null || boxApiFile == null) {
                    Log.e("CloudStorageManager", "Box provider requires BoxApiFolder and BoxApiFile instances.")
                    return null
                }
                BoxProvider(context, boxApiFolder, boxApiFile)
            }
            CloudService.ONE_DRIVE -> {
//                Log.e("CloudStorageManager", "OneDrive provider requires an authenticated GraphServiceClient instance.")
//                return null
                if (oneDriveToken == null) {
                    Log.e("CloudStorageManager", "OneDrive provider requires an access token.")
                    if (oneDriveGraphClient == null) {
                        Log.e("CloudStorageManager", "OneDrive provider requires an authenticated GraphServiceClient instance.")
                        return null
                    } else {
                        // Make sure to provide a valid cache directory from the context
                        val cacheDir = context.cacheDir
                        OneDriveProvider(oneDriveGraphClient, cacheDir)
                    }
                } else {
                    OneDriveHttpProvider(context, oneDriveToken)
                }
            }
            CloudService.PCLOUD -> {
                if (pCloudAuthToken == null) {
                    Log.e("CloudStorageManager", "pCloud provider requires an authentication token.")
                    if (pCloudApiClient == null) {
                        Log.e("CloudStorageManager", "pCloud provider requires an authenticated ApiClient instance.")
                        return null
                    } else {
                        PCloudProvider(context, pCloudApiClient)
                    }
                } else {
                    PCloudHttpProvider(context, pCloudAuthToken)
                }
            }
            CloudService.SYNC -> {
                if (syncApiToken == null) {
                    Log.e("CloudStorageManager", "Sync.com provider requires an API token.")
                    return null
                }
                SyncProvider(context, syncApiToken)
            }
            CloudService.CANTO -> {
                if (cantoAccessToken == null || cantoDomain == null) {
                    Log.e("CloudStorageManager", "Canto provider requires an access token and a domain.")
                    return null
                }
                CantoProvider(context, cantoAccessToken, cantoDomain)
            }
            CloudService.LOCAL_STORAGE -> {
                // Local storage provider doesn't need any special clients or tokens.
                LocalFileSystemProvider(context)
            }
            else -> {
                Log.e("CloudStorageManager", "No provider found.")
                return null
            }
        }
    }
}

// --- How to Use the New Structure (Conceptual) ---
/*
suspend fun useCloudStorage() {
    // 1. Authenticate with Google and get the Drive service instance
    val driveService: Drive? = getAuthenticatedDriveService()

    // 2. Get the specific provider from the manager
    val storageProvider = CloudStorageManager.getProvider(
        CloudService.GOOGLE_DRIVE,
        context,
        driveService
    )

    // 3. Use the interface methods
    storageProvider?.let { storage ->
        // First, ensure the folder structure is ready
        val isReady = storage.initializeCortexaStructure()
        
        if (isReady) {
            // Create a dummy local file to upload
            val localFile = java.io.File(context.cacheDir, "Calculus-Assignment1.pdf")
            localFile.writeText("Solutions for the first assignment.")

            // Save the file using the new app-specific method
            val fileId = storage.saveFileToCortexa(
                folderType = CortexaFolder.ASSIGNMENTS,
                specificSubFolderName = "Calculus 101",
                localFilePath = localFile.absolutePath
            )

            if (fileId != null) {
                Log.d("MyApp", "Assignment uploaded successfully with ID: $fileId")
                
                // Now download it
                storage.downloadFileFromCortexa(
                    folderType = CortexaFolder.ASSIGNMENTS,
                    specificSubFolderName = "Calculus 101",
                    fileName = "Calculus-Assignment1.pdf"
                )
            }
        }
    }
}
*/