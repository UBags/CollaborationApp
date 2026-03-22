package com.costheta.cortexa.util.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * A keyboard utility class designed for standard EditText widgets.
 *
 * This class implements logic to automatically hide the soft keyboard when the user
 * has been inactive for a set period. Inactivity is defined as no typing and no
 * screen touching.
 */
class DisappearingKeyboard private constructor() {

    private val delayMillis: Long = 5000
    private val handler = Handler(Looper.getMainLooper())
    private var currentEditText: EditText? = null
    private var isUserTouching = false

    /**
     * The core Runnable that hides the keyboard.
     *
     * It checks two conditions:
     * 1. The 3-second timer has elapsed (meaning this Runnable is now executing).
     * 2. The user is not actively touching the screen.
     * The keyboard will be hidden only if the user is not touching the screen.
     */
    private val hideKeyboardRunnable = Runnable {
        if (!isUserTouching) {
            currentEditText?.let { hideKeyboard(it) }
        }
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            // Any text change is considered activity, so reset the timer.
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
     * Registers an EditText to be monitored for user activity.
     *
     * @param editText The EditText to register.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun register(editText: EditText) {
        editText.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                currentEditText = view as EditText
                currentEditText?.addTextChangedListener(textWatcher)
                resetTimer()
            } else {
                (view as EditText).removeTextChangedListener(textWatcher)
                if (currentEditText == view) {
                    currentEditText = null
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
    }

    private fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    companion object {
        // Singleton instance created lazily
        private val INSTANCE: DisappearingKeyboard by lazy { DisappearingKeyboard() }

        fun getInstance(): DisappearingKeyboard = INSTANCE
    }
}
