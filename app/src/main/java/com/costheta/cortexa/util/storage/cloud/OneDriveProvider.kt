package com.costheta.cortexa.util.storage.cloud

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import com.costheta.cortexa.util.storage.CloudStorageProvider
import com.costheta.cortexa.util.storage.CortexaFolder
import com.microsoft.graph.models.DriveItem
import com.microsoft.graph.models.Folder
import com.microsoft.graph.serviceclient.GraphServiceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * An implementation of the CloudStorageProvider interface for Microsoft OneDrive.
 * This class uses the Microsoft GraphServiceClient to interact with the OneDrive API.
 *
 * NOTE: This implementation assumes the GraphServiceClient has a top-level `drives()`
 * method to access the collection of drives, which is standard for the SDK.
 *
 * @param graphServiceClient An authenticated instance of the GraphServiceClient.
 * @param localCacheDir The directory on the local device where downloaded files will be stored.
 */
class OneDriveProvider(
    private val graphServiceClient: GraphServiceClient,
    private val localCacheDir: File
) : CloudStorageProvider {

    private val rootFolderId = "root"
    private val cortexaDirName = "cortexa"

    // Cache for the user's default drive ID to avoid repeated lookups.
    private var defaultDriveId: String? = null

    /**
     * Retrieves the Drive ID for the current user's default drive.
     * Caches the ID after the first successful retrieval.
     */
    private suspend fun getDefaultDriveId(): String {
        if (defaultDriveId == null) {
            defaultDriveId = graphServiceClient.me().drive().get()?.id
                ?: throw IllegalStateException("Could not retrieve default drive for the user.")
        }
        return defaultDriveId!!
    }

    // --- App-Specific 'cortexa' Methods ---

    /**
     * Ensures the 'cortexa' folder and all its necessary subdirectories exist in the user's OneDrive root.
     * This method is idempotent; it will not recreate folders that already exist.
     */
    override suspend fun initializeCortexaStructure(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get or create the main "cortexa" directory in the root of the drive.
            val cortexaFolder = getOrCreateFolder(rootFolderId, cortexaDirName)
                ?: throw IllegalStateException("Could not create or find 'cortexa' directory.")

            // Sequentially ensure each required sub-folder exists within "cortexa".
            for (folder in CortexaFolder.entries) {
                getOrCreateFolder(cortexaFolder.id!!, folder.path)
                    ?: throw IllegalStateException("Failed to create subdirectory: ${folder.path}")
            }
            true
        } catch (e: Exception) {
            System.err.println("Error initializing Cortexa structure: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Saves a local file to a nested folder structure within the main 'cortexa' directory.
     * e.g., /cortexa/Assignments/Calculus/homework.pdf
     */
    override suspend fun saveFileToCortexa(
        folderType: CortexaFolder,
        specificSubFolderName: String,
        localFilePath: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Find the root 'cortexa' folder.
            val cortexaFolder = findChildByName(rootFolderId, cortexaDirName)
                ?: throw IllegalStateException("'cortexa' directory not found. Please initialize first.")

            // 2. Find the main category sub-folder (e.g., "Assignments").
            val categoryFolder = findChildByName(cortexaFolder.id!!, folderType.path)
                ?: throw IllegalStateException("Category folder '${folderType.path}' not found.")

            // 3. Get or create the specific sub-folder (e.g., "Calculus Homework").
            val targetFolder = getOrCreateFolder(categoryFolder.id!!, specificSubFolderName)
                ?: throw IllegalStateException("Could not create specific sub-folder '$specificSubFolderName'.")

            // 4. Save the file to the final destination folder.
            saveFile(targetFolder.id!!, localFilePath)
        } catch (e: Exception) {
            System.err.println("Error saving file to Cortexa path: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Downloads a file from a specific sub-folder within the 'cortexa' structure.
     */
    override suspend fun downloadFileFromCortexa(
        folderType: CortexaFolder,
        specificSubFolderName: String,
        fileName: String
    ): String? {
        // Construct the full remote path and delegate to the generic download method.
        val remotePath = "$cortexaDirName/${folderType.path}/$specificSubFolderName/$fileName"
        return downloadFile(remotePath)
    }


    // --- Generic Methods ---

    /**
     * Creates a new folder within a specified parent folder.
     */
    override suspend fun createFolder(parentFolderId: String, folderName: String): String? = withContext(Dispatchers.IO) {
        try {
            val driveId = getDefaultDriveId()
            val newFolder = DriveItem().apply {
                name = folderName
                folder = Folder() // Indicates that this DriveItem is a folder.
            }
            val createdItem = graphServiceClient.drives().byDriveId(driveId).items().byDriveItemId(parentFolderId).children().post(newFolder)
            createdItem?.id
        } catch (e: Exception) {
            System.err.println("Error creating folder '$folderName': ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a local file to a specified folder in the cloud using its ID.
     * This will overwrite the remote file if a file with the same name already exists.
     */
    override suspend fun saveFile(parentFolderId: String, localFilePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val driveId = getDefaultDriveId()
            val localFile = File(localFilePath)
            if (!localFile.exists()) {
                throw IllegalArgumentException("Local file does not exist at path: $localFilePath")
            }

            FileInputStream(localFile).use { stream ->
                // The Graph API for file upload uses a PUT request on the /content endpoint.
                // The path is constructed like: /drives/{driveId}/items/{parentId}/children/{fileName}/content
                // CORRECTED: Using byDriveItemId1 as per the provided ChildrenRequestBuilder class.
                // CORRECTED: Passing the InputStream directly to the put() method.
                val uploadedItem = graphServiceClient.drives().byDriveId(driveId).items().byDriveItemId(parentFolderId)
                    .children().byDriveItemId1(localFile.name).content().put(stream)
                uploadedItem?.id
            }
        } catch (e: Exception) {
            System.err.println("Error saving file '$localFilePath' to folder '$parentFolderId': ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a local file to a full path in the cloud, creating parent directories as needed.
     */
    override suspend fun saveFileByPath(fullRemotePath: String, localFilePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val pathSegments = fullRemotePath.split('/').filter { it.isNotEmpty() }
            if (pathSegments.isEmpty()) return@withContext null

            val fileName = pathSegments.last()
            val folderPathSegments = pathSegments.dropLast(1)

            var currentParentId = rootFolderId
            // Traverse the path, creating folders as necessary.
            for (segment in folderPathSegments) {
                val folderItem = getOrCreateFolder(currentParentId, segment)
                    ?: throw IllegalStateException("Failed to create or find directory: $segment")
                currentParentId = folderItem.id!!
            }

            // Save the file in the final parent directory.
            saveFile(currentParentId, localFilePath)
        } catch (e: Exception) {
            System.err.println("Error saving file by path '$fullRemotePath': ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Downloads a file from a full remote path to the local cache directory.
     */
    override suspend fun downloadFile(remoteFilePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val driveId = getDefaultDriveId()
            // Find the remote file by its path to get its ID.
            val fileItem = findItemByPath(remoteFilePath)
                ?: throw IllegalStateException("Remote file not found at path: $remoteFilePath")

            // Retrieve the file content as an InputStream.
            val contentStream = graphServiceClient.drives().byDriveId(driveId).items().byDriveItemId(fileItem.id!!).content().get()
                ?: throw IllegalStateException("Failed to get file content.")

            // Prepare the local destination file.
            val localFile = File(localCacheDir, File(remoteFilePath).name)
            localFile.parentFile?.mkdirs()

            // Write the downloaded stream to the local file.
            FileOutputStream(localFile).use { outputStream ->
                contentStream.copyTo(outputStream)
            }
            localFile.absolutePath
        } catch (e: Exception) {
            System.err.println("Error downloading file '$remoteFilePath': ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // --- Private Helper Methods ---

    /**
     * Finds a DriveItem (file or folder) by its name within a specific parent folder.
     * Uses the OData $filter query to match the name exactly.
     * @return The found DriveItem, or null if no item with that name exists.
     */
    private suspend fun findChildByName(parentFolderId: String, name: String): DriveItem? {
        val driveId = getDefaultDriveId()
        // The Graph API escapes single quotes in filter strings by doubling them up.
        val sanitizedName = name.replace("'", "''")
        val result = graphServiceClient.drives().byDriveId(driveId).items().byDriveItemId(parentFolderId).children()
            .get { requestConfiguration ->
                requestConfiguration.queryParameters.filter = "name eq '$sanitizedName'"
                requestConfiguration.queryParameters.top = 1 // We only need one result.
            }
        return result?.value?.firstOrNull()
    }

    /**
     * Retrieves a folder by name if it exists, otherwise creates it.
     * @return The DriveItem of the existing or newly created folder, or null on failure.
     */
    private suspend fun getOrCreateFolder(parentFolderId: String, folderName: String): DriveItem? {
        return findChildByName(parentFolderId, folderName) ?: run {
            val newFolderId = createFolder(parentFolderId, folderName)
            // Re-fetch the full item after creation to ensure we have the complete DriveItem object.
            newFolderId?.let { findChildByName(parentFolderId, folderName) }
        }
    }

    /**
     * Finds a file or folder by its full path starting from the drive's root.
     * @return The found DriveItem, or null if the path is invalid or the item doesn't exist.
     */
    private suspend fun findItemByPath(fullPath: String): DriveItem? {
        val driveId = getDefaultDriveId()
        val pathSegments = fullPath.split('/').filter { it.isNotEmpty() }
        if (pathSegments.isEmpty()) return null

        // Start the traversal from the root item of the drive.
        var currentItem: DriveItem? = graphServiceClient.drives().byDriveId(driveId).items().byDriveItemId(rootFolderId).get()
        for (segment in pathSegments) {
            currentItem = findChildByName(currentItem?.id!!, segment)
            if (currentItem == null) return null // Path segment not found, so the full path is invalid.
        }
        return currentItem
    }
}
