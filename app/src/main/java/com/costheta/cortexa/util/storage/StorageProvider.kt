/*
 * =====================================================================================
 * FILE: StorageProvider.kt
 * DESCRIPTION: Defines the common interface for all cloud storage services.
 * =====================================================================================
 */
package com.costheta.cortexa.util.storage

/**
 * Enum representing the primary sub-folders within the main 'cortexa' app directory.
 */
enum class CortexaFolder(val path: String) {
    ASSIGNMENTS("Assignments"),
    NOTES("Notes"),
    TODOS("Todos"),
    ENGAGEMENTS("Engagements"),
    ACTIONS("Actions"),
    EXAMS("Exams"),
    BACKUP("Backup"),
    COMMON("common")
}

/**
 * Interface defining the standard contract for a cloud storage service.
 * It includes both generic methods and app-specific methods for the 'cortexa' structure.
 */
interface CloudStorageProvider {
    // --- App-Specific 'cortexa' Methods ---

    /**
     * Ensures the entire 'cortexa' folder structure exists in the cloud.
     * Creates 'cortexa' and all its primary sub-folders if they don't exist.
     * @return Boolean true if the structure exists or was created successfully, false otherwise.
     */
    suspend fun initializeCortexaStructure(): Boolean

    /**
     * Saves a local file to a specific sub-folder within the 'cortexa' structure.
     * For example, saves to 'cortexa/Assignments/Calculus Homework/'.
     * @param folderType The main category folder (e.g., CortexaFolder.ASSIGNMENTS).
     * @param specificSubFolderName The name of the folder within the category (e.g., "Calculus Homework").
     * @param localFilePath The absolute path to the local file on the device.
     * @return The ID of the uploaded file, or null on failure.
     */
    suspend fun saveFileToCortexa(folderType: CortexaFolder, specificSubFolderName: String, localFilePath: String): String?

    /**
     * Downloads a file from a specific sub-folder within the 'cortexa' structure.
     * @param folderType The main category folder.
     * @param specificSubFolderName The name of the folder within the category.
     * @param fileName The name of the file to download.
     * @return The local path of the downloaded file, or null on failure.
     */
    suspend fun downloadFileFromCortexa(folderType: CortexaFolder, specificSubFolderName: String, fileName: String): String?


    // --- Generic Methods ---

    /**
     * Creates a folder in the cloud within a specified parent folder.
     * @param parentFolderId The ID of the parent folder. Can be "root" for the main directory.
     * @param folderName The name of the new folder.
     * @return The ID of the newly created folder, or null on failure.
     */
    suspend fun createFolder(parentFolderId: String, folderName: String): String?

    /**
     * Saves a local file to a specified folder in the cloud using its ID.
     * @param parentFolderId The ID of the cloud folder where the file will be saved.
     * @param localFilePath The absolute path to the local file on the device.
     * @return The ID of the uploaded file, or null on failure.
     */
    suspend fun saveFile(parentFolderId: String, localFilePath: String): String?

    /**
     * Saves a local file to a full path in the cloud, creating parent directories as needed.
     * @param fullRemotePath The full destination path in the cloud (e.g., "documents/reports/report.pdf").
     * @param localFilePath The absolute path to the local file on the device.
     * @return The ID of the uploaded file, or null on failure.
     */
    suspend fun saveFileByPath(fullRemotePath: String, localFilePath: String): String?

    /**
     * Downloads a file from the cloud to the local 'cortexa' directory, preserving the folder structure.
     * @param remoteFilePath The full path of the file in the cloud.
     * @return The local path of the downloaded file, or null on failure.
     */
    suspend fun downloadFile(remoteFilePath: String): String?
}