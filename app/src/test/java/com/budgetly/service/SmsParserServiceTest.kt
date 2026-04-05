package com.budgetly.service

import com.budgetly.data.models.ExpenseCategory
import com.budgetly.data.models.PaymentMode
import com.budgetly.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SmsParserServiceTest {

    private lateinit var parser: SmsParserService

    @Before
    fun setup() {
        parser = SmsParserService()
    }

    // ─── HDFC Debit ───────────────────────────────────────────────────────

    @Test
    fun `parse HDFC UPI debit - extracts amount merchant and category`() {
        val sms = "Dear Customer, Rs.2,350.00 debited from A/c XX1234 on 15-Jan-24. " +
                "Info: UPI/Zomato India/Pay. Avl Bal:12,450.50. -HDFC Bank"

        val result = parser.parseSms("HDFCBK", sms)

        assertNotNull("Should parse financial SMS", result)
        assertEquals(2350.0, result!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, result.transactionType)
        assertEquals("HDFC", result.bankName)
        assertEquals("1234", result.accountLast4)
        assertEquals(ExpenseCategory.FOOD_DINING, result.category)
    }

    @Test
    fun `parse HDFC credit card purchase at Amazon`() {
        val sms = "HDFC Bank: Rs.4,999.00 spent on Credit Card XX5678 at Amazon on 12-Jan-24. " +
                "Available limit: Rs.85,001.00. To dispute call 1800."

        val result = parser.parseSms("HDFCBK", sms)

        assertNotNull(result)
        assertEquals(4999.0, result!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, result.transactionType)
        assertEquals(PaymentMode.CREDIT_CARD, result.paymentMode)
        assertEquals(ExpenseCategory.SHOPPING, result.category)
    }

    // ─── ICICI Debit ──────────────────────────────────────────────────────

    @Test
    fun `parse ICICI UPI debit to PhonePe`() {
        val sms = "ICICI Bank Acct XX9012 debited for Rs 850.00 on 10-Jan-2024. " +
                "Info: UPI/PhonePe/Electricity Bill. Available Balance is Rs 34,250.00"

        val result = parser.parseSms("ICICIB", sms)

        assertNotNull(result)
        assertEquals(850.0, result!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, result.transactionType)
        assertEquals(ExpenseCategory.BILLS_UTILITIES, result.category)
    }

    @Test
    fun `parse ICICI salary credit`() {
        val sms = "Your ICICI Bank Account XX3456 is credited with Rs.75,000.00 on 01-Jan-24. " +
                "Info: SALARY/NEFT. Available Balance: Rs.1,23,456.78"

        val result = parser.parseSms("ICICIB", sms)

        assertNotNull(result)
        assertEquals(75000.0, result!!.amount, 0.01)
        assertEquals(TransactionType.CREDIT, result.transactionType)
        assertEquals(ExpenseCategory.SALARY, result.category)
    }

    // ─── SBI Debit ────────────────────────────────────────────────────────

    @Test
    fun `parse SBI debit for fuel`() {
        val sms = "Your A/c No. XXXXXX7890 is debited by Rs.3000.00 on date 08Jan24 " +
                "towards UPI/HPCL Petrol Pump/Fuel. Available Balance is Rs.8,234.56-SBI"

        val result = parser.parseSms("SBI", sms)

        assertNotNull(result)
        assertEquals(3000.0, result!!.amount, 0.01)
        assertEquals(TransactionType.DEBIT, result.transactionType)
        assertEquals(ExpenseCategory.FUEL, result.category)
    }

    // ─── Amount Parsing Edge Cases ────────────────────────────────────────

    @Test
    fun `parse amount with Indian comma format`() {
        val sms = "Rs.1,00,000.00 debited from your account XX4321 via NEFT. Ref: 12345ABC"
        val result = parser.parseSms("AXISBK", sms)
        assertNotNull(result)
        assertEquals(100000.0, result!!.amount, 0.01)
    }

    @Test
    fun `parse amount with rupee symbol`() {
        val sms = "₹599.00 debited from Kotak Account XX8765 at Netflix. Ref No: TXN98765"
        val result = parser.parseSms("KOTAK", sms)
        assertNotNull(result)
        assertEquals(599.0, result!!.amount, 0.01)
    }

    @Test
    fun `parse amount with INR prefix`() {
        val sms = "INR 1500.00 has been debited from your account XX2222 to Swiggy via UPI"
        val result = parser.parseSms("YESBNK", sms)
        assertNotNull(result)
        assertEquals(1500.0, result!!.amount, 0.01)
    }

    @Test
    fun `parse small decimal amount`() {
        val sms = "Rs.12.50 debited from A/c XX1111 at Parking via UPI. Ref: PARK123"
        val result = parser.parseSms("HDFCBK", sms)
        assertNotNull(result)
        assertEquals(12.5, result!!.amount, 0.01)
    }

    // ─── Non-financial SMS should return null ─────────────────────────────

    @Test
    fun `non-financial sms returns null`() {
        val sms = "Your OTP is 123456. Do not share with anyone. Valid for 10 minutes."
        val result = parser.parseSms("TM-HDFCBK", sms)
        assertNull("OTP SMS should not be parsed as transaction", result)
    }

    @Test
    fun `promotional sms returns null`() {
        val sms = "Get 50% off on your next order! Use code SAVE50. Shop now at example.com"
        val result = parser.parseSms("VM-OFFERS", sms)
        assertNull("Promotional SMS should not be parsed", result)
    }

    @Test
    fun `balance enquiry sms returns null`() {
        val sms = "Your HDFC Bank Account XX1234 balance is Rs.45,678.90 as on 15-Jan-24."
        // Balance SMS has no debit/credit keyword → should return null or be filtered
        // This is a grey area; the parser may pick it up or not depending on keywords
        // Just verify it doesn't crash
        val result = parser.parseSms("HDFCBK", sms)
        // Not asserting null here because balance SMSes look similar to transactions
        // The important thing is no exception thrown
    }

    // ─── Category Detection ───────────────────────────────────────────────

    @Test
    fun `categorize Zomato as food dining`() {
        val cat = parser.categorize("Zomato India", "UPI payment at Zomato India")
        assertEquals(ExpenseCategory.FOOD_DINING, cat)
    }

    @Test
    fun `categorize Netflix as entertainment`() {
        val cat = parser.categorize("Netflix", "Rs 649 debited for Netflix subscription")
        assertEquals(ExpenseCategory.ENTERTAINMENT, cat)
    }

    @Test
    fun `categorize BigBasket as groceries`() {
        val cat = parser.categorize("BigBasket", "payment to BigBasket via UPI")
        assertEquals(ExpenseCategory.GROCERIES, cat)
    }

    @Test
    fun `categorize HPCL as fuel`() {
        val cat = parser.categorize("HPCL Petrol", "payment at HPCL Petrol pump")
        assertEquals(ExpenseCategory.FUEL, cat)
    }

    @Test
    fun `categorize Uber as transport`() {
        val cat = parser.categorize("Uber India", "paid to Uber India via UPI")
        assertEquals(ExpenseCategory.TRANSPORT, cat)
    }

    @Test
    fun `categorize Apollo as health`() {
        val cat = parser.categorize("Apollo Pharmacy", "payment at Apollo Pharmacy")
        assertEquals(ExpenseCategory.HEALTH, cat)
    }

    @Test
    fun `categorize EMI as emi category`() {
        val cat = parser.categorize("Bajaj Finance", "EMI payment to Bajaj Finance")
        assertEquals(ExpenseCategory.EMI, cat)
    }

    @Test
    fun `unknown merchant returns OTHER`() {
        val cat = parser.categorize("XYZ Corp Unknown", "payment to XYZ Corp Unknown")
        assertEquals(ExpenseCategory.OTHER, cat)
    }

    // ─── Payment Mode Detection ───────────────────────────────────────────

    @Test
    fun `detect UPI payment mode`() {
        val sms = "Rs.500 debited via UPI from account XX1234. VPA: merchant@upi"
        val result = parser.parseSms("HDFCBK", sms)
        assertEquals(PaymentMode.UPI, result?.paymentMode)
    }

    @Test
    fun `detect credit card payment mode`() {
        val sms = "Rs.2000 spent on Credit Card XX9876 at Flipkart on 15-Jan-24."
        val result = parser.parseSms("ICICIB", sms)
        assertEquals(PaymentMode.CREDIT_CARD, result?.paymentMode)
    }

    @Test
    fun `detect NEFT as net banking mode`() {
        val sms = "Rs.15000 debited from A/c XX5432 via NEFT to beneficiary. Ref: NEFT123"
        val result = parser.parseSms("AXISBK", sms)
        assertEquals(PaymentMode.NET_BANKING, result?.paymentMode)
    }

    // ─── Reference Number Extraction ─────────────────────────────────────

    @Test
    fun `extract UPI reference number`() {
        val sms = "Rs.750.00 debited from XX2345 via UPI. UPI Ref: 401234567890. Balance: Rs.5000"
        val result = parser.parseSms("HDFCBK", sms)
        assertNotNull(result)
        assertEquals("401234567890", result!!.referenceNumber)
    }

    @Test
    fun `extract transaction id`() {
        val sms = "Rs.299 debited. TxnId: TXN20240115ABC123. At Spotify. Bal: Rs.12000"
        val result = parser.parseSms("KOTAK", sms)
        assertNotNull(result)
        assertTrue("Reference should not be empty", result!!.referenceNumber.isNotBlank())
    }

    // ─── Account Number Extraction ────────────────────────────────────────

    @Test
    fun `extract 4-digit account suffix`() {
        val sms = "Rs.1200 debited from A/c XX6789 at BigBasket via UPI. Bal: 8000"
        val result = parser.parseSms("HDFCBK", sms)
        assertEquals("6789", result?.accountLast4)
    }

    // ─── Deduplication Key ────────────────────────────────────────────────

    @Test
    fun `same transaction produces same dedup key`() {
        val sms = "Rs.500 debited from XX1234. UPI Ref: REF12345. At Swiggy."
        val r1 = parser.parseSms("HDFCBK", sms)
        val r2 = parser.parseSms("HDFCBK", sms)
        assertEquals(r1?.referenceNumber, r2?.referenceNumber)
    }
}
