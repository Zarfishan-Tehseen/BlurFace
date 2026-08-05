package com.example.blurface.ui.video.blureditor

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import android.graphics.drawable.LayerDrawable
import androidx.core.graphics.blue
import androidx.core.graphics.red
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.blurface.R
import com.example.blurface.databinding.FragmentVideoBlurEditorBinding
import com.example.blurface.domain.model.BlurSettings
import com.example.blurface.domain.model.BlurShape
import com.example.blurface.domain.model.BlurType
import com.example.blurface.domain.model.Person
import com.example.blurface.domain.repository.VideoRepositoryImpl
import com.example.blurface.domain.usecase.ProcessAndClusterVideoUseCase
import com.example.blurface.ui.viewmodel.FaceClusterViewModel
import com.example.blurface.utils.VideoFaceEffectProcessor
import kotlinx.coroutines.launch

class VideoBlurEditorFragment : Fragment() {

    private var _binding: FragmentVideoBlurEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FaceClusterViewModel by navGraphViewModels(R.id.nav_graph) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val videoRepository = VideoRepositoryImpl(requireContext().applicationContext)
                val useCase = ProcessAndClusterVideoUseCase(videoRepository)
                return FaceClusterViewModel(useCase) as T
            }
        }
    }

    private lateinit var facesAdapter: SelectedFacesAdapter
    private var selectedPeople: List<Person> = emptyList()
    private var blurType: BlurType = BlurType.GAUSSIAN
    private var shape: BlurShape = BlurShape.AUTO_FACE
    private var selectedEmoji: String = "😀"
    private var selectedColor: Int = Color.BLACK

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoBlurEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerContainer) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusBars.top)
            insets
        }

        selectedPeople = viewModel.selectedPeopleForBlur()

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        setupSelectedFacesList()
        setupBlurTypeCards()
        setupShapeCards()
        setupSliders()
        applyBlurSettingsToUi(viewModel.blurSettings.value)

        binding.btnBlurFacesInVideo.setOnClickListener { onBlurFacesClicked() }

        // Render an initial live preview so the strip reflects real settings from the
        // start, not just the raw face crops.
        schedulePreviewUpdate()
    }

    private fun setupSelectedFacesList() {
        facesAdapter = SelectedFacesAdapter()
        binding.rvSelectedFaces.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvSelectedFaces.adapter = facesAdapter
        facesAdapter.submitList(selectedPeople)

        binding.tvSelectedCount.text = resources.getQuantityStringSafe(selectedPeople.size)
    }

    private fun setupBlurTypeCards() {
        val cards = mapOf<View, BlurType>(
            binding.cardGaussian to BlurType.GAUSSIAN,
            binding.cardMosaic to BlurType.MOSAIC,
            binding.cardColor to BlurType.COLOR
            //binding.cardEmoji to BlurType.EMOJI
        )
        cards.forEach { (card, type) ->
            card.setOnClickListener {
                blurType = type
                updateBlurTypeSelection(cards)
                schedulePreviewUpdate()
            }
        }

        // Long-press-and-drag shows a Facebook-reaction-style horizontal picker above
        // the card: hold, drag your finger across the swatches, whichever one you're
        // over pops up larger, and lifting your finger there commits that color AND
        // switches blurType to COLOR. A plain tap (no long-press) falls through to the
        // click listener registered above.
        binding.cardColor.setOnTouchListener(
            SwipeReactionPicker(
                anchor = binding.cardColor,
                items = COLOR_OPTIONS,
                itemViewFactory = { color -> buildColorSwatch(color) },
                isCurrent = { color -> color == selectedColor },
                onSelected = { color ->
                    selectedColor = color
                    blurType = BlurType.COLOR
                    updateBlurTypeSelection(cards)
                    schedulePreviewUpdate()
                }
            )
        )
//        binding.cardEmoji.setOnTouchListener(
//            SwipeReactionPicker(
//                anchor = binding.cardEmoji,
//                items = EMOJI_OPTIONS,
//                itemViewFactory = { emoji -> buildEmojiOption(emoji) },
//                isCurrent = { emoji -> emoji == selectedEmoji },
//                onSelected = { emoji ->
//                    selectedEmoji = emoji
//                    blurType = BlurType.EMOJI
//                    updateBlurTypeSelection(cards)
//                    schedulePreviewUpdate()
//                }
//            )
//        )

        updateBlurTypeSelection(cards)
    }

    private fun updateBlurTypeSelection(cards: Map<View, BlurType>) {
        cards.forEach { (card, type) -> card.isSelected = (type == blurType) }
    }

    private fun setupShapeCards() {
        val cards = mapOf<View, BlurShape>(
            binding.cardAutoFace to BlurShape.AUTO_FACE,
            binding.cardCircle to BlurShape.CIRCLE,
            binding.cardRectangle to BlurShape.RECTANGLE
        )
        cards.forEach { (card, s) ->
            card.setOnClickListener {
                shape = s
                updateShapeSelection(cards)
                schedulePreviewUpdate()
            }
        }
        updateShapeSelection(cards)
    }

    private fun updateShapeSelection(cards: Map<View, BlurShape>) {
        cards.forEach { (card, s) -> card.isSelected = (s == shape) }
    }

    private fun setupSliders() {
        binding.sliderIntensity.addOnChangeListener { _, value, fromUser ->
            binding.tvIntensityValue.text = "${value.toInt()}%"
            if (fromUser) schedulePreviewUpdate()
        }
        binding.sliderFeather.addOnChangeListener { _, value, fromUser ->
            binding.tvFeatherValue.text = "${value.toInt()}%"
            if (fromUser) schedulePreviewUpdate()
        }
    }

    private fun applyBlurSettingsToUi(settings: BlurSettings) {
        blurType = settings.blurType
        shape = settings.shape
        selectedEmoji = settings.emoji
        selectedColor = settings.fillColor

        binding.sliderIntensity.value = settings.intensity.toFloat()
        binding.tvIntensityValue.text = "${settings.intensity}%"

        binding.sliderFeather.value = settings.feather.toFloat()
        binding.tvFeatherValue.text = "${settings.feather}%"

        binding.switchBlurEntireVideo.isChecked = settings.blurEntireVideo

        setupBlurTypeCards()
        setupShapeCards()
    }

    /** Reads the current UI state into a BlurSettings snapshot (not yet pushed to the ViewModel). */
    private fun currentSettingsFromUi(): BlurSettings = BlurSettings(
        blurType = blurType,
        shape = shape,
        intensity = binding.sliderIntensity.value.toInt(),
        feather = binding.sliderFeather.value.toInt(),
        blurEntireVideo = binding.switchBlurEntireVideo.isChecked,
        emoji = selectedEmoji,
        fillColor = selectedColor
    )

    // Trailing-edge throttle state: while a render is in flight, new calls to
    // schedulePreviewUpdate() just overwrite pendingSettings instead of starting a
    // second concurrent render. The moment the in-flight render finishes, it
    // immediately renders whatever the latest pending settings are (if any) - so
    // during a continuous slider drag the preview keeps updating as fast as actual
    // rendering allows, and the truly final value is always rendered once you stop,
    // rather than only updating after a fixed quiet period.
    private var isRenderingPreview = false
    private var pendingSettings: BlurSettings? = null

    /**
     * Recomputes the live preview for every selected face. Safe to call on every single
     * slider tick - see the throttle notes on isRenderingPreview/pendingSettings above.
     */
    private fun schedulePreviewUpdate() {
        val settings = currentSettingsFromUi()
        if (isRenderingPreview) {
            pendingSettings = settings
            return
        }
        renderPreview(settings)
    }

    private fun renderPreview(settings: BlurSettings) {
        isRenderingPreview = true
        viewLifecycleOwner.lifecycleScope.launch {
            val previews = selectedPeople.associate { person ->
                person.id to VideoFaceEffectProcessor.applyPreview(
                    context = requireContext(),
                    faceCrop = person.representativeCrop(),
                    settings = settings
                )
            }

            if (_binding != null) facesAdapter.updatePreviews(previews)

            isRenderingPreview = false
            val next = pendingSettings
            pendingSettings = null
            if (next != null) renderPreview(next)
        }
    }

    // --- Facebook-reaction-style long-press-and-drag picker ------------------------

    private fun buildColorSwatch(color: Int): View = View(requireContext()).apply {
        val size = dp(34)
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            marginStart = dp(6)
            marginEnd = dp(6)
        }

        val isSelected = (color == selectedColor)

        background = if (isSelected) {
            // 1. Outer Ring (stroke circle)
            val ring = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), color) // Outer ring color matches selected color
                setColor(Color.TRANSPARENT)
            }

            // 2. Inner Circle (solid color)
            val innerCircle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }

            // Combine into a LayerDrawable with inset padding for the gap
            LayerDrawable(arrayOf(ring, innerCircle)).apply {
                // Index 1 (innerCircle) gets inset by 5dp to create the space/ring look
                val inset = dp(5)
                setLayerInset(1, inset, inset, inset, inset)
            }
        } else {
            // Standard unselected swatch
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

    /**
     * Facebook-like-button-style picker. Long-press [anchor] to reveal a horizontal
     * strip of items above it; without lifting your finger, drag left/right and
     * whichever item is under your finger scales up; lifting there commits [onSelected]
     * for that item. A plain tap (no long-press, i.e. finger lifts before the timer
     * fires) is *not* consumed - it's forwarded via [View.performClick] so the anchor's
     * normal OnClickListener still runs for a simple tap.
     *
     * The popup itself is non-touchable: the touch sequence stays pinned to [anchor]
     * for its entire lifetime (Android keeps delivering MOVE/UP to whichever view
     * consumed DOWN), so we hit-test the finger's raw screen X against each item
     * view's on-screen bounds to figure out what to highlight - the same trick behind
     * WhatsApp/Facebook's reaction picker.
     */
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
                // The visible pill lives here, sized to its own content - so the row
                // looks compact at rest instead of carrying the overflow buffer's height.
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
                // Transparent - exists purely as scratch space above the pill so a
                // swatch that scales up while popped-out has somewhere to draw without
                // the PopupWindow's fixed-size surface clipping it. Because the
                // background lives on itemsContainer (not here), this buffer doesn't
                // make the row look inflated when nothing is highlighted - it comes
                // out of the card like a real reaction picker instead of just being a
                // permanently taller box.
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
                // Purely a visual overlay - all touch handling stays on the anchor for
                // the whole gesture, so the popup must never intercept touches itself.
                isTouchable = false
                isOutsideTouchable = false
            }
            popupWindow = window

            // WRAP_CONTENT views report 0x0 until laid out - measure explicitly so we
            // can center the popup above the anchor before it's actually shown.
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

    private fun onBlurFacesClicked() {
        val settings = currentSettingsFromUi()
        viewModel.updateBlurSettings(settings)

        findNavController().navigate(R.id.exportProcessFragment)
    }

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
private fun android.content.res.Resources.getQuantityStringSafe(count: Int): String =
    if (count == 1) "1 face selected" else "$count faces selected"