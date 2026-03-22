package com.costheta.cortexa.util.autofill

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.content.Context
import android.util.AttributeSet
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.util.Locale
import android.os.Handler
import android.os.Looper
import android.widget.AdapterView // Import for AdapterView.OnItemClickListener

/**
 * A custom AutoCompleteTextView that provides fuzzy search suggestions using FuzzyWuzzy library.
 * This optimized version uses a single Runnable and implements more advanced filtering logic.
 */
class FuzzyAutoCompleteTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.autoCompleteTextViewStyle
) : AppCompatAutoCompleteTextView(context, attrs, defStyleAttr) {

    private val TAG = "FuzzyAutoCompleteTV"

    // Renamed from 'handler' to 'fuzzyHandler' to avoid a JVM signature clash
    // with the getHandler() method inherited from the parent View class.
    private val fuzzyHandler by lazy { Handler(Looper.getMainLooper()) }

    // A flag to prevent code from running before the view is fully initialized.
    private var isInitialized = false

    // A single, reusable Runnable to dismiss the dropdown after a delay.
    private val dismissRunnable = Runnable {
        dismissDropDown()
    }

    // The complete list of strings from which suggestions will be generated.
    private var allSuggestions: List<Pair<String, String>> = emptyList()

    // The minimum score for a suggestion to be considered valid.
    private val scoreThreshold = 70
    // The absolute maximum number of suggestions to show.
    private val maxSuggestions = 5

    init {
        // This block runs after the superclass constructor is complete.
        // It's now safe to access the handler.
        isInitialized = true
        // FIXED: Set threshold to 1 to ensure the dropdown appears for any input.
        threshold = 1
    }


    // Explicitly declare getFilter() as public override
    public override fun getFilter(): Filter {
        return FuzzyFilter(this)
    }

    /**
     * Sets the data source for fuzzy suggestions.
     * @param suggestions The list of strings to use for generating auto-suggestions.
     */
    fun setFuzzySuggestions(suggestions: List<String>) {
        // Store both lowercase and original case for each suggestion
        this.allSuggestions = suggestions.map { it.lowercase(Locale.getDefault()) to it }
    }

    /**
     * Overrides onTextChanged to manage the dismiss timer.
     */
    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        // Check the initialization flag before accessing the handler.
        if (isInitialized) {
            // FIXED: If the user is selecting an item, the view is "performing completion".
            // In this case, we don't want to interfere with the dismiss timer.
            if (isPerformingCompletion) {
                return
            }
            // When user types, cancel any pending action to dismiss the dropdown.
            fuzzyHandler.removeCallbacks(dismissRunnable)
        }
    }

    /**
     * Overrides setOnItemClickListener to dismiss the dropdown immediately when an item is chosen.
     */
    override fun setOnItemClickListener(listener: AdapterView.OnItemClickListener?) {
        super.setOnItemClickListener { parent, view, position, id ->
            listener?.onItemClick(parent, view, position, id)
            // Immediately dismiss the dropdown after an item is selected.
            fuzzyHandler.removeCallbacks(dismissRunnable) // Also remove timer here
            dismissDropDown()
        }
    }

    /**
     * An ArrayAdapter that does not perform any filtering. This is crucial to prevent
     * the default filtering behavior from overriding our custom fuzzy search results.
     */
    private class NoFilterArrayAdapter<T>(context: Context, resource: Int, objects: List<T>) :
        ArrayAdapter<T>(context, resource, objects) {
        private val dummyFilter = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                return FilterResults().apply {
                    values = objects
                    count = objects.size
                }
            }
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                // Do nothing, we already have the results.
            }
        }

        override fun getFilter(): Filter {
            return dummyFilter
        }
    }


    /**
     * Internal Filter implementation for fuzzy matching.
     */
    private class FuzzyFilter(
        private val parent: FuzzyAutoCompleteTextView
    ) : Filter() {

        private val TAG = "FuzzyFilter"

        /**
         * Performs the fuzzy filtering on a background thread.
         */
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            val filteredList = mutableListOf<String>()
            val currentSuggestions = parent.allSuggestions

            if (!constraint.isNullOrBlank()) {
                val query = constraint.toString().lowercase(Locale.getDefault())

                // Get all suggestions sorted by their fuzzy match score.
                val sortedMatches = FuzzySearch.extractSorted(
                    query,
                    currentSuggestions.map { it.first }, // Search using lowercase strings
                    parent.scoreThreshold // Only include matches with a score above the threshold
                )

                // Take the top results, up to the maximum limit.
                val topMatches = sortedMatches.take(parent.maxSuggestions)

                // Map the results back to their original character case.
                val originalCaseMap = currentSuggestions.associate { it.first to it.second }
                filteredList.addAll(topMatches.mapNotNull { match ->
                    originalCaseMap[match.string]
                })
            }

            results.values = filteredList
            results.count = filteredList.size
            return results
        }

        /**
         * Publishes the filtering results on the UI thread.
         */
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            if (results != null && results.count > 0) {
                @Suppress("UNCHECKED_CAST")
                val filteredSuggestions = results.values as List<String>

                // FIXED: Use the NoFilterArrayAdapter to prevent the default filter from running.
                val adapter = NoFilterArrayAdapter(
                    parent.context,
                    android.R.layout.simple_dropdown_item_1line,
                    filteredSuggestions
                )
                parent.setAdapter(adapter)
                parent.showDropDown()

                // Schedule the single dismissRunnable to run after 2 seconds.
                parent.fuzzyHandler.postDelayed(parent.dismissRunnable, 2000)
            } else {
                parent.dismissDropDown()
                parent.setAdapter(null) // Clear previous suggestions
            }
        }
    }

    /**
     * Provides a public method to access FuzzyWuzzy's weightedRatio for external use if needed.
     * This method is not directly used by the internal auto-suggestion mechanism, which uses extractTop.
     *
     * @param s1 The first string.
     * @param s2 The second string.
     * @return The weighted ratio score between the two strings.
     */
    fun getWeightedRatio(s1: String, s2: String): Int {
        return FuzzySearch.weightedRatio(s1, s2)
    }
}
