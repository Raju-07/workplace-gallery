package com.example.workplacegallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class FullscreenPagerAdapter(
    private val context: Context,
    private val photos: List<GalleryItem.Photo>
) : RecyclerView.Adapter<FullscreenPagerAdapter.FullscreenViewHolder>() {

    private val decodePool = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    class FullscreenViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val zoomView: ZoomableImageView = view.findViewById(R.id.ivGalleryItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FullscreenViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_image, parent, false)
        return FullscreenViewHolder(view)
    }

    override fun onBindViewHolder(holder: FullscreenViewHolder, position: Int) {
        val item = photos[position]
        holder.zoomView.resetZoom()
        holder.zoomView.setImageDrawable(null)
        holder.zoomView.tag = item.uri

        decodePool.execute {
            val bitmap = decodeSampledBitmap(item.uri, 1080, 1920)
            mainHandler.post {
                if (holder.zoomView.tag == item.uri) {
                    holder.zoomView.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun decodeSampledBitmap(uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
            opts.inSampleSize = calculateInSampleSize(opts, reqW, reqH)
            opts.inJustDecodeBounds = false
            opts.inPreferredConfig = Bitmap.Config.RGB_565

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(opts: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        val h = opts.outHeight
        val w = opts.outWidth
        var inSample = 1
        if (h > reqH || w > reqW) {
            val halfH = h / 2
            val halfW = w / 2
            while ((halfH / inSample) >= reqH && (halfW / inSample) >= reqW) {
                inSample *= 2
            }
        }
        return inSample.coerceAtLeast(1)
    }

    override fun getItemCount(): Int = photos.size
}
