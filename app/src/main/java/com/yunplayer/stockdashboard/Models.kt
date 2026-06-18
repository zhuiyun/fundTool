package com.yunplayer.stockdashboard

data class Dashboard(
    val indexes: List<IndexImpact>,
    val funds: List<FundImpact>,
    val timestamp: String,
    val description: String,
    val hiddenOvernight: Boolean
)

data class IndexImpact(
    val name: String,
    val changePercent: String
)

data class FundImpact(
    val id: Int,
    val name: String,
    val estimatedImpact: Double,
    val time: String
)

data class FundDetail(
    val id: Int,
    val name: String,
    val estimatedImpact: Double,
    val time: String,
    val holdings: List<Holding>
)

data class Holding(
    val name: String,
    val weight: Double,
    val change: Double
)
