package com.usctest.app.domain

/**
 * Picks which acceptable answer gets keyed as "the" correct MCQ option for a question with more
 * than one valid answer (e.g. Q5's five First Amendment rights). Cycling by `timesSeen` instead of
 * picking randomly each time guarantees even coverage across repeated exposure -- a user who drills
 * a question five times sees five different keyed answers, not whichever one chance keeps
 * re-rolling -- so mastery reflects recognizing the whole acceptable set, not one memorized phrasing.
 */
object AnswerKeyRotation {

    fun pick(acceptableCanonicals: List<String>, timesSeen: Int): String {
        require(acceptableCanonicals.isNotEmpty()) { "Cannot key an answer from an empty answer list" }
        return acceptableCanonicals[timesSeen % acceptableCanonicals.size]
    }
}
