package com.example.workplacegallery

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.example.workplacegallery.databinding.ActivityGalleryBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var prefs: SharedPreferences
    private val rawPhotoUris = mutableListOf<Uri>()
    private val timelineItems = mutableListOf<TimelineItem>()
    private var timelineAdapter: TimelineAdapter? = null
    private var detailAdapter: GalleryAdapter? = null

    private var currentFilter = "ALL"
    private var currentSpanCount = 3
    private lateinit var gridManager: GridLayoutManager
    private lateinit var scaleDetector: ScaleGestureDetector

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) loadWorkplaceTimeline()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("workplace_gallery_prefs", Context.MODE_PRIVATE)

        checkStorageManagerPermission()
        setupTimelineRecycler()
        setupPinchToResize()
        setupSelectionActions()
        setupFullscreenViewer()
        loadWorkplaceTimeline()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.fullscreenContainer.visibility == View.VISIBLE) {
                    closeFullscreenDetail()
                } else if (timelineAdapter?.isSelectionMode == true) {
                    exitSelectionMode()
                } else {
                    finish()
                }
            }
        })
    }

    private fun checkStorageManagerPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun setupTimelineRecycler() {
        gridManager = GridLayoutManager(this, currentSpanCount)
        gridManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (binding.rvTimeline.adapter?.getItemViewType(position) == TimelineAdapter.TYPE_HEADER) currentSpanCount else 1
            }
        }
        binding.rvTimeline.layoutManager = gridManager
    }

    private fun setupPinchToResize() {
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                val scaleFactor = detector.scaleFactor
                if (scaleFactor > 1.25f && currentSpanCount > 2) {
                    currentSpanCount--
                    updateGridSpan()
                } else if (scaleFactor < 0.8f && currentSpanCount < 5) {
                    currentSpanCount++
                    updateGridSpan()
                }
            }
        })

        binding.rvTimeline.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            false
        }
    }

    private fun updateGridSpan() {
        gridManager.spanCount = currentSpanCount
        timelineAdapter?.notifyItemRangeChanged(0, timelineAdapter?.itemCount ?: 0)
    }

    private fun getWorkDir(): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WorkPlace")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getTrashDir(): File {
        val trash = File(getWorkDir(), ".trash")
        if (!trash.exists()) trash.mkdirs()
        return trash
    }

    private fun getFavorites(): MutableSet<String> {
        return prefs.getStringSet("favorites", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    }

    private fun toggleFavorite(path: String): Boolean {
        val favs = getFavorites()
        val isFav = favs.contains(path)
        if (isFav) favs.remove(path) else favs.add(path)
        prefs.edit().putStringSet("favorites", favs).apply()
        return !isFav
    }

    private fun loadWorkplaceTimeline() {
        rawPhotoUris.clear()
        timelineItems.clear()

        val targetDir = if (currentFilter == "TRASH") getTrashDir() else getWorkDir()
        val allFiles = targetDir.listFiles { f ->
            f.isFile && (f.extension.equals("jpg", true) || f.extension.equals("jpeg", true) || f.extension.equals("png", true))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        val favoritesSet = getFavorites()

        val filteredFiles = when {
            currentFilter == "FAVORITES" -> allFiles.filter { favoritesSet.contains(it.absolutePath) }
            currentFilter.startsWith("TAG:") -> {
                val tag = currentFilter.removePrefix("TAG:")
                allFiles.filter { it.name.contains("[$tag]") || it.name.startsWith("${tag}_") }
            }
            else -> allFiles
        }

        binding.tvTotalCount.text = "${filteredFiles.size} photos"

        val dateGroups = LinkedHashMap<String, MutableList<Pair<Uri, Int>>>()
        val dayFormatter = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())

        filteredFiles.forEachIndexed { index, file ->
            val uri = Uri.fromFile(file)
            rawPhotoUris.add(uri)
            val dayKey = dayFormatter.format(Date(file.lastModified()))
            dateGroups.getOrPut(dayKey) { mutableListOf() }.add(Pair(uri, index))
        }

        for ((day, photoList) in dateGroups) {
            timelineItems.add(TimelineItem.Header(day, photoList.size))
            for ((uri, rawIndex) in photoList) {
                timelineItems.add(TimelineItem.Photo(uri, rawIndex))
            }
        }

        timelineAdapter = TimelineAdapter(
            context = this,
            items = timelineItems,
            onPhotoClick = { clickedRawIndex -> openFullscreenDetail(clickedRawIndex) },
            onSelectionChanged = { count -> updateSelectionUI(count) }
        )
        binding.rvTimeline.adapter = timelineAdapter

        buildFilterChips()
    }

    private fun buildFilterChips() {
        binding.chipGroupFilters.removeAllViews()

        val workFiles = getWorkDir().listFiles { f ->
            f.isFile && (f.extension.equals("jpg", true) || f.extension.equals("jpeg", true) || f.extension.equals("png", true))
        } ?: emptyArray()

        val tags = mutableSetOf<String>()
        val tagRegex = Regex("\\[(.*?)\\]")
        workFiles.forEach { file ->
            tagRegex.find(file.name)?.let { match ->
                tags.add(match.groupValues[1])
            }
        }

        addChip("All", currentFilter == "ALL") { currentFilter = "ALL"; loadWorkplaceTimeline() }
        addChip("⭐ Favorites", currentFilter == "FAVORITES") { currentFilter = "FAVORITES"; loadWorkplaceTimeline() }

        tags.sorted().forEach { tag ->
            val key = "TAG:$tag"
            addChip(tag, currentFilter == key) { currentFilter = key; loadWorkplaceTimeline() }
        }

        addChip("🗑 Trash", currentFilter == "TRASH") { currentFilter = "TRASH"; loadWorkplaceTimeline() }
    }

    private fun addChip(label: String, isChecked: Boolean, onClick: () -> Unit) {
        val chip = Chip(this).apply {
            text = label
            isCheckable = true
            this.isChecked = isChecked
            setTextColor(ContextCompat.getColorStateList(this@MainActivity, android.R.color.white))
            setChipBackgroundColorResource(android.R.color.transparent)
            chipStrokeWidth = 2f
            setChipStrokeColorResource(if (isChecked) android.R.color.holo_green_light else android.R.color.darker_gray)
            setOnClickListener { onClick() }
        }
        binding.chipGroupFilters.addView(chip)
    }

    private fun setupSelectionActions() {
        binding.btnCloseSelection.setOnClickListener { exitSelectionMode() }
        binding.btnSelectAll.setOnClickListener { timelineAdapter?.selectAll(rawPhotoUris) }
        binding.btnBatchShare.setOnClickListener { shareSelectedPhotos() }
        binding.btnBatchDelete.setOnClickListener { confirmBatchDelete() }
    }

    private fun updateSelectionUI(count: Int) {
        if (count > 0) {
            binding.normalTopBar.visibility = View.GONE
            binding.selectionTopBar.visibility = View.VISIBLE
            binding.tvSelectionCount.text = "$count selected"
            binding.btnBatchDelete.visibility = View.VISIBLE
        } else {
            exitSelectionMode()
        }
    }

    // Guarded against recursive re-entry
    private fun exitSelectionMode() {
        if (binding.selectionTopBar.visibility != View.VISIBLE && timelineAdapter?.isSelectionMode != true) {
            return
        }
        binding.selectionTopBar.visibility = View.GONE
        binding.normalTopBar.visibility = View.VISIBLE
        timelineAdapter?.clearSelection()
    }

    private fun shareSelectedPhotos() {
        val selected = timelineAdapter?.selectedUris?.toList() ?: return
        if (selected.isEmpty()) return

        try {
            val contentUris = ArrayList<Uri>()
            for (uri in selected) {
                val file = File(uri.path ?: "")
                contentUris.add(FileProvider.getUriForFile(this, "${packageName}.provider", file))
            }

            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, contentUris)
                clipData = ClipData.newRawUri("Work Photos", contentUris[0])
                for (i in 1 until contentUris.size) {
                    clipData?.addItem(ClipData.Item(contentUris[i]))
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share ${selected.size} Photos"))
            exitSelectionMode()
        } catch (e: Exception) {
            Toast.makeText(this, "Batch share failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmBatchDelete() {
        val selected = timelineAdapter?.selectedUris?.toList() ?: return
        if (selected.isEmpty()) return

        val isTrashView = currentFilter == "TRASH"
        val title = if (isTrashView) "Permanently Delete ${selected.size} items?" else "Move ${selected.size} items to Trash?"
        val btnLabel = if (isTrashView) "Delete Permanently" else "Move to Trash"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(if (isTrashView) "These files will be permanently erased." else "Files will be stored in .trash/ and can be restored anytime.")
            .setPositiveButton(btnLabel) { _, _ ->
                var processed = 0
                val trashDir = getTrashDir()

                for (uri in selected) {
                    val file = File(uri.path ?: "")
                    if (isTrashView) {
                        val path = file.absolutePath
                        if (file.exists() && file.delete()) {
                            processed++
                            MediaScannerConnection.scanFile(applicationContext, arrayOf(path), null, null)
                        }
                    } else {
                        val dest = File(trashDir, file.name)
                        if (file.exists() && file.renameTo(dest)) processed++
                    }
                }
                Toast.makeText(this, "$btnLabel: $processed photos", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                ThumbnailLoader.clear()
                loadWorkplaceTimeline()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun closeFullscreenDetail() {
        binding.viewPagerDetail.adapter = null
        binding.fullscreenContainer.visibility = View.GONE
    }

    private fun setupFullscreenViewer() {
        binding.btnDetailBack.setOnClickListener {
            closeFullscreenDetail()
        }

        binding.viewPagerDetail.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.tvDetailTitle.text = "${position + 1} of ${rawPhotoUris.size}"
                syncDetailFavoriteStar(position)
            }
        })

        binding.btnDetailFavorite.setOnClickListener {
            val pos = binding.viewPagerDetail.currentItem
            if (pos in rawPhotoUris.indices) {
                val path = rawPhotoUris[pos].path ?: return@setOnClickListener
                val isFav = toggleFavorite(path)
                binding.btnDetailFavorite.setImageResource(
                    if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_border
                )
            }
        }

        binding.btnDetailInfo.setOnClickListener {
            val pos = binding.viewPagerDetail.currentItem
            if (pos in rawPhotoUris.indices) {
                showExifMetadataBottomSheet(rawPhotoUris[pos])
            }
        }

        binding.btnDetailRestore.setOnClickListener {
            val pos = binding.viewPagerDetail.currentItem
            if (pos in rawPhotoUris.indices) {
                val file = File(rawPhotoUris[pos].path ?: "")
                closeFullscreenDetail()

                val dest = File(getWorkDir(), file.name)
                if (file.renameTo(dest)) {
                    Toast.makeText(this, "Restored to WorkPlace", Toast.LENGTH_SHORT).show()
                    ThumbnailLoader.clear()
                    loadWorkplaceTimeline()
                }
            }
        }

        binding.btnDetailShare.setOnClickListener {
            val pos = binding.viewPagerDetail.currentItem
            if (pos in rawPhotoUris.indices) {
                try {
                    val file = File(rawPhotoUris[pos].path ?: "")
                    val shareUri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        clipData = ClipData.newRawUri("Work Photo", shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Share"))
                } catch (e: Exception) {
                    Toast.makeText(this, "Share failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnDetailDelete.setOnClickListener {
            val pos = binding.viewPagerDetail.currentItem
            if (pos in rawPhotoUris.indices) {
                val file = File(rawPhotoUris[pos].path ?: "")
                val isTrash = currentFilter == "TRASH"

                // Detach adapter before mutating lists to eliminate ViewPager2 IndexOutOfBounds crash
                closeFullscreenDetail()

                if (isTrash) {
                    val path = file.absolutePath
                    if (file.delete()) {
                        MediaScannerConnection.scanFile(applicationContext, arrayOf(path), null, null)
                        Toast.makeText(this, "Permanently deleted", Toast.LENGTH_SHORT).show()
                        ThumbnailLoader.clear()
                        loadWorkplaceTimeline()
                    }
                } else {
                    val dest = File(getTrashDir(), file.name)
                    if (file.renameTo(dest)) {
                        Toast.makeText(this, "Moved to Trash", Toast.LENGTH_SHORT).show()
                        ThumbnailLoader.clear()
                        loadWorkplaceTimeline()
                    }
                }
            }
        }
    }

    private fun syncDetailFavoriteStar(position: Int) {
        if (position in rawPhotoUris.indices) {
            val path = rawPhotoUris[position].path ?: return
            val isFav = getFavorites().contains(path)
            binding.btnDetailFavorite.setImageResource(
                if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_border
            )
        }
    }

    private fun openFullscreenDetail(initialIndex: Int) {
        detailAdapter = GalleryAdapter(this, rawPhotoUris)
        binding.viewPagerDetail.adapter = detailAdapter
        binding.viewPagerDetail.setCurrentItem(initialIndex, false)
        binding.tvDetailTitle.text = "${initialIndex + 1} of ${rawPhotoUris.size}"

        val isTrash = currentFilter == "TRASH"
        binding.btnDetailRestore.visibility = if (isTrash) View.VISIBLE else View.GONE
        binding.btnDetailShare.visibility = if (isTrash) View.GONE else View.VISIBLE
        binding.btnDetailFavorite.visibility = if (isTrash) View.GONE else View.VISIBLE

        syncDetailFavoriteStar(initialIndex)
        binding.fullscreenContainer.visibility = View.VISIBLE
    }

    private fun showExifMetadataBottomSheet(uri: Uri) {
        val file = File(uri.path ?: "")
        if (!file.exists()) return

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.item_gallery_header, null)
        val title = view.findViewById<TextView>(R.id.tvHeaderTitle)
        val subtitle = view.findViewById<TextView>(R.id.tvHeaderSubtitle)

        title.text = "Photo Details"

        var details = "File Name: ${file.name}\n" +
                "Size: ${String.format(Locale.US, "%.2f MB", file.length() / (1024f * 1024f))}\n" +
                "Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))}\n" +
                "Path: ${file.parent ?: ""}\n"

        try {
            val exif = ExifInterface(file.absolutePath)
            val w = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH) ?: ""
            val h = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH) ?: ""
            if (w.isNotEmpty() && h.isNotEmpty()) {
                val mp = (w.toInt() * h.toInt()) / 1_000_000f
                details += "Resolution: ${w}x${h} (${String.format(Locale.US, "%.1f MP", mp)})\n"
            }
        } catch (_: Exception) {}

        subtitle.text = details
        subtitle.textSize = 13f
        subtitle.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        dialog.setContentView(view)
        dialog.show()
    }
}
