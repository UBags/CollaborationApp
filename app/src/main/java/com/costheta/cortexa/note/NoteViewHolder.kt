package com.costheta.cortexa.note

import android.text.Html
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.models.NotesItem
import com.costheta.cortexa.util.ui.CardColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val noteCardView: CardView = itemView.findViewById(R.id.noteCardView)
    private val noteSubjectTextView: TextView = itemView.findViewById(R.id.noteSubjectTextView)
    private val noteTypeTextView: TextView = itemView.findViewById(R.id.noteTypeTextView)
    private val noteDescriptionTextView: TextView = itemView.findViewById(R.id.noteDescriptionTextView)
    private val noteKeywordsTextView: TextView = itemView.findViewById(R.id.noteKeywordsTextView)
    private val noteCardVisibleView: TextView = itemView.findViewById(R.id.noteCardVisibleView)
    private val archiveNoteCheckBox: CheckBox = itemView.findViewById(R.id.archiveNoteCheckBox)
    private val noteLastModifiedTextView: TextView = itemView.findViewById(R.id.noteLastModifiedTextView)
    private val noteTimezoneTextView: TextView = itemView.findViewById(R.id.noteTimezoneTextView)
    private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteNoteButton)
    private val editButton: ImageButton = itemView.findViewById(R.id.editNoteButton)
    private val attachmentIconImageView: ImageView = itemView.findViewById(R.id.attachmentIconImageView)

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm dd-MMM-yyyy", Locale.ENGLISH)

    fun bind(
        noteItem: NotesItem,
        attachmentDao: AttachmentDao,
        coroutineScope: CoroutineScope,
        onDeleteClick: (NotesItem) -> Unit,
        onEditClick: (NotesItem) -> Unit,
        onArchiveClick: (NotesItem, Boolean) -> Unit
    ) {
        val (cardBackgroundColor, textColor) = CardColor.determineNotesCardColors(noteItem, itemView.context)
        val textColorHex = String.format("#%06X", 0xFFFFFF and textColor)

        noteCardView.setCardBackgroundColor(cardBackgroundColor)
        noteSubjectTextView.setTextColor(textColor)
        noteTypeTextView.setTextColor(textColor)
        noteDescriptionTextView.setTextColor(textColor)
        noteKeywordsTextView.setTextColor(textColor)
        archiveNoteCheckBox.setTextColor(textColor)
        noteLastModifiedTextView.setTextColor(textColor)
        noteCardVisibleView.setTextColor(textColor)
        noteTimezoneTextView.setTextColor(textColor)

        noteSubjectTextView.text = noteItem.subject?.ifEmpty { null } ?: itemView.context.getString(R.string.no_subject_text)

        noteTypeTextView.visibility = if (noteItem.type.isNullOrEmpty()) View.GONE else View.VISIBLE
        noteItem.type?.let {
            noteTypeTextView.text = Html.fromHtml("<b>Type</b>: $it", Html.FROM_HTML_MODE_LEGACY)
        }

        noteDescriptionTextView.visibility = if (noteItem.description.isNullOrEmpty()) View.GONE else View.VISIBLE
        noteItem.description?.let {
            noteDescriptionTextView.text = Html.fromHtml("<b>Description</b>: $it", Html.FROM_HTML_MODE_LEGACY)
        }

        noteKeywordsTextView.visibility = if (noteItem.keywords.isNullOrEmpty()) View.GONE else View.VISIBLE
        noteItem.keywords?.let {
            noteKeywordsTextView.text = Html.fromHtml("<b>Keywords</b>: $it", Html.FROM_HTML_MODE_LEGACY)
        }

        archiveNoteCheckBox.setOnCheckedChangeListener(null)
        archiveNoteCheckBox.isChecked = noteItem.archivedNote
        archiveNoteCheckBox.setOnCheckedChangeListener { _, isChecked -> onArchiveClick(noteItem, isChecked) }

        val systemZoneId = ZoneId.systemDefault()
        val lastModifiedDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(noteItem.lastModifiedMillis), systemZoneId)
        val lastModifiedText = "<b>Last modified on</b>: ${lastModifiedDateTime.format(dateTimeFormatter)}"

        noteCardVisibleView.visibility = View.GONE
        if (noteItem.archivedNote) {
            val now = LocalDateTime.now()
            val displayUntilTime = lastModifiedDateTime.plusMinutes(10)
            if(now.isBefore(displayUntilTime)) {
                noteCardVisibleView.visibility = View.VISIBLE
                val cardVisibleTillString = itemView.context.getString(R.string.card_visible_till, displayUntilTime.format(dateTimeFormatter))
                noteCardVisibleView.text = Html.fromHtml("<small>$cardVisibleTillString</small>", Html.FROM_HTML_MODE_LEGACY)
            }
        }

        noteLastModifiedTextView.text = Html.fromHtml(lastModifiedText, Html.FROM_HTML_MODE_LEGACY)
        noteTimezoneTextView.text = "(${systemZoneId.id})"

        deleteButton.setOnClickListener { onDeleteClick(noteItem) }
        editButton.setOnClickListener { onEditClick(noteItem) }

        noteItem.noteId?.let { id ->
            coroutineScope.launch {
                val attachmentCount = withContext(Dispatchers.IO) {
                    attachmentDao.getAttachmentCountForEvent("Note", id)
                }
                withContext(Dispatchers.Main) {
                    attachmentIconImageView.visibility = if (attachmentCount > 0) View.VISIBLE else View.GONE
                }
            }
        } ?: run {
            attachmentIconImageView.visibility = View.GONE
        }
    }
}
