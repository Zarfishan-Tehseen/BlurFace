package com.example.blurface.ui.blureditor

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.example.blurface.R
import com.example.blurface.databinding.FragmentBlurEditorBinding
import com.example.blurface.domain.model.FaceEffect
import com.example.blurface.ui.viewmodel.PhotoEditViewModel
import com.example.blurface.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlurEditorFragment : Fragment() {

    private var _binding: FragmentBlurEditorBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: PhotoEditViewModel by navGraphViewModels(R.id.nav_graph)

    private lateinit var chipAdapter: SelectedFaceChipAdapter
    private var isFirstBitmapRender = true
    private var selectedColor: Int = Color.BLACK
    private var selectedEmoji: String = "😀"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBlurEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnExport.setOnClickListener {
            com.example.blurface.ui.export.ExportBottomSheetFragment()
                .show(childFragmentManager, "export_sheet")
        }

        setUpEffectChips()
        setUpIntensitySlider()
        setUpSelectedFacesStrip()
        observeViewModel()
    }

    private fun setUpEffectChips() {
        val chips = mapOf(
            binding.chipGaussian to FaceEffect.BLUR,
            binding.chipMosaic to FaceEffect.PIXELATE,
            binding.chipColor to FaceEffect.COLOR,
            binding.chipEmoji to FaceEffect.EMOJI
        )
        chips.forEach { (view, effect) ->
            view.setOnClickListener { sharedViewModel.setEffect(effect) }
        }
        binding.chipColor.setOnTouchListener(
            SwipeReactionPicker(
                anchor = binding.chipColor,
                items = COLOR_OPTIONS,
                itemViewFactory = { color -> buildColorSwatch(color) },
                isCurrent = { color -> color == selectedColor },
                onSelected = { color ->
                    selectedColor = color
                    sharedViewModel.setSelectedColor(color)
                    sharedViewModel.setEffect(FaceEffect.COLOR)
                }
            )
        )
        binding.chipEmoji.setOnTouchListener(
            SwipeReactionPicker(
                anchor = binding.chipEmoji,
                items = EMOJI_OPTIONS,
                itemViewFactory = { emoji -> buildEmojiOption(emoji) },
                isCurrent = { emoji -> emoji == selectedEmoji },
                onSelected = { emoji ->
                    selectedEmoji = emoji
                    sharedViewModel.setEmoji(emoji)
                    sharedViewModel.setEffect(FaceEffect.EMOJI)
                }
            )
        )
    }

    private fun updateChipSelection(selected: FaceEffect) {
        binding.chipGaussian.isSelected = selected == FaceEffect.BLUR
        binding.chipMosaic.isSelected = selected == FaceEffect.PIXELATE
        binding.chipColor.isSelected = selected == FaceEffect.COLOR
        binding.chipEmoji.isSelected = selected == FaceEffect.EMOJI
    }

    private fun setUpIntensitySlider() {
        binding.sliderIntensity.addOnChangeListener { _, value, fromUser ->
            binding.tvIntensityValue.text = "${value.toInt()}%"
            if (fromUser) sharedViewModel.setIntensity(value)
        }
        binding.btnReset.setOnClickListener { sharedViewModel.resetAllEdits() }
    }

    private fun setUpSelectedFacesStrip() {
        chipAdapter = SelectedFaceChipAdapter(
            onRemove = { faceId -> sharedViewModel.toggleFaceSelection(faceId) }
        )
        binding.rvSelectedFaces.adapter = chipAdapter

        binding.btnClearAll.setOnClickListener { sharedViewModel.setAllSelected(false) }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sharedViewModel.currentEffect.collect { updateChipSelection(it) }
                }
                launch {
                    sharedViewModel.intensityPercent.collect { percent ->
                        binding.tvIntensityValue.text = "${percent.toInt()}%"
                        if (binding.sliderIntensity.value != percent) {
                            binding.sliderIntensity.value = percent
                        }
                    }
                }
                launch {
                    sharedViewModel.editedBitmap.collect { bitmap ->
                        bitmap ?: return@collect
                        if (isFirstBitmapRender) {
                            binding.zoomableImage.setImageBitmap(bitmap)
                            isFirstBitmapRender = false
                        } else {
                            binding.zoomableImage.updateImagePreservingMatrix(bitmap)
                        }
                    }
                }
                launch {
                    sharedViewModel.faces.collect { faces ->
                        val selected = faces.filter { it.isSelected }
                        binding.tvSelectedCount.text =
                            getString(R.string.selected_faces_count, selected.size)

                        val bitmap = sharedViewModel.sourceBitmap.value ?: return@collect
                        val items = withContext(Dispatchers.Default) {
                            selected.map { face ->
                                SelectedFaceChipAdapter.Item(
                                    faceId = face.id,
                                    thumbnail = BitmapUtils.cropFace(bitmap, face.boundingBox)
                                )
                            }
                        }
                        chipAdapter.submitList(items)
                    }
                }
                launch {
                    sharedViewModel.faces.collect { faces ->
                        binding.zoomableImage.setFaceOverlays(faces)
                    }
                }
            }
        }
    }

    private fun buildColorSwatch(color: Int): View = View(requireContext()).apply {
        val size = dp(34)
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            marginStart = dp(6)
            marginEnd = dp(6)
        }

        val isSelected = (color == selectedColor)

        background = if (isSelected) {
            val ring = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), color)
                setColor(Color.TRANSPARENT)
            }
            val innerCircle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            LayerDrawable(arrayOf(ring, innerCircle)).apply {
                val inset = dp(5)
                setLayerInset(1, inset, inset, inset, inset)
            }
        } else {
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(dp(1), ContextCompat.getColor(requireContext(), R.color.divider))
            }
        }
    }

    private fun buildEmojiOption(emoji: String): View = TextView(requireContext()).apply {
        text = emoji
        textSize = 26f
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
    }

    private inner class SwipeReactionPicker<T>(
        private val anchor: View,
        private val items: List<T>,
        private val itemViewFactory: (T) -> View,
        private val isCurrent: (T) -> Boolean = { false },
        private val onSelected: (T) -> Unit
    ) : View.OnTouchListener {

        private val longPressHandler = Handler(Looper.getMainLooper())
        private var longPressTriggered = false
        private var lastRawX = 0f

        private var popupWindow: PopupWindow? = null
        private var itemViews: List<View> = emptyList()
        private var highlightedIndex = -1

        private val longPressRunnable = Runnable {
            longPressTriggered = true
            anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showPopup()
            updateHighlight(lastRawX)
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = event.rawX
                    longPressTriggered = false
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    longPressHandler.postDelayed(
                        longPressRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong()
                    )
                }
                MotionEvent.ACTION_MOVE -> {
                    lastRawX = event.rawX
                    if (longPressTriggered) updateHighlight(lastRawX)
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    if (longPressTriggered) {
                        commitSelection()
                    } else {
                        v.performClick()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    dismissWithoutSelecting()
                }
            }
            return true
        }

        private fun showPopup() {
            val itemsContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F6F5FB"))
                    cornerRadius = dp(24).toFloat()
                }
                setPadding(dp(12), dp(10), dp(16), dp(10))
            }
            itemViews = items.map { item ->
                itemViewFactory(item).also { view ->
                    if (isCurrent(item)) {
                        view.scaleX = 1.15f
                        view.scaleY = 1.15f
                    }
                    itemsContainer.addView(view)
                }
            }

            val scrollHost = HorizontalScrollView(requireContext()).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                clipChildren = false
                clipToPadding = false
                setPadding(0, dp(36), 0, 0)
                addView(
                    itemsContainer,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
            }

            val window = PopupWindow(
                scrollHost,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                /* focusable = */ false
            ).apply {
                isTouchable = false
                isOutsideTouchable = false
            }
            popupWindow = window

            scrollHost.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            val anchorPos = IntArray(2)
            anchor.getLocationInWindow(anchorPos)
            val screenWidth = resources.displayMetrics.widthPixels
            val x = anchorPos[0] + anchor.width / 2 - scrollHost.measuredWidth / 2
            val y = anchorPos[1] - scrollHost.measuredHeight - dp(10)

            window.showAtLocation(
                anchor,
                Gravity.NO_GRAVITY,
                x.coerceIn(dp(8), (screenWidth - scrollHost.measuredWidth - dp(8)).coerceAtLeast(dp(8))),
                y.coerceAtLeast(dp(8))
            )
        }

        private fun updateHighlight(rawX: Float) {
            val loc = IntArray(2)
            val idx = itemViews.indexOfFirst { view ->
                view.getLocationOnScreen(loc)
                rawX >= loc[0] && rawX <= loc[0] + view.width
            }
            if (idx == highlightedIndex) return
            highlightedIndex = idx
            itemViews.forEachIndexed { i, view ->
                val scale = if (i == idx) 1.6f else 1f
                val liftY = if (i == idx) -dp(16).toFloat() else 0f
                view.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .translationY(liftY)
                    .setDuration(130)
                    .start()
            }
        }

        private fun commitSelection() {
            val index = highlightedIndex
            popupWindow?.dismiss()
            popupWindow = null
            itemViews = emptyList()
            highlightedIndex = -1
            if (index in items.indices) onSelected(items[index])
        }

        private fun dismissWithoutSelecting() {
            popupWindow?.dismiss()
            popupWindow = null
            itemViews = emptyList()
            highlightedIndex = -1
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val EMOJI_OPTIONS = listOf("😀", "😂", "😍", "😎", "😱", "🤫", "🙈", "🥸")
        private val COLOR_OPTIONS = listOf(
            Color.BLACK,
            Color.parseColor("#800080"),
            Color.BLUE,
            Color.RED,
            Color.YELLOW,
            Color.WHITE,
            Color.GREEN
        )
    }
}