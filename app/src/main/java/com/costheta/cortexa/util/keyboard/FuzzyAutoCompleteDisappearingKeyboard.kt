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
import com.costheta.cortexa.util.autofill.FuzzyAutoCompleteTextView

/**
 * A specialized DisappearingKeyboard class designed specifically for FuzzyAutoCompleteTextViews.
 *
 * This class accounts for the internal timers of the FuzzyAutoCompleteTextView and links the
 * keyboard's visibility to the suggestion dropdown's visibility (`isPopupShowing()`).
 */
class FuzzyAutoCompleteDisappearingKeyboard private constructor() {

    private val delayMillis: Long = 5000
    private val handler = Handler(Looper.getMainLooper())
    private var currentAutoCompleteTextView: FuzzyAutoCompleteTextView? = null
    private var isUserTouching = false

    /**
     * The core Runnable that hides the keyboard.
     *
     * *** KEY CHANGE ***
     * It now checks two conditions again:
     * 1. The user is not actively touching the screen.
     * 2. The FuzzyAutoCompleteTextView's suggestion dropdown is NOT visible.
     * The keyboard will only be hidden if BOTH conditions are true, creating a better UX
     * by linking the keyboard state to the dropdown state.
     */
    private val hideKeyboardRunnable = Runnable {
        if (!isUserTouching && currentAutoCompleteTextView?.isPopupShowing == false) {
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
     * Registers a FuzzyAutoCompleteTextView to be monitored.
     *
     * @param editText The FuzzyAutoCompleteTextView to register.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun register(editText: FuzzyAutoCompleteTextView) {
        editText.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                currentAutoCompleteTextView = view as FuzzyAutoCompleteTextView
                currentAutoCompleteTextView?.addTextChangedListener(textWatcher)
                resetTimer()
            } else {
                (view as FuzzyAutoCompleteTextView).removeTextChangedListener(textWatcher)
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
        // The FuzzyAutoCompleteTextView is designed to chain this call, so its
        // internal listener will also execute correctly.
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
        private val INSTANCE: FuzzyAutoCompleteDisappearingKeyboard by lazy { FuzzyAutoCompleteDisappearingKeyboard() }

        fun getInstance(): FuzzyAutoCompleteDisappearingKeyboard = INSTANCE
    }
}
