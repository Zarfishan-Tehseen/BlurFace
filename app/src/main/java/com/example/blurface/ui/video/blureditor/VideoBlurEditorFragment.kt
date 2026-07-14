package com.example.blurface.ui.video.blureditor

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
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

    // Same graph-scoped ViewModel as AnalyzingVideoFragment / DetectedFacesFragment.
    // We deliberately reuse it (rather than a fresh ViewModel) so the people
    // list and shouldBlur flags set on DetectedFacesFragment are still here -
    // this factory only actually runs if this screen is somehow opened first.
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

    // Local, in-progress editor state. Only pushed into the shared ViewModel
    // when the user taps the CTA - keeps intermediate slider drags from
    // spamming ViewModel state.
    private var blurType: BlurType = BlurType.GAUSSIAN
    private var shape: BlurShape = BlurShape.AUTO_FACE
    // Set via the long-press pickers on the Emoji/Color cards; single-tapping those
    // cards applies whichever of these was last picked (defaults below otherwise).
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
            binding.cardColor to BlurType.COLOR,
            binding.cardEmoji to BlurType.EMOJI
        )
        cards.forEach { (card, type) ->
            card.setOnClickListener {
                blurType = type
                updateBlurTypeSelection(cards)
                schedulePreviewUpdate()
            }
        }

        // Long-press shows a WhatsApp-reaction-style horizontal picker above the card.
        // Picking an item there both sets the value AND switches blurType to that
        // card's effect (matches "single press applies the effect, long press lets you
        // change + apply the color/emoji").
        binding.cardColor.setOnLongClickListener {
            showColorPicker(binding.cardColor, cards)
            true
        }
        binding.cardEmoji.setOnLongClickListener {
            showEmojiPicker(binding.cardEmoji, cards)
            true
        }

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

    // --- WhatsApp-reaction-style long-press pickers --------------------------------

    private fun showEmojiPicker(anchor: View, cards: Map<View, BlurType>) {
        showReactionPopup(anchor) { dismiss ->
            EMOJI_OPTIONS.map { emoji ->
                TextView(requireContext()).apply {
                    text = emoji
                    textSize = 26f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
                    isClickable = true
                    setBackgroundResource(borderlessRippleRes())
                    setOnClickListener {
                        selectedEmoji = emoji
                        blurType = BlurType.EMOJI
                        updateBlurTypeSelection(cards)
                        schedulePreviewUpdate()
                        dismiss()
                    }
                }
            }
        }
    }

    private fun showColorPicker(anchor: View, cards: Map<View, BlurType>) {
        showReactionPopup(anchor) { dismiss ->
            COLOR_OPTIONS.map { color ->
                View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                        marginStart = dp(5)
                        marginEnd = dp(5)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                        setStroke(dp(1), ContextCompat.getColor(requireContext(), R.color.divider))
                    }
                    isClickable = true
                    setOnClickListener {
                        selectedColor = color
                        blurType = BlurType.COLOR
                        updateBlurTypeSelection(cards)
                        schedulePreviewUpdate()
                        dismiss()
                    }
                }
            }
        }
    }

    /**
     * Builds a rounded, elevated horizontal strip of items (via [buildItems], which
     * receives a dismiss callback each item's click listener should call) and shows it
     * floating above [anchor] - the same interaction shape as WhatsApp's long-press
     * message-reaction picker. Dismisses on outside tap.
     */
    private fun showReactionPopup(anchor: View, buildItems: (dismiss: () -> Unit) -> List<View>) {
        val itemsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val scrollHost = HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundResource(R.drawable.bg_popup_picker)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            elevation = dp(8).toFloat()
            addView(
                itemsContainer,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }

        val popupWindow = PopupWindow(
            scrollHost,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            /* focusable = */ true
        ).apply {
            isOutsideTouchable = true
        }

        buildItems { popupWindow.dismiss() }.forEach { itemsContainer.addView(it) }

        // WRAP_CONTENT views report 0x0 until laid out - measure explicitly so we can
        // center the popup above the anchor before it's actually shown.
        scrollHost.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val anchorPos = IntArray(2)
        anchor.getLocationInWindow(anchorPos)

        val x = anchorPos[0] + anchor.width / 2 - scrollHost.measuredWidth / 2
        val y = anchorPos[1] - scrollHost.measuredHeight - dp(10)

        popupWindow.showAtLocation(
            anchor,
            Gravity.NO_GRAVITY,
            x.coerceAtLeast(dp(8)),
            y.coerceAtLeast(dp(8))
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun borderlessRippleRes(): Int {
        val outValue = TypedValue()
        requireContext().theme.resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless, outValue, true
        )
        return outValue.resourceId
    }

    private fun onBlurFacesClicked() {
        val settings = currentSettingsFromUi()
        viewModel.updateBlurSettings(settings)

        // TODO: navigate to the actual render/processing screen once it
        // exists, e.g. findNavController().navigate(R.id.blurProcessingFragment)
        // The use case would then run over selectedPeople + settings.
    }

    override fun onDestroyView() {
        // viewLifecycleOwner.lifecycleScope cancels any in-flight renderPreview() coroutine
        // automatically here; pendingSettings/isRenderingPreview just go stale harmlessly.
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