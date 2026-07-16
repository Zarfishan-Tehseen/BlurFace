package com.example.blurface.ui.recents

import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.blurface.databinding.FragmentRecentsBinding
import com.example.blurface.databinding.PopupRecentEditActionsBinding
import com.example.blurface.databinding.PopupRecentsFilterBinding
import com.example.blurface.domain.model.EditType
import com.example.blurface.domain.model.RecentEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecentsFragment : Fragment() {

    private var _binding: FragmentRecentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecentsViewModel by viewModels()

    private lateinit var adapter: RecentEditsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecentEditsAdapter(onMoreClicked = { edit, anchor -> showActionsPopup(edit, anchor) })
        binding.rvRecentEdits.adapter = adapter

        setUpFilterChips()
        binding.btnFilterSort.setOnClickListener { showFilterPopup(it) }
        binding.btnSearch.setOnClickListener {
            // TODO: wire up a search field/screen once needed
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Picks up anything saved since the last time this screen was visible.
        viewModel.refresh()
    }

    private fun setUpFilterChips() {
        binding.filterAll.setOnClickListener { viewModel.setTypeFilter(null) }
        binding.filterBlurFaces.setOnClickListener { viewModel.setTypeFilter(EditType.BLUR_FACES) }
        binding.filterBlurBackground.setOnClickListener { viewModel.setTypeFilter(EditType.BLUR_BACKGROUND) }
    }

    private fun updateFilterChipSelection(selected: EditType?) {
        binding.filterAll.isSelected = selected == null
        binding.filterBlurFaces.isSelected = selected == EditType.BLUR_FACES
        binding.filterBlurBackground.isSelected = selected == EditType.BLUR_BACKGROUND
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.typeFilter.collect { updateFilterChipSelection(it) }
                }
                launch {
                    viewModel.visibleEdits.collect { edits ->
                        adapter.submitList(edits)
                        binding.emptyState.isVisible = edits.isEmpty()
                        binding.rvRecentEdits.isVisible = edits.isNotEmpty()
                    }
                }
            }
        }
    }

    // ── Per-item actions popup ──

    private fun showActionsPopup(edit: RecentEdit, anchor: View) {
        val popupBinding = PopupRecentEditActionsBinding.inflate(LayoutInflater.from(requireContext()))
        val popup = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply { elevation = 12f }

        popupBinding.rowDownload.setOnClickListener {
            popup.dismiss()
            downloadEdit(edit)
        }
        popupBinding.rowDelete.setOnClickListener {
            popup.dismiss()
            viewModel.delete(edit)
            Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
        }
        popupBinding.rowShare.setOnClickListener {
            popup.dismiss()
            shareEdit(edit)
        }

        popup.showAsDropDown(anchor, -120, 8)
    }

    private fun downloadEdit(edit: RecentEdit) {
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { copyToDownloads(edit) }.getOrNull()
            }
            val message = if (saved != null) "Saved to Downloads" else "Could not download"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToDownloads(edit: RecentEdit): Uri? {
        val context = requireContext()
        val resolver = context.contentResolver
        val sourceUri = Uri.parse(edit.mediaUri)
        val mimeType = if (edit.isVideo) "video/mp4" else "image/jpeg"
        val extension = if (edit.isVideo) "mp4" else "jpg"
        val filename = "BlurFace_${System.currentTimeMillis()}.$extension"

        val (collectionUri, directory) = if (edit.isVideo) {
            Pair(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_MOVIES)
        } else {
            Pair(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_PICTURES)
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/BlurFace")
            }
        }

        val destUri = resolver.insert(collectionUri, values) ?: return null

        resolver.openInputStream(sourceUri)?.use { input ->
            resolver.openOutputStream(destUri)?.use { output -> input.copyTo(output) }
        } ?: return null

        return destUri
    }

    private fun shareEdit(edit: RecentEdit) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (edit.isVideo) "video/mp4" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(edit.mediaUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share"))
    }

    // ── Sort / date filter popup ──

    private fun showFilterPopup(anchor: View) {
        val popupBinding = PopupRecentsFilterBinding.inflate(LayoutInflater.from(requireContext()))
        val popup = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply { elevation = 12f }

        fun refreshSelections() {
            val sort = viewModel.sortOption.value
            popupBinding.cbSortRecentlyUpdated.isChecked = sort == SortOption.RECENTLY_UPDATED
            popupBinding.cbSortOldestFirst.isChecked = sort == SortOption.OLDEST_FIRST
            popupBinding.cbSortName.isChecked = sort == SortOption.NAME

            val date = viewModel.dateFilter.value
            popupBinding.cbDateToday.isChecked = date == DateFilter.TODAY
            popupBinding.cbDateThisWeek.isChecked = date == DateFilter.THIS_WEEK
            popupBinding.cbDateThisMonth.isChecked = date == DateFilter.THIS_MONTH
        }
        refreshSelections()

        popupBinding.sortRecentlyUpdated.setOnClickListener {
            viewModel.setSortOption(SortOption.RECENTLY_UPDATED); refreshSelections()
        }
        popupBinding.sortOldestFirst.setOnClickListener {
            viewModel.setSortOption(SortOption.OLDEST_FIRST); refreshSelections()
        }
        popupBinding.sortName.setOnClickListener {
            viewModel.setSortOption(SortOption.NAME); refreshSelections()
        }
        popupBinding.dateToday.setOnClickListener {
            viewModel.setDateFilter(DateFilter.TODAY); refreshSelections()
        }
        popupBinding.dateThisWeek.setOnClickListener {
            viewModel.setDateFilter(DateFilter.THIS_WEEK); refreshSelections()
        }
        popupBinding.dateThisMonth.setOnClickListener {
            viewModel.setDateFilter(DateFilter.THIS_MONTH); refreshSelections()
        }

        popup.showAsDropDown(anchor, -180, 8)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}