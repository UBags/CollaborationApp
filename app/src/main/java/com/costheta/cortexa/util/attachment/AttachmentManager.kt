package com.costheta.cortexa.util.attachment

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleCoroutineScope
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.ActionDao
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.db.dao.AssignmentDao
import com.costheta.cortexa.data.db.dao.EngagementDao
import com.costheta.cortexa.data.db.dao.ExamDao
import com.costheta.cortexa.data.db.dao.NotesDao
import com.costheta.cortexa.data.db.dao.TodoDao
import com.costheta.cortexa.data.models.Attachment
import com.costheta.cortexa.data.models.AttachmentType
import com.costheta.cortexa.util.audio.RecordAudioActivity
import com.costheta.cortexa.util.storage.CortexaFolder
import com.costheta.cortexa.util.storage.local.LocalFileSystemProvider
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import android.content.ClipData
import android.util.Patterns
import android.view.ViewGroup
import java.io.IOException

class AttachmentManager(
    private val activity: Activity,
    private val attachmentDao: AttachmentDao,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val callbacks: AttachmentManagerCallbacks,
    private val pickFileLauncher: ActivityResultLauncher<Intent>,
    private val takePhotoLauncher: ActivityResultLauncher<Uri>,
    private val takeVideoLauncher: ActivityResultLauncher<Uri>,
    private val recordAudioLauncher: ActivityResultLauncher<Intent>,
    private val requestCameraPermissionLauncher: ActivityResultLauncher<String>,
    private val requestMediaPermissionsLauncher: ActivityResultLauncher<Array<String>>
) {

    private var currentPhotoOrVideoUri: Uri? = null
    private val TAG = "AttachmentManager"

    private val assignmentDao: AssignmentDao = AppDatabase.getDatabase(activity).assignmentDao()
    private val examDao: ExamDao = AppDatabase.getDatabase(activity).examDao()
    private val todoDao: TodoDao = AppDatabase.getDatabase(activity).todoDao()
    private val notesDao: NotesDao = AppDatabase.getDatabase(activity).notesDao()
    private val engagementDao: EngagementDao = AppDatabase.getDatabase(activity).engagementDao()
    private val actionDao: ActionDao = AppDatabase.getDatabase(activity).actionDao()


    private var copyAttachmentDialog: AlertDialog? = null

    private val storageProvider = LocalFileSystemProvider(activity)

    private val shareActivityLauncher: ActivityResultLauncher<Intent> = (activity as androidx.activity.ComponentActivity).registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "Share activity finished.")
    }

    init {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                storageProvider.initializeCortexaStructure()
            }
        }
    }

    fun handlePickFileResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            data?.data?.let { originalUri ->
                lifecycleScope.launch {
                    val mimeType = activity.contentResolver.getType(originalUri)
                    val attachmentType = getAttachmentTypeFromMime(mimeType)
                    val defaultDisplayName = (getFileName(activity.contentResolver, originalUri) ?: originalUri.lastPathSegment ?: "Unknown File").removeFileExtension()

                    val tempCopiedUri = withContext(Dispatchers.IO) {
                        copyContentUriToCacheAndGetFileProviderUri(originalUri, defaultDisplayName)
                    }

                    if (tempCopiedUri != null) {
                        showDisplayNameDialog(tempCopiedUri, attachmentType, defaultDisplayName)
                    } else {
                        Log.e(TAG, "Failed to copy picked file to cache: $originalUri. Cannot add attachment.")
                        withContext(Dispatchers.Main) {
                            callbacks.showToast(R.string.attachment_string_failed_to_make_file_persistent_cannot_save, Toast.LENGTH_LONG)
                        }
                    }
                }
            }
        }
    }

    fun handleTakePhotoResult(success: Boolean) {
        if (success) {
            currentPhotoOrVideoUri?.let { uri ->
                val defaultDisplayName = (getFileName(activity.contentResolver, uri) ?: uri.lastPathSegment ?: "Photo").removeFileExtension()
                showDisplayNameDialog(uri, AttachmentType.IMAGE_LOCAL, defaultDisplayName)
                currentPhotoOrVideoUri = null
            }
        } else {
            currentPhotoOrVideoUri?.let { uri ->
                if (uri.scheme == "file" && uri.path != null) {
                    File(uri.path!!).delete()
                }
                currentPhotoOrVideoUri = null
            }
            callbacks.showToast(R.string.attachment_string_photo_capture_cancelled_failed, Toast.LENGTH_SHORT)
        }
    }

    fun handleTakeVideoResult(success: Boolean) {
        if (success) {
            currentPhotoOrVideoUri?.let { uri ->
                val defaultDisplayName = (getFileName(activity.contentResolver, uri) ?: uri.lastPathSegment ?: "Video").removeFileExtension()
                showDisplayNameDialog(uri, AttachmentType.VIDEO_LOCAL, defaultDisplayName)
                currentPhotoOrVideoUri = null
            }
        } else {
            currentPhotoOrVideoUri?.let { uri ->
                if (uri.scheme == "file" && uri.path != null) {
                    File(uri.path!!).delete()
                }
                currentPhotoOrVideoUri = null
            }
            callbacks.showToast(R.string.attachment_string_video_capture_cancelled_failed, Toast.LENGTH_SHORT)
        }
    }

    fun handleRecordAudioResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val audioUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(RecordAudioActivity.EXTRA_AUDIO_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra(RecordAudioActivity.EXTRA_AUDIO_URI)
            }
            val transcription = data?.getStringExtra(RecordAudioActivity.EXTRA_TRANSCRIPTION)
            if (audioUri != null) {
                Log.d(TAG, "Recorded Audio URI: $audioUri")
                Log.d(TAG, "Transcription: $transcription")
                val defaultDisplayName = activity.getString(R.string.component_audio_audio_note_default_name, ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")))
                showDisplayNameDialog(audioUri, AttachmentType.AUDIO_LOCAL, defaultDisplayName)
            } else {
                Log.e(TAG, "Audio recording result was null.")
                callbacks.showToast(R.string.attachment_string_audio_recording_failed_cancelled, Toast.LENGTH_SHORT)
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            callbacks.showToast(R.string.attachment_string_audio_recording_cancelled, Toast.LENGTH_SHORT)
        }
    }

    fun handleCameraPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            showImageVideoCaptureDialog()
        } else {
            callbacks.showToast(activity.getString(R.string.attachment_string_attachment_permission_denied_message), Toast.LENGTH_SHORT)
        }
    }

    fun handleMediaPermissionsResult(permissions: Map<String, Boolean>) {
        val granted = permissions.entries.all { it.value }
        if (granted) {
            startFilePicking()
        } else {
            callbacks.showToast(activity.getString(R.string.attachment_string_attachment_permission_denied_message), Toast.LENGTH_SHORT)
        }
    }

    private fun showDisplayNameDialog(uri: Uri, attachmentType: AttachmentType, defaultDisplayName: String) {
        val builder = AlertDialog.Builder(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.attachment_name_dialog, null)
        val editTextDisplayName = view.findViewById<TextInputEditText>(R.id.editTextAttachmentDisplayName)
        editTextDisplayName.setText(defaultDisplayName)
        editTextDisplayName.setHint(R.string.attachment_string_dialog_attachment_name_hint)

        builder.setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.attachment_string_dialog_attachment_name_negative) { dialog, _ ->
                if (uri.scheme == "file" && uri.path != null) {
                    try {
                        val tempFile = File(uri.path!!)
                        if (tempFile.exists() && tempFile.delete()) {
                            Log.d(TAG, "Deleted temporary file on dialog cancel: ${tempFile.absolutePath}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete temporary file on cancel: ${e.message}")
                    }
                }
                callbacks.showToast(R.string.attachment_string_attachment_add_cancelled, Toast.LENGTH_SHORT)
                dialog.cancel()
            }

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val finalDisplayName = editTextDisplayName.text.toString().trim()
            if (finalDisplayName.isEmpty()) {
                callbacks.showToast(R.string.attachment_string_attachment_name_cannot_be_empty, Toast.LENGTH_SHORT)
            } else {
                saveAttachment(uri, attachmentType, finalDisplayName)
                dialog.dismiss()
            }
        }
    }

    private fun saveAttachment(sourceUri: Uri, attachmentType: AttachmentType, displayName: String) {
        lifecycleScope.launch {
            try {
                val eventType = callbacks.getCurrentEventType()
                val eventRefId = callbacks.getCurrentEventRefId()

                // Define the destination path within the cortexa structure
                val cortexaRoot = activity.getString(R.string.cortexa_folder_name)
                val destinationPath = "$cortexaRoot/$eventType/$eventRefId"
                val destinationDir = File(activity.filesDir, destinationPath)
                if (!destinationDir.exists()) {
                    destinationDir.mkdirs()
                }

                // Get a clean file name for the destination
                val fileName = getFileName(activity.contentResolver, sourceUri) ?: sourceUri.lastPathSegment ?: "${System.currentTimeMillis()}_file"
                val destinationFile = File(destinationDir, fileName)

                // **THE FIX**: Use a ContentResolver to open an InputStream from the source URI
                // and copy its content to the final destination file.
                withContext(Dispatchers.IO) {
                    activity.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                        FileOutputStream(destinationFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    } ?: throw IOException("Failed to get input stream from URI: $sourceUri")

                    // Also, delete the temporary file from the cache after copying
                    if (sourceUri.scheme == "content" && sourceUri.authority == "${activity.applicationContext.packageName}.fileprovider") {
                        val tempFile = File(activity.cacheDir, sourceUri.path?.substringAfterLast('/') ?: "")
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }
                    }
                }

                // The URI stored in the database is the relative path within the app's files directory
                val relativePath = destinationFile.relativeTo(activity.filesDir).path
                Log.d(TAG, "File copied to: ${destinationFile.absolutePath}. Storing relative path: $relativePath")

                val attachment = Attachment(
                    eventType = eventType,
                    eventRefId = eventRefId,
                    attachmentType = attachmentType,
                    uriString = relativePath, // Store the relative path
                    displayName = displayName,
                    validatedItem = false
                )
                val insertedId = withContext(Dispatchers.IO) {
                    attachmentDao.insertAttachment(attachment)
                }
                Log.d(TAG, "Attachment saved with ID: $insertedId, URI stored: ${attachment.uriString}")

                val newAttachment = attachment.copy(attachmentId = insertedId)
                withContext(Dispatchers.Main) {
                    callbacks.onAttachmentAdded(newAttachment)
                }
                Log.d(TAG, "Attachment saved with ID: $insertedId, URI stored: ${attachment.uriString}")

                withContext(Dispatchers.Main) {
                    callbacks.showToast(R.string.attachment_string_attachment_added_success, Toast.LENGTH_SHORT)
                    callbacks.onAttachmentsUpdated()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving attachment: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callbacks.showToast(activity.getString(R.string.attachment_string_attachment_add_failure, e.localizedMessage), Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun getAttachmentTypeFromMime(mimeType: String?): AttachmentType {
        return when {
            mimeType == null -> AttachmentType.DOCUMENT_LOCAL
            mimeType.startsWith("image/") -> AttachmentType.IMAGE_LOCAL
            mimeType.startsWith("video/") -> AttachmentType.VIDEO_LOCAL
            mimeType.startsWith("audio/") -> AttachmentType.AUDIO_LOCAL
            else -> AttachmentType.DOCUMENT_LOCAL
        }
    }

    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String? {
        var name: String? = null
        try {
            if (uri.scheme == "content") {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            } else if (uri.scheme == "file") {
                name = uri.lastPathSegment
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getFileName for URI: $uri, ${e.message}")
            name = uri.lastPathSegment
        }
        return name
    }

    fun checkAndRequestMediaPermissionsForPicker() {
        startFilePicking()
    }

    private fun startFilePicking() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        callbacks.launchActivityForResult(Intent.createChooser(intent, activity.getString(R.string.attachment_string_select_a_file)), pickFileLauncher)
    }

    fun checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showImageVideoCaptureDialog()
        } else {
            callbacks.requestPermission(Manifest.permission.CAMERA, requestCameraPermissionLauncher)
        }
    }

    private fun showImageVideoCaptureDialog() {
        val options = arrayOf<CharSequence>(activity.getString(R.string.attachment_string_take_photo), activity.getString(R.string.attachment_string_record_video), activity.getString(R.string.cancel))
        val builder: AlertDialog.Builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.attachment_string_choose_action)
        builder.setItems(options) { dialog, item ->
            when (options[item]) {
                activity.getString(R.string.attachment_string_take_photo) -> launchCameraForImage()
                activity.getString(R.string.attachment_string_record_video) -> launchCameraForVideo()
                activity.getString(R.string.cancel) -> dialog.dismiss()
            }
        }
        builder.show()
    }

    private fun launchCameraForImage() {
        val photoFile: File? = try {
            createTemporaryMediaFile("IMG_", ".jpg")
        } catch (ex: Exception) {
            callbacks.showToast(activity.getString(R.string.attachment_string_error_creating_file, ex.message), Toast.LENGTH_LONG)
            null
        }
        photoFile?.also {
            currentPhotoOrVideoUri = FileProvider.getUriForFile(
                activity,
                "${activity.applicationContext.packageName}.fileprovider",
                it
            )
            callbacks.launchUriActivityForResult(currentPhotoOrVideoUri!!, takePhotoLauncher)
        }
    }

    private fun launchCameraForVideo() {
        val videoFile: File? = try {
            createTemporaryMediaFile("VID_", ".mp4")
        } catch (ex: Exception) {
            callbacks.showToast(activity.getString(R.string.attachment_string_error_creating_file, ex.message), Toast.LENGTH_LONG)
            null
        }
        videoFile?.also {
            currentPhotoOrVideoUri = FileProvider.getUriForFile(
                activity,
                "${activity.applicationContext.packageName}.fileprovider",
                it
            )
            callbacks.launchUriActivityForResult(currentPhotoOrVideoUri!!, takeVideoLauncher)
        }
    }

    private fun createTemporaryMediaFile(prefix: String, suffix: String): File {
        val timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = "${prefix}${timestamp}"
        val storageDir = activity.cacheDir
        return File.createTempFile(fileName, suffix, storageDir)
    }

    fun launchRecordAudioActivity() {
        val intent = Intent(activity, RecordAudioActivity::class.java)
        callbacks.launchActivityForResult(intent, recordAudioLauncher)
    }

    fun showAddHyperlinkDialog() {
        val builder = AlertDialog.Builder(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.attachment_add_hyperlink_dialog, null)
        val editTextUrl = view.findViewById<TextInputEditText>(R.id.editTextHyperlinkUrl)
        val editTextDisplayName = view.findViewById<TextInputEditText>(R.id.editTextDisplayName)

        builder.setView(view)
        builder.setPositiveButton(android.R.string.ok, null)
        builder.setNegativeButton(R.string.attachment_string_cancel_add_hyperlink) { dialog, _ ->
            dialog.cancel()
        }

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            var url = editTextUrl.text.toString().trim()
            val displayName = editTextDisplayName.text.toString().trim()

            if (!url.matches("^(http|https)://.*".toRegex())) {
                url = "https://$url"
            }

            if (!Patterns.WEB_URL.matcher(url).matches()) {
                callbacks.showToast(R.string.attachment_string_error_invalid_url, Toast.LENGTH_SHORT)
            } else {
                val attachment = Attachment(
                    eventType = callbacks.getCurrentEventType(),
                    eventRefId = callbacks.getCurrentEventRefId(),
                    attachmentType = AttachmentType.HYPERLINK,
                    uriString = url,
                    displayName = displayName.ifEmpty { url },
                    validatedItem = false
                )
                saveHyperlinkAttachment(attachment)
                dialog.dismiss()
            }
        }
    }

    private fun saveHyperlinkAttachment(attachment: Attachment) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val insertedId = attachmentDao.insertAttachment(attachment)
                    val newAttachment = attachment.copy(attachmentId = insertedId)
                    withContext(Dispatchers.Main) {
                        callbacks.onAttachmentAdded(newAttachment)
                        callbacks.showToast(R.string.attachment_string_attachment_added_success, Toast.LENGTH_SHORT)
                        callbacks.onAttachmentsUpdated()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving hyperlink attachment: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        callbacks.showToast(activity.getString(R.string.attachment_string_attachment_add_failure, e.localizedMessage), Toast.LENGTH_LONG)
                    }
                }
            }
        }
    }

    fun showDeleteAttachmentDialog(attachment: Attachment) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.attachment_string_attachment_deleted_dialog_title)
            .setMessage(R.string.attachment_string_attachment_deleted_dialog_message)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                deleteAttachment(attachment)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    fun deleteAttachment(attachment: Attachment) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    attachmentDao.deleteAttachment(attachment)

                    if (attachment.attachmentType == AttachmentType.HYPERLINK) {
                        withContext(Dispatchers.Main) {
                            callbacks.showToast(R.string.attachment_string_attachment_delete_success, Toast.LENGTH_SHORT)
                            callbacks.onAttachmentsUpdated()
                        }
                        return@withContext
                    }

                    val otherReferences = attachmentDao.getAttachmentsByUriString(attachment.uriString)
                    val file = File(activity.filesDir, attachment.uriString)

                    if (otherReferences.isEmpty()) {
                        if (file.exists() && file.delete()) {
                            Log.d(TAG, "Deleted local file as it was no longer referenced: ${file.absolutePath}")
                        } else {
                            Log.w(TAG, "Failed to delete unreferenced local file or file not found: ${file.absolutePath}")
                        }
                    } else {
                        Log.d(TAG, "File not deleted as it is still referenced by ${otherReferences.size} other events: ${file.absolutePath}")
                    }

                    val eventDir = file.parentFile
                    if (eventDir != null && eventDir.isDirectory && eventDir.listFiles()?.isEmpty() == true) {
                        if (eventDir.delete()) {
                            Log.d(TAG, "Deleted empty event directory: ${eventDir.absolutePath}")
                        }
                    }

                    withContext(Dispatchers.Main) {
                        callbacks.showToast(R.string.attachment_string_attachment_delete_success, Toast.LENGTH_SHORT)
                        callbacks.onAttachmentsUpdated()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during attachment deletion process", e)
                    withContext(Dispatchers.Main) {
                        callbacks.showToast(activity.getString(R.string.attachment_string_attachment_delete_failure, e.message), Toast.LENGTH_LONG)
                    }
                }
            }
        }
    }

    fun viewAttachment(attachment: Attachment) {
        try {
            if (attachment.attachmentType == AttachmentType.HYPERLINK) {
                val intent = Intent(Intent.ACTION_VIEW, attachment.uriString.toUri())
                activity.startActivity(intent)
                return
            }

            val file = File(activity.filesDir, attachment.uriString)
            if (!file.exists()) {
                Log.e(TAG, "File not found for viewing: ${file.absolutePath}. Cannot view.")
                callbacks.showToast(R.string.attachment_string_error_file_not_found, Toast.LENGTH_LONG)
                return
            }

            val uriToView = FileProvider.getUriForFile(
                activity,
                "${activity.applicationContext.packageName}.fileprovider",
                file
            )
            Log.d(TAG, "Generated content URI for viewing: $uriToView")

            val intent = Intent(Intent.ACTION_VIEW)
            val mimeType = activity.contentResolver.getType(uriToView) ?: "*/*"
            intent.setDataAndType(uriToView, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.attachment_string_view_with)))
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No app found to view attachment: ${e.message}", e)
            callbacks.showToast(R.string.attachment_string_error_no_viewer_found, Toast.LENGTH_LONG)
        } catch (e: Exception) {
            Log.e(TAG, "Error viewing attachment: ${e.message}", e)
            callbacks.showToast(activity.getString(R.string.attachment_string_error_general_viewing, e.localizedMessage), Toast.LENGTH_LONG)
        }
    }

    fun shareAttachments(attachments: List<Attachment>) {
        if (attachments.isEmpty()) {
            callbacks.showToast(R.string.attachment_string_no_attachments_to_share, Toast.LENGTH_SHORT)
            return
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                clearShareCache()
            }

            val fileAttachments = attachments.filter { it.attachmentType != AttachmentType.HYPERLINK }
            val hyperlinkAttachments = attachments.filter { it.attachmentType == AttachmentType.HYPERLINK }
            val preparedUris = ArrayList<Uri>()

            if (hyperlinkAttachments.isNotEmpty()) {
                val hyperlinkText = buildString {
                    append(activity.getString(R.string.attachment_string_shared_hyperlinks_header))
                    hyperlinkAttachments.forEach { hyperlink ->
                        append("\n- ${hyperlink.displayName}: ${hyperlink.uriString}")
                    }
                    append("\n\n")
                }

                val hyperlinkFileUri = withContext(Dispatchers.IO) {
                    createHyperlinkTextFile(hyperlinkText)
                }

                if (hyperlinkFileUri != null) {
                    preparedUris.add(hyperlinkFileUri)
                } else {
                    withContext(Dispatchers.Main) {
                        callbacks.showToast(R.string.attachment_string_error_creating_hyperlink_file, Toast.LENGTH_LONG)
                    }
                }
            }

            fileAttachments.forEach { attachment ->
                val absoluteFile = File(activity.filesDir, attachment.uriString)
                if (absoluteFile.exists()) {
                    val preparedUri = FileProvider.getUriForFile(
                        activity,
                        "${activity.applicationContext.packageName}.fileprovider",
                        absoluteFile
                    )
                    preparedUris.add(preparedUri)
                } else {
                    withContext(Dispatchers.Main) {
                        callbacks.showToast(activity.getString(R.string.attachment_string_could_not_attach_file_skipping, attachment.displayName), Toast.LENGTH_LONG)
                    }
                }
            }

            if (preparedUris.isEmpty()) {
                withContext(Dispatchers.Main) {
                    callbacks.showToast(R.string.attachment_string_no_attachments_to_share, Toast.LENGTH_SHORT)
                }
                return@launch
            }

            val shareIntent = Intent()
            when {
                preparedUris.size > 1 -> {
                    shareIntent.action = Intent.ACTION_SEND_MULTIPLE
                    shareIntent.type = "*/*"
                    shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, preparedUris)
                }
                preparedUris.size == 1 -> {
                    shareIntent.action = Intent.ACTION_SEND
                    val uri = preparedUris.first()
                    shareIntent.type = activity.contentResolver.getType(uri) ?: "*/*"
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                }
            }

            shareIntent.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.attachment_string_share_subject, callbacks.getCurrentEventType()))
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val clipData = ClipData("Attachments", arrayOf("*/*"), ClipData.Item(preparedUris.first()))
            for (i in 1 until preparedUris.size) {
                clipData.addItem(ClipData.Item(preparedUris[i]))
            }
            shareIntent.clipData = clipData

            try {
                val chooser = Intent.createChooser(shareIntent, activity.getString(R.string.attachment_string_share_attachments_chooser_title))
                shareActivityLauncher.launch(chooser)
            } catch (e: ActivityNotFoundException) {
                callbacks.showToast(R.string.attachment_string_no_sharing_app_found, Toast.LENGTH_SHORT)
            }
        }
    }

    private fun createHyperlinkTextFile(textContent: String): Uri? {
        return try {
            val timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val fileName = "Other Attachments - $timestamp.txt"

            val cacheSubDir = File(activity.cacheDir, "attachment_share_cache")
            cacheSubDir.mkdirs()
            val tempFile = File(cacheSubDir, fileName)

            tempFile.writeText(textContent)
            Log.d(TAG, "Created temporary hyperlink file: ${tempFile.absolutePath}")

            FileProvider.getUriForFile(
                activity,
                "${activity.applicationContext.packageName}.fileprovider",
                tempFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating hyperlink text file", e)
            null
        }
    }

    private fun clearShareCache() {
        try {
            val cacheDir = File(activity.cacheDir, "attachment_share_cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                Log.d(TAG, "Cleared attachment share cache.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing attachment share cache", e)
        }
    }

    private fun copyContentUriToCacheAndGetFileProviderUri(contentUri: Uri, displayName: String): Uri? {
        return try {
            val fileName = getFileName(activity.contentResolver, contentUri) ?: displayName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")

            val cacheSubDir = File(activity.cacheDir, "attachment_share_cache")
            cacheSubDir.mkdirs()
            val tempFile = File(cacheSubDir, "${System.currentTimeMillis()}_${fileName}")

            activity.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: run {
                Log.e(TAG, "Failed to open input stream for content URI: $contentUri. InputStream was null.")
                return null
            }
            FileProvider.getUriForFile(
                activity,
                "${activity.applicationContext.packageName}.fileprovider",
                tempFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error copying content URI to cache: $contentUri, ${e.message}", e)
            null
        }
    }

    private fun String.removeFileExtension(): String {
        val lastDotIndex = this.lastIndexOf('.')
        return if (lastDotIndex > 0 && lastDotIndex < this.length - 1) {
            this.substring(0, lastDotIndex)
        } else {
            this
        }
    }

    fun startCopyAttachmentsProcess() {
        lifecycleScope.launch {
            callbacks.onAttachmentCopyRequested()
            val eventTypes = withContext(Dispatchers.IO) {
                attachmentDao.getUniqueAttachmentEventTypes()
            }
            if (eventTypes.isEmpty()) {
                withContext(Dispatchers.Main) {
                    callbacks.showToast(R.string.attachment_string_no_other_events_to_copy_from, Toast.LENGTH_LONG)
                }
            } else {
                showCopyAttachmentsDialog(eventTypes)
            }
        }
    }

    private fun showCopyAttachmentsDialog(eventTypes: List<String>) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.attachment_copy_attachments_selection_dialog, null)
        val spinnerEventType: Spinner = dialogView.findViewById(R.id.spinnerEventType)
        val spinnerEvents: Spinner = dialogView.findViewById(R.id.spinnerEvents)
        val textViewSelectEventPrompt: TextView = dialogView.findViewById(R.id.textViewSelectEventPrompt)
        val textViewSelectAttachmentsPrompt: TextView = dialogView.findViewById(R.id.textViewSelectAttachmentsPrompt)
        val recyclerViewAttachments: androidx.recyclerview.widget.RecyclerView = dialogView.findViewById(R.id.recyclerViewAttachments)
        val noAttachmentsFoundTextView: TextView = dialogView.findViewById(R.id.noAttachmentsFoundTextView)
        val buttonCopyAttachments: Button = dialogView.findViewById(R.id.buttonCopyAttachments)

        val eventTypeAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, eventTypes)
        eventTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEventType.adapter = eventTypeAdapter

        val attachmentCopyAdapter = AttachmentCopyAdapter()
        recyclerViewAttachments.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
            adapter = attachmentCopyAdapter
        }

        textViewSelectEventPrompt.visibility = View.GONE
        spinnerEvents.visibility = View.GONE
        textViewSelectAttachmentsPrompt.visibility = View.GONE
        recyclerViewAttachments.visibility = View.GONE
        noAttachmentsFoundTextView.visibility = View.VISIBLE
        buttonCopyAttachments.visibility = View.GONE


        spinnerEventType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedType = eventTypes[position]
                textViewSelectEventPrompt.visibility = View.VISIBLE
                spinnerEvents.visibility = View.VISIBLE

                lifecycleScope.launch {
                    val eventsForType = withContext(Dispatchers.IO) {
                        getEventsForType(selectedType)
                    }
                    withContext(Dispatchers.Main) {
                        if (eventsForType.isEmpty()) {
                            val emptyEventAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, listOf(activity.getString(R.string.attachment_string_no_events_found)))
                            emptyEventAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            spinnerEvents.adapter = emptyEventAdapter
                            spinnerEvents.setSelection(0)
                            spinnerEvents.isEnabled = false

                            textViewSelectAttachmentsPrompt.visibility = View.GONE
                            recyclerViewAttachments.visibility = View.GONE
                            noAttachmentsFoundTextView.visibility = View.VISIBLE
                            buttonCopyAttachments.visibility = View.GONE
                            attachmentCopyAdapter.submitList(emptyList())
                            Log.d(TAG, "No events found for type: $selectedType")

                        } else {
                            spinnerEvents.isEnabled = true
                            val eventNames = eventsForType.map { it.second }
                            val eventNameToIdMap = eventsForType.associate { it.second to it.first }

                            val eventAdapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, eventNames) {
                                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                                    val view = super.getView(position, convertView, parent) as TextView
                                    view.ellipsize = TextUtils.TruncateAt.END
                                    view.isSingleLine = true
                                    return view
                                }

                                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                                    val view = super.getDropDownView(position, convertView, parent) as TextView
                                    view.ellipsize = TextUtils.TruncateAt.END
                                    view.isSingleLine = true
                                    return view
                                }
                            }

                            eventAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            spinnerEvents.adapter = eventAdapter

                            spinnerEvents.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                                    val selectedEventDisplayName = eventNames[position]
                                    val selectedEventRefId = eventNameToIdMap[selectedEventDisplayName]
                                    if (selectedEventRefId != null) {
                                        lifecycleScope.launch {
                                            val attachments = withContext(Dispatchers.IO) {
                                                attachmentDao.getAttachmentsForEvent(selectedType, selectedEventRefId).firstOrNull() ?: emptyList()
                                            }
                                            attachmentCopyAdapter.submitList(attachments)
                                            textViewSelectAttachmentsPrompt.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
                                            recyclerViewAttachments.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
                                            noAttachmentsFoundTextView.visibility = if (attachments.isEmpty()) View.VISIBLE else View.GONE
                                            buttonCopyAttachments.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
                                            Log.d(TAG, "Loaded ${attachments.size} attachments for event: $selectedEventDisplayName (ID: $selectedEventRefId)")
                                        }
                                    } else {
                                        attachmentCopyAdapter.submitList(emptyList())
                                        textViewSelectAttachmentsPrompt.visibility = View.GONE
                                        recyclerViewAttachments.visibility = View.GONE
                                        noAttachmentsFoundTextView.visibility = View.VISIBLE
                                        buttonCopyAttachments.visibility = View.GONE
                                        Log.w(TAG, "Selected eventRefId is null for: $selectedEventDisplayName")
                                    }
                                }

                                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                                    textViewSelectAttachmentsPrompt.visibility = View.GONE
                                    recyclerViewAttachments.visibility = View.GONE
                                    noAttachmentsFoundTextView.visibility = View.VISIBLE
                                    buttonCopyAttachments.visibility = View.GONE
                                    attachmentCopyAdapter.submitList(emptyList())
                                }
                            }
                            spinnerEvents.setSelection(0, true)
                        }
                    }
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                textViewSelectEventPrompt.visibility = View.GONE
                spinnerEvents.visibility = View.GONE
                textViewSelectAttachmentsPrompt.visibility = View.GONE
                recyclerViewAttachments.visibility = View.GONE
                noAttachmentsFoundTextView.visibility = View.VISIBLE
                buttonCopyAttachments.visibility = View.GONE
                attachmentCopyAdapter.submitList(emptyList())
            }
        }

        buttonCopyAttachments.setOnClickListener {
            val selectedAttachments = attachmentCopyAdapter.getSelectedAttachments()
            copySelectedAttachments(selectedAttachments)
            copyAttachmentDialog?.dismiss()
        }

        copyAttachmentDialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setNegativeButton(R.string.dialog_cancel_button) { dialog, _ -> dialog.dismiss() }
            .create()
        copyAttachmentDialog?.show()
    }

    private fun copySelectedAttachments(attachmentsToCopy: List<Attachment>) {
        if (attachmentsToCopy.isEmpty()) {
            callbacks.showToast(activity.getString(R.string.attachment_string_no_attachments_selected), Toast.LENGTH_SHORT)
            return
        }

        lifecycleScope.launch {
            val currentEventType = callbacks.getCurrentEventType()
            val currentEventRefId = callbacks.getCurrentEventRefId()
            var copiedCount = 0

            withContext(Dispatchers.IO) {
                val cortexaRoot = activity.getString(R.string.cortexa_folder_name)
                val commonDir = File(activity.filesDir, "$cortexaRoot/${CortexaFolder.COMMON.path}")
                if (!commonDir.exists()) commonDir.mkdirs()

                attachmentsToCopy.forEach { attachmentToCopy ->
                    if (attachmentToCopy.attachmentType == AttachmentType.HYPERLINK) {
                        val newHyperlink = attachmentToCopy.copy(
                            attachmentId = 0L,
                            eventType = currentEventType,
                            eventRefId = currentEventRefId,
                            validatedItem = false
                        )
                        attachmentDao.insertAttachment(newHyperlink)
                        copiedCount++
                        return@forEach
                    }

                    val sourceFile = File(activity.filesDir, attachmentToCopy.uriString)
                    if (!sourceFile.exists()) {
                        Log.w(TAG, "Source file for copy not found, skipping: ${sourceFile.absolutePath}")
                        return@forEach
                    }

                    val isAlreadyCommon = attachmentToCopy.uriString.contains("/${CortexaFolder.COMMON.path}/")

                    if (isAlreadyCommon) {
                        val newAttachmentRecord = attachmentToCopy.copy(
                            attachmentId = 0L,
                            eventType = currentEventType,
                            eventRefId = currentEventRefId,
                            validatedItem = false
                        )
                        attachmentDao.insertAttachment(newAttachmentRecord)
                        copiedCount++
                    } else {
                        val destinationFile = File(commonDir, sourceFile.name)
                        sourceFile.copyTo(destinationFile, overwrite = true)
                        sourceFile.delete()

                        val newCommonRelativePath = destinationFile.relativeTo(activity.filesDir).path

                        val oldRelativePath = attachmentToCopy.uriString
                        attachmentDao.updateUriStringPath(oldRelativePath, newCommonRelativePath)
                        Log.d(TAG, "Moved file to common and updated existing DB records from '$oldRelativePath' to '$newCommonRelativePath'")

                        val newAttachmentRecord = attachmentToCopy.copy(
                            attachmentId = 0L,
                            eventType = currentEventType,
                            eventRefId = currentEventRefId,
                            uriString = newCommonRelativePath,
                            validatedItem = false
                        )
                        attachmentDao.insertAttachment(newAttachmentRecord)
                        copiedCount++
                    }
                }
            }

            withContext(Dispatchers.Main) {
                callbacks.showToast(activity.getString(R.string.attachment_string_attachments_copied_success, copiedCount), Toast.LENGTH_SHORT)
                callbacks.onAttachmentsUpdated()
            }
        }
    }

    /**
     * MODIFIED: This function now formats the event display names according to the user's specific requirements.
     * It fetches the corresponding event object and constructs a string from its properties.
     */
    private suspend fun getEventsForType(eventType: String): List<Pair<Long, String>> {
        return try {
            withContext(Dispatchers.IO) {
                val attachmentsForType = attachmentDao.getValidatedAttachmentsForEventType(eventType)

                attachmentsForType.distinctBy { it.eventRefId }
                    .mapNotNull { attachment ->
                        val eventRefId = attachment.eventRefId
                        val eventName = when (eventType) {
                            "Assignment" -> {
                                val assignment = assignmentDao.getAssignmentById(eventRefId)
                                // Format: <courseName>-<assignmentTopic>-<assignmentName>
                                assignment?.let { "${it.courseName} - ${it.assignmentTopic} - ${it.assignmentName}" }
                            }
                            "Action" -> {
                                val action = actionDao.getActionById(eventRefId)
                                // Format: <actionName>-<venue>-<actionDescription> (filters out blank parts)
                                action?.let {
                                    listOfNotNull(it.actionName, it.venue, it.description)
                                        .filter { part -> part.isNotBlank() }
                                        .joinToString(" - ")
                                }
                            }
                            "Engagement" -> {
                                val engagement = engagementDao.getEngagementById(eventRefId)
                                // Format: <engagementName>-<venue>-<notes> (filters out blank parts)
                                engagement?.let {
                                    listOfNotNull(it.engagementName, it.venue, it.notes)
                                        .filter { part -> part.isNotBlank() }
                                        .joinToString(" - ")
                                }
                            }
                            "Exam" -> {
                                val exam = examDao.getExamById(eventRefId)
                                // Format: <courseName>-<examDetails>-<examDate> (uses courseName as examName)
                                exam?.let {
                                    listOfNotNull(it.courseName, it.examDetails, it.examDate.toString())
                                        .filter { part -> part.isNotBlank() }
                                        .joinToString(" - ")
                                }
                            }
                            "Notes" -> {
                                val note = notesDao.getNoteById(eventRefId)
                                // Format: <subject>-<type>-<description> (filters out blank parts)
                                note?.let {
                                    listOfNotNull(it.subject, it.type, it.description)
                                        .filter { part -> part.isNotBlank() }
                                        .joinToString(" - ")
                                } ?: activity.getString(R.string.untitled_note)
                            }
                            "Todo" -> {
                                val todo = todoDao.getTodoById(eventRefId)
                                // Format: <title>-<description> (filters out blank parts)
                                todo?.let {
                                    listOfNotNull(it.title, it.description)
                                        .filter { part -> part.isNotBlank() }
                                        .joinToString(" - ")
                                }
                            }
                            else -> null
                        }
                        // Only return if an event name could be constructed
                        eventName?.takeIf { it.isNotBlank() }?.let { eventRefId to it }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching events for type $eventType: ${e.message}", e)
            emptyList()
        }
    }


    fun onDestroy() {
        copyAttachmentDialog?.dismiss()
        copyAttachmentDialog = null
    }
}
