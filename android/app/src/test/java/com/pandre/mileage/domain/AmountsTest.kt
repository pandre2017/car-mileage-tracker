package com.pandre.mileage.domain
import org.junit.Assert.*
import org.junit.Test
class AmountsTest {
    @Test fun parsesCommaAndDotExactly() { assertEquals(4237L, Amounts.parseHundredths("42,37")); assertEquals(7520L, Amounts.parseHundredths("75.20")) }
    @Test fun derivesReceiptPrice() { assertEquals("1.77", Amounts.unitPrice(7520, 4237)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsExcessPrecision() { Amounts.parseHundredths("1.234") }
}
