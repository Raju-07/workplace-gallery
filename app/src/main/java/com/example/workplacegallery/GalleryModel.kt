package com.example.workplacegallery

import android.net.Uri

sealed class GalleryItem {
    data class DateHeader(val title: String, val count: Int) : GalleryItem()
    data class Photo(val uri: Uri, val timestamp: Long, val name: String) : GalleryItem()
}
