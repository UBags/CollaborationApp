package com.costheta.cortexa.util.image2pdf.mlkit.old

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView // Import TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R // Import your app's R file

class ThumbnailAdapter(
    private val onItemClick: (Uri) -> Unit,
    private val onDeleteClick: (Uri) -> Unit
) : ListAdapter<Uri, ThumbnailAdapter.ThumbnailViewHolder>(ThumbnailDiffCallback()) {

    private val TAG = "ThumbnailAdapter"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.i2pdf_image_thumbnail_item, parent, false)
        return ThumbnailViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        val uri = getItem(position)
        // Pass the position to the bind method to display the page number
        Log.d(TAG, "Binding position $position with URI $uri, setting page number to Page ${position + 1}")
        holder.bind(uri, position + 1)
    }

    inner class ThumbnailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.thumbnail_image_view)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.delete_thumbnail_button)
        private val pageNumberTextView: TextView = itemView.findViewById(R.id.thumbnail_page_number_text_view) // Reference to the new TextView

        fun bind(uri: Uri, pageNumber: Int) {
            thumbnailImageView.setImageURI(uri)
            // Set the page number text
            pageNumberTextView.text = "Page ${pageNumber}"
            Log.d(TAG, "Bound ViewHolder at position $bindingAdapterPosition with page number $pageNumber and URI $uri")
            itemView.setOnClickListener { onItemClick(uri) }
            deleteButton.setOnClickListener { onDeleteClick(uri) }
        }
    }

    private class ThumbnailDiffCallback : DiffUtil.ItemCallback<Uri>() {
        private val TAG = "ThumbnailDiffCallback"

        override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            val result = oldItem == newItem
            Log.d(TAG, "areItemsTheSame: oldItem=$oldItem, newItem=$newItem, result=$result")
            return result // URIs are unique identifiers
        }

        override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            val result = oldItem.toString() == newItem.toString()
            Log.d(TAG, "areContentsTheSame: oldItem=$oldItem, newItem=$newItem, result=$result")
            return result
        }
    }
}
