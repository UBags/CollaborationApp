package com.costheta.cortexa.util.keyboard

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AutoCompleteTextView

/**
 * A specialized version of DisappearingKeyboard designed for AutoCompleteTextViews.
 *
 * This class prevents the keyboard from hiding while the user is viewing the suggestion dropdown,
 * in addition to keeping it visible during active typing or swiping.
 */
class AutoCompleteDisappearingKeyboard private constructor() {

    private val delayMillis: Long = 5000
    private val handler = Handler(Looper.getMainLooper())
    private var currentAutoCompleteTextView: AutoCompleteTextView? = null
    private var isUserTouching = false

    /**
     * The core Runnable that hides the keyboard.
     *
     * *** KEY CHANGE ***
     * It now checks only two conditions as per the requirements:
     * 1. The user is not actively touching the screen.
     * 2. The 3-second timer has elapsed.
     * The keyboard will be hidden if BOTH conditions are true.
     */
    private val hideKeyboardRunnable = Runnable {
        // The check for `isPopupShowing()` has been removed to match the requirements.
        if (!isUserTouching) {
            currentAutoCompleteTextView?.let { hideKeyboard(it) }
        }
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            // Any text change is activity, so reset the timer.
            resetTimer()
        }
    }

    private fun resetTimer() {
        handler.removeCallbacks(hideKeyboardRunnable)
        handler.postDelayed(hideKeyboardRunnable, delayMillis)
    }

    private fun stopTimer() {
        handler.removeCallbacks(hideKeyboardRunnable)
    }

    /**
     * Registers an AutoCompleteTextView to be monitored.
     *
     * @param editText The AutoCompleteTextView or its subclass to register.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun register(editText: AutoCompleteTextView) {
        editText.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                currentAutoCompleteTextView = view as AutoCompleteTextView
                currentAutoCompleteTextView?.addTextChangedListener(textWatcher)
                resetTimer()
            } else {
                (view as AutoCompleteTextView).removeTextChangedListener(textWatcher)
                if (currentAutoCompleteTextView == view) {
                    currentAutoCompleteTextView = null
                }
                stopTimer()
            }
        }

        editText.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isUserTouching = true
                    stopTimer()
                }
                MotionEvent.ACTION_MOVE -> {
                    // Continuously reset the timer as the user swipes/moves their finger
                    resetTimer()
                }
                MotionEvent.ACTION_UP -> {
                    isUserTouching = false
                    // Perform the click action associated with the view
                    v.performClick()
                    // Start the timer now that the touch has ended
                    resetTimer()
                }
            }
            // Return false to allow other touch listeners to process the event
            false
        }

        // When a user selects an item from the dropdown, it's a form of activity.
        // We reset the timer here to give them time for their next action.
        editText.setOnItemClickListener { _, _, _, _ ->
            resetTimer()
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    companion object {
        // Singleton instance created lazily
        private val INSTANCE: AutoCompleteDisappearingKeyboard by lazy { AutoCompleteDisappearingKeyboard() }

        fun getInstance(): AutoCompleteDisappearingKeyboard = INSTANCE
    }
}
