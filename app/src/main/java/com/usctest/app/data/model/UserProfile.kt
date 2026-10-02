package com.usctest.app.data.model

import kotlinx.datetime.LocalDate

data class UserProfile(
    val name: String? = null,
    val testDate: LocalDate? = null,
    val filingDate: LocalDate? = null,
)
