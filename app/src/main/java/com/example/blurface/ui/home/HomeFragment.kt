package com.example.blurface.ui.home

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.blurface.R
import android.media.MediaMetadataRetriever
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.blurface.data.history.RecentEditsStore
import com.example.blurface.databinding.FragmentHomeBinding
import com.example.blurface.domain.model.RecentEdit
import com.example.blurface.ui.recents.RecentEditActionsHelper
import com.example.blurface.ui.recents.RecentsViewModel
import com.webscare.prescriptionscanner.common.Utils.addPressEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var recentAdapter: HomeRecentThumbnailAdapter
    private val viewModel: RecentsViewModel by activityViewModels()
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { navigateToDetectingFaces(it) }
    }

    private val pickBackgroundImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { navigateToBackgroundBlur(it) }
    }

    private val pickVideo = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { checkVideoDurationAndNavigate(it) }
    }
    private val selectMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            handleSelectedMedia(uri)
        } else {
            Toast.makeText(requireContext(), getString(R.string.no_media_selected), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.headerContainer) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        binding.btnSelectPhoto.addPressEffect {
            selectMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }
        binding.cardBlurFaces.addPressEffect {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.cardBlurBackground.addPressEffect {
            pickBackgroundImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.cardBlurVideo.addPressEffect {
            pickVideo.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        }
        binding.btnCrown.addPressEffect {
            findNavController().navigate(R.id.premiumFragment)
        }
        setUpRecentEdits()
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentEditsForHome.collect { edits ->
                    recentAdapter.submitList(edits)

                    if (edits.isEmpty()) {
                        binding.rvRecentEdits.visibility = View.GONE
                        binding.layoutEmptyState.visibility = View.VISIBLE
                        binding.btnSeeAll.visibility = View.GONE
                    } else {
                        binding.rvRecentEdits.visibility = View.VISIBLE
                        binding.layoutEmptyState.visibility = View.GONE
                        binding.btnSeeAll.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
    private fun setUpRecentEdits() {
        binding.rvRecentEdits.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        recentAdapter = HomeRecentThumbnailAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            onMoreClicked = { edit, anchor -> showActionsPopup(edit, anchor) }
        )
        binding.rvRecentEdits.adapter = recentAdapter

        binding.btnSeeAll.addPressEffect {
            // Replace R.id.recentsFragment with your bottom_nav_menu.xml item ID for Recents
            requireActivity()
                .findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
                ?.selectedItemId = R.id.recentsFragment
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun deleteEdit(edit: RecentEdit) {
        val dialogBinding = com.example.blurface.databinding.DialogConfirmActionBinding.inflate(layoutInflater)
        val dialog = android.app.Dialog(requireContext()).apply {
            setContentView(dialogBinding.root)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        dialogBinding.tvDialogTitle.text = "Delete Recent Edit?"
        dialogBinding.tvDialogMessage.text = "Are you sure you want to delete this file? This action cannot be undone."
        dialogBinding.btnConfirm.text = "Delete"

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
    private fun showActionsPopup(edit: RecentEdit, anchor: View) {
        RecentEditActionsHelper.showPopup(
            context = requireContext(),
            anchor = anchor,
            onDownload = { downloadEdit(edit) },
            onDelete = { deleteEdit(edit) },
            onShare = { RecentEditActionsHelper.share(requireContext(), edit) }
        )
    }

    private fun downloadEdit(edit: RecentEdit) {
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { RecentEditActionsHelper.copyToDownloads(requireContext(), edit) }.getOrNull()
            }
            val message = if (saved != null) getString(R.string.saved_to_downloads) else getString(R.string.could_not_download)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
    private fun navigateToDetectingFaces(uri: Uri) {
        findNavController().navigate(
            R.id.detectingFacesFragment,
            bundleOf("imageUri" to uri.toString())
        )
    }

    private fun navigateToAnalyzingVideo(uri: Uri) {
        findNavController().navigate(
            R.id.analyzingVideoFragment,
            bundleOf("videoUri" to uri.toString())
        )
    }

    private fun navigateToBackgroundBlur(uri: Uri) {
        findNavController().navigate(
            R.id.backgroundBlurFragment,
            bundleOf("imageUri" to uri.toString())
        )
    }
    private fun handleSelectedMedia(uri: Uri) {
        val mimeType = requireContext().contentResolver.getType(uri)

        when {
            mimeType?.startsWith("image/") == true -> {
                val args = bundleOf("imageUri" to uri.toString())
                findNavController().navigate(R.id.detectingFacesFragment, args)
            }
            mimeType?.startsWith("video/") == true -> {
                checkVideoDurationAndNavigate(uri)
            }
            else -> {
                Toast.makeText(
                    requireContext(),
                    "Select Video less than 30 secs",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    private fun checkVideoDurationAndNavigate(uri: Uri) {
        val durationMs = try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(requireContext(), uri)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }

        val maxDurationMs = 30_000L // 30 seconds

        if (durationMs > maxDurationMs) {
            Toast.makeText(
                requireContext(),
                "Selected video exceeds 30 seconds limit",
                Toast.LENGTH_LONG
            ).show()
        } else {
            navigateToAnalyzingVideo(uri)
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}