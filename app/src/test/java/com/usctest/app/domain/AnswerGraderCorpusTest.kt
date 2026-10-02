package com.usctest.app.domain

import com.usctest.app.data.model.AnswerType
import com.usctest.app.data.model.Question
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Corpus-based grading tests run against the real bundled bank content (not hand-built toy
 * answer lists, like [AnswerGraderTest]) -- building out the app plan's Verification section,
 * which calls this content-backed testing out as CI-required and not yet done.
 *
 * Two angles:
 *
 * 1. **Prose-answer corpus** -- hand-authored realistic paraphrases and near-miss wrong answers
 *    for every question whose acceptable answer is a full sentence rather than a short fact. This
 *    is the category flagged as the app's differentiator from reference apps, and the one
 *    keyword-only matching is weakest on -- see the "What is Memorial Day?" case that prompted
 *    this file. Includes cross-checks against sibling questions in the same confusable cluster
 *    (Memorial Day/Veterans Day, Independence Day/Presidents Day/Juneteenth) as explicit wrong
 *    answers, rather than a blanket "no overlapping question may ever share an answer" sweep --
 *    an earlier version of this file tried that sweep against every `overlapsWith` pair and it
 *    was mostly false alarms (e.g. "the President" is a *legitimately* correct answer to several
 *    different executive-branch questions; Washington is legitimately both "Father of Our
 *    Country" and "the first president"). `overlapsWith` encodes "don't reuse as an MCQ
 *    distractor," not "these two questions' answers must be mutually exclusive."
 * 2. **ASR-noise corpus** -- hand-authored transcription-style errors (word splits/merges,
 *    phoneme confusions), standing in until mic input (step 9, currently skipped) makes a real
 *    TTS -> SpeechRecognizer round-trip corpus possible, per the plan's Open Items.
 *
 * False accepts are asserted as a hard failure -- the one thing this app must never produce.
 * False rejects are reported the same way, as a paraphrase-coverage gap to close, not a crash;
 * per the plan's asymmetric tuning policy this is the acceptable side to be wrong on.
 */
class AnswerGraderCorpusTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun loadBank(fileName: String): List<Question> =
        json.decodeFromString<List<Question>>(File("src/main/assets/$fileName").readText())

    private val bank2008 = loadBank("questions_2008.json")
    private val bank2025 = loadBank("questions_2025.json")

    private fun grade(bank: List<Question>, questionId: Int, response: String): Boolean {
        val q = bank.first { it.id == questionId }
        return AnswerGrader.grade(
            responseText = response,
            acceptableAnswers = q.acceptableAnswers,
            answerMode = q.answerMode,
            requiredCount = q.requiredCount,
            isPersonAnswer = q.answerType == AnswerType.PERSON,
        ).isCorrect
    }

    // ---------------------------------------------------------------------
    // 1. Prose-answer corpus
    // ---------------------------------------------------------------------

    private data class ProseCase(
        val bank: List<Question>,
        val label: String,
        val questionId: Int,
        val correctParaphrases: List<String>,
        val wrongAnswers: List<String> = emptyList(),
    )

    private val proseCases2025 = listOf(
        ProseCase(bank2025, "2025", 3,
            listOf("it protects the rights of the people", "it defines the different parts of the government"),
            listOf("it collects taxes from citizens")),
        ProseCase(bank2025, "2025", 6,
            listOf("the basic rights of Americans", "the rights that all Americans have"),
            listOf("the right to a fair trial, but only for citizens")),
        ProseCase(bank2025, "2025", 8,
            listOf("it says all people are created equal", "it declares that everyone is equal"),
            listOf("it freed the slaves")),
        ProseCase(bank2025, "2025", 13,
            listOf("everyone must follow the law", "nobody is above the law, not even the government"),
            listOf("the president can ignore laws he disagrees with")),
        ProseCase(bank2025, "2025", 14,
            listOf("the Federalist Papers", "the papers written by the federalists"),
            listOf("the Emancipation Proclamation")),
        ProseCase(bank2025, "2025", 15,
            listOf("so one part does not become too powerful", "so no single branch gets too much power"),
            listOf("to make the government bigger")),
        ProseCase(bank2025, "2025", 26,
            listOf("to more closely follow public opinion", "so they stay closer to what voters currently want"),
            listOf("because they represent fewer people")),
        ProseCase(bank2025, "2025", 28,
            listOf("equal representation", "so small states get the same representation as big ones"),
            listOf("because of the size of their population")),
        ProseCase(bank2025, "2025", 33,
            listOf("citizens in their district", "the people who live in their congressional district"),
            listOf("all citizens of the state")),
        ProseCase(bank2025, "2025", 34,
            listOf("citizens from their district", "voters in their congressional district"),
            listOf("the state legislature")),
        ProseCase(bank2025, "2025", 35,
            listOf("the state's population", "because they have more residents"),
            listOf("because they were one of the original 13 colonies")),
        ProseCase(bank2025, "2025", 37,
            listOf("the 22nd amendment", "to keep the president from becoming too powerful"),
            listOf("the constitution says so")),
        ProseCase(bank2025, "2025", 40,
            listOf("the vice president", "the VP takes over"),
            listOf("the speaker of the house")),
        ProseCase(bank2025, "2025", 41,
            listOf("commander in chief", "commands the armed forces"),
            listOf("makes the laws")),
        ProseCase(bank2025, "2025", 42,
            listOf("the president", "the commander in chief is the president"),
            listOf("the secretary of defense")),
        ProseCase(bank2025, "2025", 43,
            listOf("the president", "the president signs bills into law"),
            listOf("congress")),
        ProseCase(bank2025, "2025", 44,
            listOf("the president", "only the president can veto a bill"),
            listOf("the supreme court")),
        ProseCase(bank2025, "2025", 45,
            listOf("the president", "the president appoints federal judges"),
            listOf("the senate")),
        ProseCase(bank2025, "2025", 46,
            listOf("the president", "the cabinet"),
            listOf("the supreme court")),
        ProseCase(bank2025, "2025", 47,
            listOf("advises the president", "gives the president advice"),
            listOf("writes the laws")),
        ProseCase(bank2025, "2025", 49,
            listOf("it decides who is elected president", "it's how the president actually gets elected"),
            listOf("it counts the national popular vote directly")),
        ProseCase(bank2025, "2025", 51,
            listOf("resolves disputes about the law", "settles disagreements about what the law means"),
            listOf("makes the laws")),
        ProseCase(bank2025, "2025", 56,
            listOf("to be independent of politics", "so they're not influenced by politics"),
            listOf("because they are elected for life")),
        ProseCase(bank2025, "2025", 59,
            listOf("provide schooling and education", "run the public schools"),
            listOf("print money")),
        ProseCase(bank2025, "2025", 60,
            listOf(
                "powers not given to the federal government belong to the states or the people",
                "whatever isn't given to the federal government stays with the states",
            ),
            listOf("the federal government has unlimited power")),
        ProseCase(bank2025, "2025", 63,
            listOf("citizens eighteen and older can vote", "you have to be 18 to vote"),
            listOf("you must own property to vote")),
        ProseCase(bank2025, "2025", 64,
            listOf("citizens", "only U.S. citizens"),
            listOf("permanent residents")),
        ProseCase(bank2025, "2025", 68,
            listOf("naturalize", "go through naturalization"),
            listOf("marrying a citizen automatically grants it")),
        ProseCase(bank2025, "2025", 70,
            listOf("vote", "voting in elections"),
            listOf("protesting the government")),
        ProseCase(bank2025, "2025", 71,
            listOf("required by the constitution", "the 16th amendment requires it"),
            listOf("it's optional if you disagree with spending")),
        ProseCase(bank2025, "2025", 72,
            listOf("makes the draft fair", "so the draft is fair for everyone"),
            listOf("it guarantees you'll be drafted")),
        ProseCase(bank2025, "2025", 77,
            listOf("taxation without representation", "they were taxed without having a say in government"),
            listOf("the civil war")),
        ProseCase(bank2025, "2025", 80,
            listOf("Valley Forge", "the winter encampment at Valley Forge"),
            listOf("the Battle of Gettysburg")),
        ProseCase(bank2025, "2025", 84,
            listOf("they supported passing the constitution", "they helped convince people to ratify the constitution"),
            listOf("they declared independence from Britain")),
        ProseCase(bank2025, "2025", 85,
            listOf("founded the first free public libraries", "he started the public library system"),
            listOf("he was the first president")),
        ProseCase(bank2025, "2025", 86,
            listOf("first president of the United States", "he was America's first president"),
            listOf("he freed the slaves")),
        ProseCase(bank2025, "2025", 87,
            listOf("Louisiana Purchase", "he doubled the size of the country with the Louisiana Purchase"),
            listOf("he was the first president")),
        ProseCase(bank2025, "2025", 88,
            listOf("Father of the Constitution", "he's known as the father of the constitution"),
            listOf("he wrote the Declaration of Independence")),
        ProseCase(bank2025, "2025", 89,
            listOf("first secretary of the treasury", "he was the treasury secretary"),
            listOf("he was a U.S. president")),
        ProseCase(bank2025, "2025", 94,
            listOf("freed the slaves", "he ended slavery with the Emancipation Proclamation"),
            listOf("he was the first president")),
        ProseCase(bank2025, "2025", 95,
            listOf("freed the slaves", "it freed enslaved people in the Confederacy"),
            listOf("it ended World War Two")),
        ProseCase(bank2025, "2025", 101,
            listOf("to support the Allied Powers", "to help the Allies"),
            listOf("to stop the spread of communism")),
        ProseCase(bank2025, "2025", 103,
            listOf("longest economic recession in modern history", "a very long and severe economic downturn"),
            listOf("a stock market boom")),
        ProseCase(bank2025, "2025", 104,
            listOf("stock market crash of 1929", "the market crashed in 1929"),
            listOf("World War One ended")),
        ProseCase(bank2025, "2025", 106,
            listOf("Pearl Harbor", "Japan attacked Pearl Harbor"),
            listOf("Germany attacked U.S. ships")),
        ProseCase(bank2025, "2025", 107,
            listOf("34th president of the United States", "he was the 34th president"),
            listOf("he freed the slaves")),
        ProseCase(bank2025, "2025", 110,
            listOf("to stop the spread of communism", "to prevent communism from spreading"),
            listOf("to support the Allied Powers")),
        ProseCase(bank2025, "2025", 111,
            listOf("to stop the spread of communism", "to stop communism from spreading in Southeast Asia"),
            listOf("to force Iraq out of Kuwait")),
        ProseCase(bank2025, "2025", 112,
            listOf("fought to end racial discrimination", "worked to end discrimination based on race"),
            listOf("ended slavery")),
        ProseCase(bank2025, "2025", 113,
            listOf("worked for equality for all Americans", "he fought for equal rights for everyone"),
            listOf("he was a U.S. president")),
        ProseCase(bank2025, "2025", 114,
            listOf("to force the Iraqi military from Kuwait", "to push Iraq's army out of Kuwait"),
            listOf("to stop the spread of communism")),
        ProseCase(bank2025, "2025", 115,
            listOf("terrorists attacked the United States", "terrorists hijacked planes and attacked the U.S."),
            listOf("the stock market crashed")),
        ProseCase(bank2025, "2025", 118,
            listOf("the light bulb", "the invention of the light bulb"),
            listOf("the printing press")),
        ProseCase(bank2025, "2025", 121,
            listOf("13 original colonies", "there were 13 colonies originally"),
            listOf("there are 50 states")),
        ProseCase(bank2025, "2025", 122,
            listOf("one star for each state", "each star stands for a state"),
            listOf("the 13 stripes represent the colonies")),
        ProseCase(bank2025, "2025", 125,
            listOf("a holiday to celebrate U.S. independence", "it celebrates America's independence from Britain"),
            listOf(
                "a holiday to honor soldiers who died in military service",
                // Regression case: the "birthday" keyword on this question's second acceptable
                // answer ("The country's birthday") used to fire on any text merely containing
                // the word "birthday" -- including this wrong holiday's own parenthetical.
                "Presidents Day (Washington's Birthday)",
                // Regression case: Juneteenth's own official name literally contains the word
                // "independence," which used to satisfy this question's "independence" keyword.
                "Juneteenth National Independence Day",
            )),
        ProseCase(bank2025, "2025", 127,
            listOf("a holiday to honor soldiers who died in military service", "it honors soldiers who died while serving"),
            listOf("a holiday to honor people who have served in the military")),
        ProseCase(bank2025, "2025", 128,
            listOf("a holiday to honor people who have served in the military", "it honors veterans who served"),
            listOf("a holiday to honor soldiers who died in military service")),
    )

    private val proseCases2008 = listOf(
        ProseCase(bank2008, "2008", 2,
            listOf("protects basic rights of Americans", "it protects the rights of the people"),
            listOf("it collects taxes")),
        ProseCase(bank2008, "2008", 4,
            listOf("a change to the Constitution", "it's a change or addition to the Constitution"),
            listOf("a law passed by Congress")),
        ProseCase(bank2008, "2008", 8,
            listOf("declared our independence from Great Britain", "it announced that the colonies were independent"),
            listOf("it freed the slaves")),
        ProseCase(bank2008, "2008", 10,
            listOf("you can practice any religion, or not practice one", "you're free to practice any religion or none at all"),
            listOf("everyone must belong to a church")),
        ProseCase(bank2008, "2008", 12,
            listOf("no one is above the law", "everyone, including the government, has to obey the law"),
            listOf("the president can override any law")),
        ProseCase(bank2008, "2008", 16,
            listOf("Congress", "the Senate and House of Representatives"),
            listOf("the Supreme Court")),
        ProseCase(bank2008, "2008", 24,
            listOf("all people of the state", "everyone who lives in the state"),
            listOf("only the people who voted for them")),
        ProseCase(bank2008, "2008", 25,
            listOf("because some states have more people", "states with bigger populations get more representatives"),
            listOf("because they were founded earlier")),
        ProseCase(bank2008, "2008", 31,
            listOf("the Speaker of the House", "the Speaker of the House of Representatives becomes president"),
            listOf("the Vice President")),
        ProseCase(bank2008, "2008", 37,
            listOf("decides if a law goes against the Constitution", "checks whether a law is constitutional"),
            listOf("writes new laws")),
        ProseCase(bank2008, "2008", 42,
            listOf("approve zoning and land use", "control zoning and how land is used"),
            listOf("print money")),
        ProseCase(bank2008, "2008", 48,
            listOf("Citizens eighteen and older can vote", "you must be at least 18 to vote"),
            listOf("only property owners can vote")),
        ProseCase(bank2008, "2008", 49,
            listOf("vote in a federal election", "voting in a federal election is a citizen-only responsibility"),
            listOf("paying taxes")),
        ProseCase(bank2008, "2008", 53,
            listOf("obey the laws of the United States", "you promise to follow U.S. laws"),
            listOf("you promise to never leave the country")),
        ProseCase(bank2008, "2008", 57,
            listOf("between eighteen and twenty-six", "from age 18 to 26"),
            listOf("at any age")),
        ProseCase(bank2008, "2008", 61,
            listOf("because of high taxes", "they were taxed without having representation in government"),
            listOf("because of slavery")),
        ProseCase(bank2008, "2008", 65,
            listOf("the Founding Fathers wrote the Constitution", "that's where the Constitution was drafted"),
            listOf("the Declaration of Independence was signed there")),
        ProseCase(bank2008, "2008", 68,
            listOf("started the first free libraries", "he founded the public library system"),
            listOf(
                // Regression case: "U.S." fragments into two single-character tokens ("u", "s")
                // during normalization. Those trivially "match" via fuzzy edit-distance-0, which
                // used to be enough to satisfy the "3+ tokens need n-1 matched" fuzzy tolerance
                // for "U.S. diplomat" even though nothing else in the response was close.
                "he was a U.S. president",
            )),
        ProseCase(bank2008, "2008", 73,
            listOf("the Civil War", "Americans call it the Civil War"),
            listOf("the Revolutionary War")),
        ProseCase(bank2008, "2008", 75,
            listOf("freed the slaves", "he ended slavery"),
            listOf("he was the first president")),
        ProseCase(bank2008, "2008", 76,
            listOf("freed slaves in the Confederacy", "it freed slaves in the southern states"),
            listOf("it ended the war")),
        ProseCase(bank2008, "2008", 85,
            listOf("worked for equality for all Americans", "he fought for equal rights"),
            listOf("he was a U.S. president")),
        ProseCase(bank2008, "2008", 86,
            listOf("Terrorists attacked the United States", "terrorists carried out attacks on the U.S."),
            listOf("the stock market crashed")),
        ProseCase(bank2008, "2008", 96,
            listOf("there were 13 original colonies", "13 colonies existed originally"),
            listOf("there are 50 states")),
        ProseCase(bank2008, "2008", 97,
            listOf("there is one star for each state", "each star represents one state"),
            listOf("there were 13 original colonies")),
    )

    @Test
    fun `prose-answer corpus -- realistic paraphrases and near-miss wrong answers grade as expected`() {
        val falseRejects = mutableListOf<String>()
        val falseAccepts = mutableListOf<String>()

        for (case in proseCases2025 + proseCases2008) {
            for (paraphrase in case.correctParaphrases) {
                if (!grade(case.bank, case.questionId, paraphrase)) {
                    falseRejects += "${case.label} Q${case.questionId}: expected correct, graded WRONG -- '$paraphrase'"
                }
            }
            for (wrong in case.wrongAnswers) {
                if (grade(case.bank, case.questionId, wrong)) {
                    falseAccepts += "${case.label} Q${case.questionId}: expected wrong, graded CORRECT -- '$wrong'"
                }
            }
        }

        val report = "FALSE ACCEPTS -- must never happen (${falseAccepts.size}):\n" + falseAccepts.joinToString("\n") +
            "\n\nFALSE REJECTS -- paraphrase-coverage gaps (${falseRejects.size}):\n" + falseRejects.joinToString("\n")
        assertTrue(report, falseAccepts.isEmpty() && falseRejects.isEmpty())
    }

    // ---------------------------------------------------------------------
    // 2. ASR-noise corpus (hand-authored stand-in -- see class doc)
    // ---------------------------------------------------------------------

    private data class AsrCase(val bank: List<Question>, val label: String, val questionId: Int, val transcripts: List<String>)

    private val asrCases = listOf(
        // Single-token phoneme slip -- within the grader's 1-edit tolerance.
        AsrCase(bank2025, "2025", 105, listOf("Rosevelt")),
        AsrCase(bank2025, "2025", 57, listOf("Robert's")),
        AsrCase(bank2025, "2025", 39, listOf("Vence")),
        AsrCase(bank2025, "2025", 30, listOf("Jonson")),
        // Two-token proper noun, one token mis-heard -- both required, fuzzy covers the one miss.
        AsrCase(bank2025, "2025", 105, listOf("Franklin Rosevelt")),
        // ASR-style word-boundary noise: a single proper noun mis-segmented into two tokens.
        AsrCase(bank2025, "2025", 78, listOf("Jeffer son")),
        AsrCase(bank2025, "2025", 80, listOf("York town")),
    )

    @Test
    fun `ASR-noise corpus -- common mishearings still grade correct`() {
        val failures = mutableListOf<String>()
        for (case in asrCases) {
            for (transcript in case.transcripts) {
                if (!grade(case.bank, case.questionId, transcript)) {
                    failures += "${case.label} Q${case.questionId}: ASR-style transcript graded WRONG -- '$transcript'"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
