package com.pandre.mileage.domain

import java.math.BigDecimal
import java.math.RoundingMode

object Amounts {
    fun parseHundredths(text: String): Long {
        val value = text.trim().replace(',', '.')
        require(Regex("[0-9]+([.][0-9]{1,2})?").matches(value)) { "Enter a positive amount with at most two decimals." }
        return BigDecimal(value).movePointRight(2).longValueExact().also { require(it > 0) { "Amount must be positive." } }
    }
    fun decimal(value: Long): String = BigDecimal.valueOf(value, 2).setScale(2).toPlainString()
    fun unitPrice(cents: Long, hundredths: Long): String =
        if (hundredths > 0) BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(hundredths), 2, RoundingMode.HALF_UP).toPlainString() else "—"
}
