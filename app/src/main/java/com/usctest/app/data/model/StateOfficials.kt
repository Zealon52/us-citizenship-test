package com.usctest.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class OfficialsData(
    val updatedAt: String,
    val states: List<StateOfficials>,
)

@Serializable
data class StateOfficials(
    val stateName: String,
    val stateCode: String,
    val capital: String,
    val governor: String,
    val senators: List<String>,
    val representatives: List<String>,
    /** True for DC and territories: no voting senators, non-voting delegate only. */
    val hasNoVotingSenators: Boolean = false,
)
