package com.example.blurface.ui.recents

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.blurface.R
import com.example.blurface.databinding.FragmentMediaDetailsBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.Locale

class MediaDetailsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentMediaDetailsBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.TransparentBottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaDetailsBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = arguments?.getString(ARG_TITLE) ?: "Media Details"
        val editType = arguments?.getString(ARG_EDIT_TYPE) ?: "Photo"
        val isVideo = arguments?.getBoolean(ARG_IS_VIDEO) ?: false
        val timestampMillis = arguments?.getLong(ARG_TIMESTAMP) ?: System.currentTimeMillis()
        val fileSizeBytes = arguments?.getLong(ARG_FILE_SIZE) ?: 0L
        val path = arguments?.getString(ARG_PATH) ?: ""

        binding.tvDetailTitle.text = title
        binding.tvDetailEditTypeTag.text = editType
        binding.tvDetailMediaTypeTag.text = if (isVideo) "Video" else "Photo"

        val dateFormat = SimpleDateFormat("EEEE, MMM d, yyyy · h:mm a", Locale.getDefault())
        binding.tvDetailDateTime.text = dateFormat.format(timestampMillis)

        val sizeMb = fileSizeBytes / (1024.0 * 1024.0)
        binding.tvDetailFileSize.text = String.format(Locale.US, "%.1f MB", sizeMb)

        binding.tvDetailPath.text = path.ifEmpty { "N/A" }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "MediaDetailsBottomSheet"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_EDIT_TYPE = "arg_edit_type"
        private const val ARG_IS_VIDEO = "arg_is_video"
        private const val ARG_TIMESTAMP = "arg_timestamp"
        private const val ARG_FILE_SIZE = "arg_file_size"
        private const val ARG_PATH = "arg_path"

        fun newInstance(
            title: String,
            editType: String,
            isVideo: Boolean,
            timestampMillis: Long,
            fileSizeBytes: Long,
            path: String
        ): MediaDetailsBottomSheetFragment {
            return MediaDetailsBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_EDIT_TYPE, editType)
                    putBoolean(ARG_IS_VIDEO, isVideo)
                    putLong(ARG_TIMESTAMP, timestampMillis)
                    putLong(ARG_FILE_SIZE, fileSizeBytes)
                    putString(ARG_PATH, path)
                }
            }
        }
    }
}
