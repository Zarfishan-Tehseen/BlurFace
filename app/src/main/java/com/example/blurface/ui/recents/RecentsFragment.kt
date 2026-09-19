package com.example.blurface.ui.recents

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.PopupWindow
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.blurface.R
import com.example.blurface.databinding.FragmentRecentsBinding
import com.example.blurface.databinding.PopupRecentsFilterBinding
import com.example.blurface.domain.model.RecentEdit
import com.webscare.prescriptionscanner.common.Utils.addPressEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecentsFragment : Fragment() {

    private var _binding: FragmentRecentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecentsViewModel by activityViewModels()
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerContainer) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        adapter = RecentEditsAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            onItemClicked = { edit -> openMediaPreview(edit) },
            onMoreClicked = { edit, anchor -> showActionsPopup(edit, anchor) }
        )
        binding.rvRecentEdits.adapter = adapter

        setUpFilterChips()
        binding.btnFilterSort.addPressEffect { showFilterPopup(binding.btnFilterSort) }
        setUpSearch()

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Picks up anything saved since the last time this screen was visible.
        viewModel.refresh()
    }

    private fun setUpSearch() {
        binding.btnSearch.addPressEffect { openSearch() }
        binding.btnCloseSearch.addPressEffect { closeSearch() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString().orEmpty())
            }
        })
    }

    private fun openSearch() {
        binding.titleRow.visibility = View.GONE
        binding.searchBarContainer.visibility = View.VISIBLE
        binding.etSearch.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeSearch() {
        binding.etSearch.text?.clear()
        viewModel.setSearchQuery("")
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        binding.searchBarContainer.visibility = View.GONE
        binding.titleRow.visibility = View.VISIBLE
    }

    private fun setUpFilterChips() {
        binding.filterAll.addPressEffect { viewModel.setTypeFilter(RecentsTypeFilter.ALL) }
        binding.filterBlurFaces.addPressEffect { viewModel.setTypeFilter(RecentsTypeFilter.BLUR_FACES) }
        binding.filterBlurBackground.addPressEffect { viewModel.setTypeFilter(RecentsTypeFilter.BLUR_BACKGROUND) }
        binding.filterVideo.addPressEffect { viewModel.setTypeFilter(RecentsTypeFilter.VIDEO) }
    }

    private fun updateFilterChipSelection(selected: RecentsTypeFilter) {
        binding.filterAll.isSelected = selected == RecentsTypeFilter.ALL
        binding.filterBlurFaces.isSelected = selected == RecentsTypeFilter.BLUR_FACES
        binding.filterBlurBackground.isSelected = selected == RecentsTypeFilter.BLUR_BACKGROUND
        binding.filterVideo.isSelected = selected == RecentsTypeFilter.VIDEO
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

    private fun openMediaPreview(edit: RecentEdit) {
        val bundle = Bundle().apply {
            putString("mediaUri", edit.mediaUri)
            putBoolean("isVideo", edit.isVideo)
            putString("title", edit.title)
            putString("editType", edit.editType.label)
            putLong("timestampMillis", edit.timestampMillis)
            putLong("fileSizeBytes", edit.fileSizeBytes)
        }
        findNavController().navigate(com.example.blurface.R.id.mediaPreviewFragment, bundle)
    }

    // ── Per-item actions popup ──

    private fun showActionsPopup(edit: RecentEdit, anchor: View) {
        RecentEditActionsHelper.showPopup(
            context = requireContext(),
            anchor = anchor,
            onDownload = { downloadEdit(edit) },
            onDelete = { showDeleteConfirmationDialog(edit) },
            onShare = { RecentEditActionsHelper.share(requireContext(), edit) }
        )
    }

    private fun showDeleteConfirmationDialog(edit: RecentEdit) {
        val dialogBinding = com.example.blurface.databinding.DialogConfirmActionBinding.inflate(layoutInflater)
        val dialog = android.app.Dialog(requireContext()).apply {
            setContentView(dialogBinding.root)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Retain icon and buttons layout structure while updating content text
        dialogBinding.tvDialogTitle.text = getString(R.string.delete_recent_edit_title)
        dialogBinding.tvDialogMessage.text = getString(R.string.delete_recent_edit_message)
        dialogBinding.btnConfirm.text = getString(R.string.action_delete)

        dialogBinding.btnCancel.addPressEffect {
            dialog.dismiss()
        }

        dialogBinding.btnConfirm.addPressEffect {
            viewModel.delete(edit)
            Toast.makeText(requireContext(), getString(R.string.status_deleted), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun downloadEdit(edit: RecentEdit) {
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    RecentEditActionsHelper.copyToDownloads(
                        requireContext(),
                        edit
                    )
                }.getOrNull()
            }
            val message = if (saved != null) getString(R.string.saved_to_downloads) else getString(R.string.could_not_download)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    // ── Sort / date filter popup ──

    private fun showFilterPopup(anchor: View) {
        val popupBinding = PopupRecentsFilterBinding.inflate(LayoutInflater.from(requireContext()))

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

        popupBinding.sortRecentlyUpdated.addPressEffect {
            viewModel.setSortOption(SortOption.RECENTLY_UPDATED); refreshSelections()
        }
        popupBinding.sortOldestFirst.addPressEffect {
            viewModel.setSortOption(SortOption.OLDEST_FIRST); refreshSelections()
        }
        popupBinding.sortName.addPressEffect {
            viewModel.setSortOption(SortOption.NAME); refreshSelections()
        }
        popupBinding.dateToday.addPressEffect {
            viewModel.setDateFilter(DateFilter.TODAY); refreshSelections()
        }
        popupBinding.dateThisWeek.addPressEffect {
            viewModel.setDateFilter(DateFilter.THIS_WEEK); refreshSelections()
        }
        popupBinding.dateThisMonth.addPressEffect {
            viewModel.setDateFilter(DateFilter.THIS_MONTH); refreshSelections()
        }

        popupBinding.root.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = popupBinding.root.measuredWidth
        val popup = PopupWindow(
            popupBinding.root,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 12f
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        val marginPx = (8 * resources.displayMetrics.density).toInt()
        val xOffset = -(popupWidth - anchor.width + marginPx)

        popup.showAsDropDown(anchor, xOffset, 8)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}