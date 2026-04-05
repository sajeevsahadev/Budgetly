package com.budgetly.service

import android.util.Log
import com.budgetly.data.models.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core SMS parsing engine.
 * Parses bank transaction SMS messages to extract:
 * - Amount, merchant, bank name, account info
 * - Transaction type (debit/credit)
 * - Auto-categorizes based on merchant name
 */
@Singleton
class SmsParserService @Inject constructor() {

    companion object {
        private const val TAG = "SmsParser"

        // Amount patterns - handles Rs., INR, ₹ prefix
        private val AMOUNT_PATTERNS = listOf(
            Pattern.compile("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""([\d,]+(?:\.\d{1,2})?)\s*(?:Rs\.?|INR|₹)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:debited|credited|paid|spent)\s+(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
        )

        // Merchant extraction patterns
        private val MERCHANT_PATTERNS = listOf(
            Pattern.compile("""(?:at|to|@)\s+([A-Za-z0-9\s&\-'.]+?)(?:\s+on|\s+via|\s+Ref|\s+UPI|\s+A/c|[.\n]|$)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:merchant|store|shop):\s*([A-Za-z0-9\s&\-'.]+?)(?:[.\n,]|$)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""VPA\s+([^\s@]+@[^\s]+)""", Pattern.CASE_INSENSITIVE), // UPI VPA
            Pattern.compile("""(?:paid to|transfer to)\s+([A-Za-z0-9\s&\-'.]+?)(?:\s+via|\s+on|[.\n]|$)""", Pattern.CASE_INSENSITIVE)
        )

        // Reference number patterns
        private val REF_PATTERNS = listOf(
            Pattern.compile("""(?:Ref(?:erence)?\.?\s*(?:No\.?|#)?|TxnId|IMPS Ref)\s*:?\s*([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:UTR|UPI Ref|Transaction ID)[\s:]*([A-Za-z0-9]+)""", Pattern.CASE_INSENSITIVE)
        )

        // Account patterns
        private val ACCOUNT_PATTERNS = listOf(
            Pattern.compile("""(?:A/c|Acct|Account|Card)[\s*]*(?:ending|No\.?)?[\s*]*(?:XX|x{2,}|\*+)?(\d{4})""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:XX|x{2,}|\*+)(\d{4})""", Pattern.CASE_INSENSITIVE)
        )

        // Bank senders
        private val BANK_SENDERS = mapOf(
            "HDFC" to listOf("HDFCBK", "HDFC", "HD-HDFCBK"),
            "ICICI" to listOf("ICICIB", "ICICIBANK", "iCICI"),
            "SBI" to listOf("SBI", "SBIINB", "SBIPSG", "SBIUPI"),
            "Axis Bank" to listOf("AXISBK", "AXIS", "UTIBMO"),
            "Kotak" to listOf("KOTAK", "KOTAKB", "KOTAK811"),
            "IDFC First" to listOf("IDFCBK", "IDFCFB"),
            "Yes Bank" to listOf("YESBNK", "YESBANK"),
            "IndusInd" to listOf("INDBNK", "IBLBNK"),
            "Bank of Baroda" to listOf("BOBBRD", "BOBIBN"),
            "PNB" to listOf("PNBSMS", "PUNBNK"),
            "Canara Bank" to listOf("CNRBNK", "CANARABNK"),
            "Amazon Pay" to listOf("AMAZON", "AMZNPAY", "AMZN"),
            "PhonePe" to listOf("PHONEPE", "PPAY", "PPEBNK"),
            "Google Pay" to listOf("GPAY", "GOOGLEPAY"),
            "Paytm" to listOf("PAYTM", "PYTM"),
            "CRED" to listOf("CREDPAY", "CRED"),
            "Freecharge" to listOf("FREECHARGE", "FRCHRG")
        )

        // Category keywords for auto-categorization
        private val CATEGORY_KEYWORDS = mapOf(
            ExpenseCategory.FOOD_DINING to listOf(
                "zomato", "swiggy", "dominos", "domino", "pizza", "burger king", "mcdonald", "kfc",
                "subway", "starbucks", "cafe", "restaurant", "hotel", "dhaba", "biryani",
                "barbeque nation", "haldiram", "bikanervala", "fasoos"
            ),
            ExpenseCategory.GROCERIES to listOf(
                "bigbasket", "grofers", "blinkit", "zepto", "dunzo", "more supermarket",
                "d-mart", "dmart", "reliance fresh", "reliance smart", "nature's basket",
                "star bazaar", "hypercity", "spar", "spencers", "lulu", "nilgiris",
                "grocery", "supermarket", "vegetables", "fruits", "kirana"
            ),
            ExpenseCategory.SHOPPING to listOf(
                "amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho", "snapdeal",
                "shoppers stop", "lifestyle", "max fashion", "pantaloons", "westside",
                "h&m", "zara", "adidas", "nike", "puma", "fashion", "clothing", "apparel",
                "croma", "vijay sales", "reliance digital", "apple store"
            ),
            ExpenseCategory.ENTERTAINMENT to listOf(
                "netflix", "amazon prime", "hotstar", "disney+", "zee5", "sonyliv", "voot",
                "pvr", "inox", "cinepolis", "bookmyshow", "spotify", "gaana", "youtube",
                "games", "gaming", "playstation", "xbox", "steam"
            ),
            ExpenseCategory.TRANSPORT to listOf(
                "uber", "ola", "rapido", "auto", "cab", "taxi", "irctc", "train",
                "indigo", "air india", "spicejet", "go air", "vistara", "airline",
                "bus", "metro", "fastag", "toll", "makemytrip", "yatra", "cleartrip",
                "redbus", "abhibus", "ola electric"
            ),
            ExpenseCategory.HEALTH to listOf(
                "pharmeasy", "netmeds", "1mg", "apollo pharmacy", "medplus", "tata 1mg",
                "hospital", "clinic", "doctor", "diagnostic", "lab", "thyrocare",
                "dr lal", "practo", "health", "medicine", "pharmacy", "gym", "fitness",
                "cult fit", "cultfit", "healthify"
            ),
            ExpenseCategory.BILLS_UTILITIES to listOf(
                "bescom", "msedcl", "electricity", "water", "gas", "bsnl", "airtel",
                "jio", "vodafone", "vi", "idea", "tata sky", "dish tv", "sun direct",
                "broadband", "internet", "bill payment", "utility", "municipal", "maintenance"
            ),
            ExpenseCategory.FUEL to listOf(
                "petrol", "diesel", "fuel", "hp petrol", "bharat petroleum", "indian oil",
                "iocl", "hpcl", "bpcl", "cng", "shell", "essar"
            ),
            ExpenseCategory.EDUCATION to listOf(
                "school", "college", "university", "tuition", "course", "udemy", "coursera",
                "byjus", "byju", "unacademy", "vedantu", "books", "stationery"
            ),
            ExpenseCategory.TRAVEL to listOf(
                "hotel", "oyo", "treebo", "fabhotels", "airbnb", "booking.com", "goibibo",
                "holiday", "resort", "tourism", "travel"
            ),
            ExpenseCategory.INVESTMENTS to listOf(
                "zerodha", "groww", "upstox", "angel broking", "icicidirect", "mutual fund",
                "sip", "stocks", "shares", "investment", "nps", "provident fund"
            ),
            ExpenseCategory.INSURANCE to listOf(
                "lic", "hdfc life", "icici prudential", "sbi life", "max life", "bajaj allianz",
                "star health", "niva bupa", "insurance", "premium", "policy"
            ),
            ExpenseCategory.EMI to listOf(
                "emi", "loan", "mortgage", "home loan", "car loan", "personal loan",
                "bajaj finance", "hdfc credila", "full emi", "auto debit emi"
            ),
            ExpenseCategory.SUBSCRIPTIONS to listOf(
                "subscription", "membership", "prime", "annual plan", "monthly plan",
                "renewal", "auto-renewal", "recurring"
            )
        )
    }

    /**
     * Main entry point - parse a raw SMS body
     */
    fun parseSms(sender: String, body: String): ParsedTransaction? {
        return try {
            if (!isFinancialSms(sender, body)) return null

            val transactionType = detectTransactionType(body)
            val amount = extractAmount(body) ?: return null
            val merchant = extractMerchant(body)
            val bankName = detectBankName(sender)
            val accountLast4 = extractAccountLast4(body)
            val refNumber = extractReferenceNumber(body)
            val paymentMode = detectPaymentMode(body)

            val category = categorize(merchant, body)

            ParsedTransaction(
                amount = amount,
                merchantName = merchant ?: "",
                transactionType = transactionType,
                bankName = bankName,
                accountLast4 = accountLast4,
                referenceNumber = refNumber,
                paymentMode = paymentMode,
                category = category,
                rawSmsBody = body
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SMS: ${e.message}", e)
            null
        }
    }

    private fun isFinancialSms(sender: String, body: String): Boolean {
        val lowerBody = body.lowercase()
        val financialKeywords = listOf(
            "debited", "credited", "debit", "credit", "rs.", "rs ", "inr", "₹",
            "transaction", "payment", "paid", "spent", "transferred", "withdrawn",
            "upi", "imps", "neft", "rtgs", "emi"
        )
        // Check if it comes from a known bank sender or contains financial keywords
        val fromKnownSender = BANK_SENDERS.values.flatten()
            .any { sender.uppercase().contains(it.uppercase()) }

        val hasFinancialContent = financialKeywords.any { lowerBody.contains(it) }
        return fromKnownSender || hasFinancialContent
    }

    private fun detectTransactionType(body: String): TransactionType {
        val lower = body.lowercase()
        val debitKeywords = listOf("debited", "debit", "spent", "paid", "payment done",
            "transferred from", "withdrawal", "purchase", "charged")
        val creditKeywords = listOf("credited", "credit", "received", "refund", "cashback",
            "transferred to your", "deposited")

        val debitScore = debitKeywords.count { lower.contains(it) }
        val creditScore = creditKeywords.count { lower.contains(it) }

        return if (creditScore > debitScore) TransactionType.CREDIT else TransactionType.DEBIT
    }

    private fun extractAmount(body: String): Double? {
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val amountStr = matcher.group(1)?.replace(",", "") ?: continue
                return amountStr.toDoubleOrNull()
            }
        }
        return null
    }

    private fun extractMerchant(body: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val merchant = matcher.group(1)?.trim()
                if (!merchant.isNullOrBlank() && merchant.length > 2) {
                    return cleanMerchantName(merchant)
                }
            }
        }
        return null
    }

    private fun cleanMerchantName(raw: String): String {
        return raw
            .trim()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""[*#]"""), "")
            .take(50) // Max 50 chars
    }

    private fun detectBankName(sender: String): String {
        val upperSender = sender.uppercase()
        for ((bank, codes) in BANK_SENDERS) {
            if (codes.any { upperSender.contains(it.uppercase()) }) {
                return bank
            }
        }
        return sender.take(20)
    }

    private fun extractAccountLast4(body: String): String {
        for (pattern in ACCOUNT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                return matcher.group(1) ?: ""
            }
        }
        return ""
    }

    private fun extractReferenceNumber(body: String): String {
        for (pattern in REF_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                return matcher.group(1) ?: ""
            }
        }
        return ""
    }

    private fun detectPaymentMode(body: String): PaymentMode {
        val lower = body.lowercase()
        return when {
            lower.contains("upi") || lower.contains("vpa") -> PaymentMode.UPI
            lower.contains("credit card") || lower.contains("cc ") -> PaymentMode.CREDIT_CARD
            lower.contains("debit card") || lower.contains("dc ") -> PaymentMode.DEBIT_CARD
            lower.contains("neft") || lower.contains("rtgs") || lower.contains("imps") -> PaymentMode.NET_BANKING
            lower.contains("emi") -> PaymentMode.EMI
            lower.contains("wallet") -> PaymentMode.WALLET
            else -> PaymentMode.OTHER
        }
    }

    /**
     * Auto-categorize based on merchant name and SMS body
     */
    fun categorize(merchantName: String?, body: String): ExpenseCategory {
        val searchText = "${merchantName?.lowercase() ?: ""} ${body.lowercase()}"

        // Check each category's keywords
        for ((category, keywords) in CATEGORY_KEYWORDS) {
            if (keywords.any { keyword -> searchText.contains(keyword) }) {
                return category
            }
        }

        // EMI detection
        if (searchText.contains("emi") || searchText.contains("loan emi")) {
            return ExpenseCategory.EMI
        }

        // Salary credit
        if (searchText.contains("salary") || searchText.contains("payroll")) {
            return ExpenseCategory.SALARY
        }

        return ExpenseCategory.OTHER
    }

    /**
     * Get common subscription names for auto-detection
     */
    fun getKnownSubscriptions(): List<String> = listOf(
        "Netflix", "Amazon Prime", "Hotstar", "Disney+", "Spotify", "YouTube Premium",
        "Apple Music", "Gaana", "ZEE5", "SonyLIV", "Voot", "Hungama",
        "Jio", "Airtel", "Microsoft 365", "Google One", "iCloud",
        "Byju's", "Unacademy", "Coursera", "LinkedIn Premium",
        "Swiggy One", "Zomato Gold", "Paytm First", "Amazon Pay",
        "Cult.fit", "Healthify"
    )
}

// ─── Parsed Transaction Result ────────────────────────────────────────────

data class ParsedTransaction(
    val amount: Double,
    val merchantName: String,
    val transactionType: TransactionType,
    val bankName: String,
    val accountLast4: String,
    val referenceNumber: String,
    val paymentMode: PaymentMode,
    val category: ExpenseCategory,
    val rawSmsBody: String
)
