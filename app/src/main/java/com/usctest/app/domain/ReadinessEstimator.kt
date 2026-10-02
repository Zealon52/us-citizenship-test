package com.usctest.app.domain

import kotlin.random.Random

/**
 * Monte Carlo simulation of a real oral-exam attempt, using each question's practice accuracy as
 * the probability of getting it right if asked. Surfaced to the user as their "Confidence Score"
 * -- an estimate based on practice performance, not a prediction, per the app plan's "Progress,
 * mastery, and readiness" section.
 */
object ReadinessEstimator {

    private const val SIMULATIONS = 5000

    /** Conservative prior for a question that's never been attempted -- unpracticed content
     * should drag the estimate down, not be silently ignored or assumed correct. */
    private const val UNSEEN_PROBABILITY = 0.3

    /** [probability] is null for a never-attempted question -- callers don't decide the prior. */
    data class QuestionAccuracy(val category: String, val probability: Double?)

    data class Result(val confidencePercent: Int, val weakestCategories: List<String>)

    fun estimate(
        accuracies: List<QuestionAccuracy>,
        rules: ExamRules,
        random: Random = Random.Default,
    ): Result {
        if (accuracies.size < rules.maxQuestions) return Result(confidencePercent = 0, weakestCategories = emptyList())

        var passes = 0
        repeat(SIMULATIONS) {
            val drawn = accuracies.shuffled(random).take(rules.maxQuestions)
            var correct = 0
            var wrong = 0
            for (q in drawn) {
                if (correct >= rules.passThreshold || wrong >= rules.failThreshold) break
                if (random.nextDouble() < (q.probability ?: UNSEEN_PROBABILITY)) correct++ else wrong++
            }
            if (correct >= rules.passThreshold) passes++
        }

        val weakestCategories = accuracies
            .groupBy { it.category }
            .mapValues { (_, qs) -> qs.map { it.probability ?: UNSEEN_PROBABILITY }.average() }
            .entries
            .sortedBy { it.value }
            .take(2)
            .map { it.key }

        return Result(confidencePercent = (passes * 100) / SIMULATIONS, weakestCategories = weakestCategories)
    }
}
