package com.xiaz.flowvault.core.model

/** 一笔流水。amount_cents 带符号：支出为负、收入为正。categoryName 是 JOIN 出来的，行里只存 id。 */
data class LedgerTx(
    val id: Long,
    val amountCents: Long,
    val categoryId: Long?,
    val categoryName: String?,
    val note: String?,
    val occurredAt: Long,
    val dayKey: String,
)
