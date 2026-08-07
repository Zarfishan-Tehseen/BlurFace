package com.webscare.prescriptionscanner.common

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.ranges.rangeTo

object Utils {
    private var lastVibrateTime = 0L
    private const val VIBRATE_COOL_DOWN = 150L

    @SuppressLint("ClickableViewAccessibility")
    fun View.addPressEffect(onClick: (() -> Unit)? = null) {
        var isInside = false

        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isInside = true
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // Check if finger is still inside view bounds
                    val insideNow =
                        event.x >= 0 && event.x <= v.width && event.y >= 0 && event.y <= v.height
                    if (isInside && !insideNow) {
                        // Finger moveRd out → cancel press effect
                        isInside = false
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    } else if (!isInside && insideNow) {
                        // Finger moved back in → reapply press effect
                        isInside = true
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).withEndAction {
                        if (isInside) {
                            onClick?.invoke() ?: v.performClick()
                        }
                    }.start()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isInside = false
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    true
                }

                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun View.addPressEffectWithLongClick(
        onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null
    ) {
        var isInside = false
        var longPressed = false
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        val handler = Handler(Looper.getMainLooper())

        val longPressRunnable = Runnable {
            if (isInside) {
                longPressed = true
                vibrateSoft()
                onLongClick?.invoke()
            }
        }

        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isInside = true
                    longPressed = false
                    handler.postDelayed(longPressRunnable, longPressTimeout)

                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()

                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val insideNow =
                        event.x in 0f..v.width.toFloat() && event.y in 0f..v.height.toFloat()

                    if (!insideNow && isInside) {
                        isInside = false
                        handler.removeCallbacks(longPressRunnable)
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    } else if (insideNow && !isInside) {
                        isInside = true
                        handler.postDelayed(longPressRunnable, longPressTimeout)
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)

                    if (isInside && !longPressed) {
                        // Trigger click directly here instead of relying on animation
                        onClick?.invoke()
                    }

                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()

                    isInside = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    isInside = false
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    true
                }

                else -> false
            }
        }

        isClickable = true
        isFocusable = true
    }

    fun View.vibrateSoft(durationMs: Long = 30L, amplitude: Int = 40) {
        val now = System.currentTimeMillis()
        if (now - (lastVibrateTime) < VIBRATE_COOL_DOWN) return
        lastVibrateTime = now

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(durationMs)
        }
    }
}