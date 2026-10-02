package com.usctest.app.domain

import com.usctest.app.data.model.TestVersion

/** Official USCIS oral-exam format per bank -- see the app plan's "Test facts" section. Shared
 * by Recall Mode (the real simulation) and [ReadinessEstimator] (which simulates it statistically). */
data class ExamRules(val maxQuestions: Int, val passThreshold: Int, val failThreshold: Int)

object ExamFormat {
    fun rulesFor(version: TestVersion): ExamRules = when (version) {
        TestVersion.V2008 -> ExamRules(maxQuestions = 10, passThreshold = 6, failThreshold = 5)
        TestVersion.V2025 -> ExamRules(maxQuestions = 20, passThreshold = 12, failThreshold = 9)
    }
}
