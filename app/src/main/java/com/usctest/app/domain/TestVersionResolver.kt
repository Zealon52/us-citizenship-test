package com.usctest.app.domain

import com.usctest.app.data.model.TestVersion
import com.usctest.app.data.model.TestVersionSetting
import com.usctest.app.data.model.UserSettings
import kotlinx.datetime.LocalDate

/**
 * Filing date decides the question bank, not interview date — interview backlogs mean both
 * banks stay relevant for years. Filed on/after Oct 20, 2025 -> 2025 test; before -> 2008 test.
 * A manual override in Settings always wins.
 */
object TestVersionResolver {
    private val cutoverDate = LocalDate(2025, 10, 20)

    fun resolve(settings: UserSettings, filingDate: LocalDate?): TestVersion {
        val manual = (settings.testVersion as? TestVersionSetting.Manual)?.version
        if (manual != null) return manual
        return if (filingDate != null && filingDate >= cutoverDate) TestVersion.V2025 else TestVersion.V2008
    }
}
