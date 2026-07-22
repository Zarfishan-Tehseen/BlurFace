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
import com.example.blurface.data.history.RecentEditsStore
import com.example.blurface.databinding.FragmentHomeBinding
import com.example.blurface.domain.model.RecentEdit
import com.example.blurface.ui.recents.RecentEditActionsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var recentAdapter: HomeRecentThumbnailAdapter
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
        uri?.let { navigateToAnalyzingVideo(it) }
    }
    private val selectMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            handleSelectedMedia(uri)
        } else {
            Toast.makeText(requireContext(), "No media selected", Toast.LENGTH_SHORT).show()
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

        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollContent) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        binding.btnSelectPhoto.setOnClickListener {
            selectMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }
        binding.cardBlurFaces.setOnClickListener {
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.cardBlurBackground.setOnClickListener {
            pickBackgroundImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.cardBlurVideo.setOnClickListener {
            pickVideo.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        }
        binding.btnCrown.setOnClickListener {
            findNavController().navigate(R.id.premiumFragment)
        }
        setUpRecentEdits()
    }

    private fun setUpRecentEdits() {
        binding.rvRecentEdits.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        recentAdapter = HomeRecentThumbnailAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            onMoreClicked = { edit, anchor -> showActionsPopup(edit, anchor) }
        )
        binding.rvRecentEdits.adapter = recentAdapter

        binding.btnSeeAll.setOnClickListener {
            findNavController().navigate(R.id.recentsFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        loadRecentEdits()
    }

    private fun loadRecentEdits() {
        val edits = RecentEditsStore(requireContext()).getAll().take(8)
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
            val message = if (saved != null) "Saved to Downloads" else "Could not download"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteEdit(edit: RecentEdit) {
        runCatching {
            requireContext().contentResolver.delete(Uri.parse(edit.mediaUri), null, null)
        }
        RecentEditsStore(requireContext()).delete(edit.id)
        loadRecentEdits()
        Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
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
                val args = bundleOf("videoUri" to uri.toString())
                findNavController().navigate(R.id.analyzingVideoFragment, args)
            }
            else -> {
                Toast.makeText(
                    requireContext(),
                    "Unsupported file type. Please select an image or video.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}