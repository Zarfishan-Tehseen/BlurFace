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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.blurface.databinding.FragmentRecentsBinding
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerContainer) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        adapter = RecentEditsAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            onMoreClicked = { edit, anchor -> showActionsPopup(edit, anchor) }
        )
        binding.rvRecentEdits.adapter = adapter

        setUpFilterChips()
        binding.btnFilterSort.setOnClickListener { showFilterPopup(it) }
        setUpSearch()

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Picks up anything saved since the last time this screen was visible.
        viewModel.refresh()
    }

    private fun setUpSearch() {
        binding.btnSearch.setOnClickListener { openSearch() }
        binding.btnCloseSearch.setOnClickListener { closeSearch() }

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
        RecentEditActionsHelper.showPopup(
            context = requireContext(),
            anchor = anchor,
            onDownload = { downloadEdit(edit) },
            onDelete = {
                viewModel.delete(edit)
                Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
            },
            onShare = { RecentEditActionsHelper.share(requireContext(), edit) }
        )
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
            val message = if (saved != null) "Saved to Downloads" else "Could not download"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
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