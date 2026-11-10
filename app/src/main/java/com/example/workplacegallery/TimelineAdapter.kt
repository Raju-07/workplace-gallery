package com.example.workplacegallery

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class TimelineItem {
    data class Header(val dateTitle: String, val count: Int) : TimelineItem()
    data class Photo(val uri: Uri, val rawIndex: Int) : TimelineItem()
}

class TimelineAdapter(
    private val context: Context,
    private val items: List<TimelineItem>,
    private val onPhotoClick: (rawIndex: Int) -> Unit,
    private val onSelectionChanged: (selectedCount: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    val selectedUris = mutableSetOf<Uri>()
    var isSelectionMode = false
        private set

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_PHOTO = 1
        private const val PAYLOAD_SELECTION = "PAYLOAD_SELECTION"
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is TimelineItem.Header -> TYPE_HEADER
            is TimelineItem.Photo -> TYPE_PHOTO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_gallery_header, parent, false))
        } else {
            PhotoViewHolder(inflater.inflate(R.layout.item_gallery_grid_photo, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION) && holder is PhotoViewHolder) {
            val item = items[position] as TimelineItem.Photo
            val isSelected = selectedUris.contains(item.uri)
            holder.scrim.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.checkmark.visibility = if (isSelected) View.VISIBLE else View.GONE
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is TimelineItem.Header -> {
                val h = holder as HeaderViewHolder
                h.title.text = item.dateTitle
                h.subtitle.text = "${item.count} items"
            }
            is TimelineItem.Photo -> {
                val p = holder as PhotoViewHolder
                val isSelected = selectedUris.contains(item.uri)
                p.scrim.visibility = if (isSelected) View.VISIBLE else View.GONE
                p.checkmark.visibility = if (isSelected) View.VISIBLE else View.GONE

                ThumbnailLoader.load(context, item.uri, p.imageView)

                p.itemView.setOnClickListener {
                    if (isSelectionMode) {
                        toggleSelection(position, item.uri)
                    } else {
                        onPhotoClick(item.rawIndex)
                    }
                }

                p.itemView.setOnLongClickListener {
                    if (!isSelectionMode) {
                        isSelectionMode = true
                    }
                    toggleSelection(position, item.uri)
                    true
                }
            }
        }
    }

    private fun toggleSelection(position: Int, uri: Uri) {
        if (selectedUris.contains(uri)) {
            selectedUris.remove(uri)
        } else {
            selectedUris.add(uri)
        }

        if (selectedUris.isEmpty()) {
            isSelectionMode = false
        }
        notifyItemChanged(position, PAYLOAD_SELECTION)
        onSelectionChanged(selectedUris.size)
    }

    fun selectAll(allUris: List<Uri>) {
        selectedUris.clear()
        selectedUris.addAll(allUris)
        isSelectionMode = true
        if (itemCount > 0) {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        }
        onSelectionChanged(selectedUris.size)
    }

    fun clearSelection() {
        if (!isSelectionMode && selectedUris.isEmpty()) return
        selectedUris.clear()
        isSelectionMode = false
        if (itemCount > 0) {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        }
        onSelectionChanged(0)
    }

    override fun getItemCount(): Int = items.size

    class HeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvHeaderTitle)
        val subtitle: TextView = v.findViewById(R.id.tvHeaderSubtitle)
    }

    class PhotoViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val imageView: ImageView = v.findViewById(R.id.ivGridThumb)
        val scrim: View = v.findViewById(R.id.selectionScrim)
        val checkmark: ImageView = v.findViewById(R.id.ivCheckmark)
    }
}
