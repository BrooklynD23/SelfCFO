package com.ledgerlens.categorization

/**
 * Default system categories seeded on first launch.
 * These categories provide a comprehensive starting point for personal finance categorization.
 */
object DefaultCategories {

    /**
     * All default categories organized by top-level parent.
     * Categories are assigned sort orders to maintain logical grouping.
     */
    val all: List<Category> by lazy {
        income + housing + foodAndDining + transportation + shopping +
        entertainment + healthAndMedical + personalCare + education +
        financialServices + transfers + other
    }

    // ==================== INCOME ====================
    val income = listOf(
        Category(
            id = "income",
            name = "Income",
            parentId = null,
            isSystemDefault = true,
            icon = "arrow_down",
            color = "#4CAF50",
            sortOrder = 0
        ),
        Category(
            id = "salary",
            name = "Salary",
            parentId = "income",
            isSystemDefault = true,
            sortOrder = 1
        ),
        Category(
            id = "freelance",
            name = "Freelance",
            parentId = "income",
            isSystemDefault = true,
            sortOrder = 2
        ),
        Category(
            id = "investments",
            name = "Investments",
            parentId = "income",
            isSystemDefault = true,
            sortOrder = 3
        ),
        Category(
            id = "refunds",
            name = "Refunds",
            parentId = "income",
            isSystemDefault = true,
            sortOrder = 4
        ),
        Category(
            id = "gifts_received",
            name = "Gifts Received",
            parentId = "income",
            isSystemDefault = true,
            sortOrder = 5
        ),
    )

    // ==================== HOUSING ====================
    val housing = listOf(
        Category(
            id = "housing",
            name = "Housing",
            parentId = null,
            isSystemDefault = true,
            icon = "home",
            color = "#795548",
            sortOrder = 10
        ),
        Category(
            id = "rent",
            name = "Rent/Mortgage",
            parentId = "housing",
            isSystemDefault = true,
            sortOrder = 11
        ),
        Category(
            id = "utilities",
            name = "Utilities",
            parentId = "housing",
            isSystemDefault = true,
            sortOrder = 12
        ),
        Category(
            id = "home_maintenance",
            name = "Home Maintenance",
            parentId = "housing",
            isSystemDefault = true,
            sortOrder = 13
        ),
        Category(
            id = "home_insurance",
            name = "Home Insurance",
            parentId = "housing",
            isSystemDefault = true,
            sortOrder = 14
        ),
        Category(
            id = "property_tax",
            name = "Property Tax",
            parentId = "housing",
            isSystemDefault = true,
            sortOrder = 15
        ),
    )

    // ==================== FOOD & DINING ====================
    val foodAndDining = listOf(
        Category(
            id = "food",
            name = "Food & Dining",
            parentId = null,
            isSystemDefault = true,
            icon = "restaurant",
            color = "#FF9800",
            sortOrder = 20
        ),
        Category(
            id = "groceries",
            name = "Groceries",
            parentId = "food",
            isSystemDefault = true,
            sortOrder = 21
        ),
        Category(
            id = "restaurants",
            name = "Restaurants",
            parentId = "food",
            isSystemDefault = true,
            sortOrder = 22
        ),
        Category(
            id = "coffee",
            name = "Coffee Shops",
            parentId = "food",
            isSystemDefault = true,
            sortOrder = 23
        ),
        Category(
            id = "delivery",
            name = "Food Delivery",
            parentId = "food",
            isSystemDefault = true,
            sortOrder = 24
        ),
        Category(
            id = "alcohol_bars",
            name = "Alcohol & Bars",
            parentId = "food",
            isSystemDefault = true,
            sortOrder = 25
        ),
    )

    // ==================== TRANSPORTATION ====================
    val transportation = listOf(
        Category(
            id = "transport",
            name = "Transportation",
            parentId = null,
            isSystemDefault = true,
            icon = "car",
            color = "#2196F3",
            sortOrder = 30
        ),
        Category(
            id = "gas",
            name = "Gas & Fuel",
            parentId = "transport",
            isSystemDefault = true,
            sortOrder = 31
        ),
        Category(
            id = "parking",
            name = "Parking",
            parentId = "transport",
            isSystemDefault = true,
            sortOrder = 32
        ),
        Category(
            id = "rideshare",
            name = "Rideshare",
            parentId = "transport",
            isSystemDefault = true,
            sortOrder = 33
        ),
        Category(
            id = "public_transit",
            name = "Public Transit",
            parentId = "transport",
            isSystemDefault = true,
            sortOrder = 34
        ),
        Category(
            id = "car_maintenance",
            name = "Car Maintenance",
            parentId = "transport",
            isSystemDefault = true,
            sortOrder = 35
        ),
        Category(
            id = "car_insurance",
            name = "Car Insurance",
            parentId = "transport",
            isSystemDefault = true,
            sortOrder = 36
        ),
        Category(
            id = "car_payment",
            name = "Car Payment",
            parentId = "transport",
            isSystemDefault = true,
            sortOrder = 37
        ),
    )

    // ==================== SHOPPING ====================
    val shopping = listOf(
        Category(
            id = "shopping",
            name = "Shopping",
            parentId = null,
            isSystemDefault = true,
            icon = "shopping_cart",
            color = "#E91E63",
            sortOrder = 40
        ),
        Category(
            id = "clothing",
            name = "Clothing",
            parentId = "shopping",
            isSystemDefault = true,
            sortOrder = 41
        ),
        Category(
            id = "electronics",
            name = "Electronics",
            parentId = "shopping",
            isSystemDefault = true,
            sortOrder = 42
        ),
        Category(
            id = "home_goods",
            name = "Home Goods",
            parentId = "shopping",
            isSystemDefault = true,
            sortOrder = 43
        ),
        Category(
            id = "gifts_given",
            name = "Gifts Given",
            parentId = "shopping",
            isSystemDefault = true,
            sortOrder = 44
        ),
    )

    // ==================== ENTERTAINMENT ====================
    val entertainment = listOf(
        Category(
            id = "entertainment",
            name = "Entertainment",
            parentId = null,
            isSystemDefault = true,
            icon = "movie",
            color = "#9C27B0",
            sortOrder = 50
        ),
        Category(
            id = "subscriptions",
            name = "Subscriptions",
            parentId = "entertainment",
            isSystemDefault = true,
            sortOrder = 51
        ),
        Category(
            id = "movies_shows",
            name = "Movies & Shows",
            parentId = "entertainment",
            isSystemDefault = true,
            sortOrder = 52
        ),
        Category(
            id = "games",
            name = "Games",
            parentId = "entertainment",
            isSystemDefault = true,
            sortOrder = 53
        ),
        Category(
            id = "concerts_events",
            name = "Concerts & Events",
            parentId = "entertainment",
            isSystemDefault = true,
            sortOrder = 54
        ),
        Category(
            id = "hobbies",
            name = "Hobbies",
            parentId = "entertainment",
            isSystemDefault = true,
            sortOrder = 55
        ),
    )

    // ==================== HEALTH & MEDICAL ====================
    val healthAndMedical = listOf(
        Category(
            id = "health",
            name = "Health & Medical",
            parentId = null,
            isSystemDefault = true,
            icon = "health",
            color = "#F44336",
            sortOrder = 60
        ),
        Category(
            id = "doctor",
            name = "Doctor",
            parentId = "health",
            isSystemDefault = true,
            sortOrder = 61
        ),
        Category(
            id = "pharmacy",
            name = "Pharmacy",
            parentId = "health",
            isSystemDefault = true,
            sortOrder = 62
        ),
        Category(
            id = "fitness",
            name = "Fitness",
            parentId = "health",
            isSystemDefault = true,
            sortOrder = 63
        ),
        Category(
            id = "health_insurance",
            name = "Health Insurance",
            parentId = "health",
            isSystemDefault = true,
            sortOrder = 64
        ),
        Category(
            id = "dental",
            name = "Dental",
            parentId = "health",
            isSystemDefault = true,
            sortOrder = 65
        ),
        Category(
            id = "vision",
            name = "Vision",
            parentId = "health",
            isSystemDefault = true,
            sortOrder = 66
        ),
    )

    // ==================== PERSONAL CARE ====================
    val personalCare = listOf(
        Category(
            id = "personal",
            name = "Personal Care",
            parentId = null,
            isSystemDefault = true,
            icon = "person",
            color = "#00BCD4",
            sortOrder = 70
        ),
        Category(
            id = "haircut",
            name = "Haircut",
            parentId = "personal",
            isSystemDefault = true,
            sortOrder = 71
        ),
        Category(
            id = "spa_massage",
            name = "Spa & Massage",
            parentId = "personal",
            isSystemDefault = true,
            sortOrder = 72
        ),
    )

    // ==================== EDUCATION ====================
    val education = listOf(
        Category(
            id = "education",
            name = "Education",
            parentId = null,
            isSystemDefault = true,
            icon = "school",
            color = "#3F51B5",
            sortOrder = 80
        ),
        Category(
            id = "tuition",
            name = "Tuition",
            parentId = "education",
            isSystemDefault = true,
            sortOrder = 81
        ),
        Category(
            id = "books_supplies",
            name = "Books & Supplies",
            parentId = "education",
            isSystemDefault = true,
            sortOrder = 82
        ),
        Category(
            id = "courses",
            name = "Courses",
            parentId = "education",
            isSystemDefault = true,
            sortOrder = 83
        ),
    )

    // ==================== FINANCIAL SERVICES ====================
    val financialServices = listOf(
        Category(
            id = "financial",
            name = "Financial Services",
            parentId = null,
            isSystemDefault = true,
            icon = "account_balance",
            color = "#009688",
            sortOrder = 85
        ),
        Category(
            id = "bank_fees",
            name = "Bank Fees",
            parentId = "financial",
            isSystemDefault = true,
            sortOrder = 86
        ),
        Category(
            id = "interest",
            name = "Interest",
            parentId = "financial",
            isSystemDefault = true,
            sortOrder = 87
        ),
        Category(
            id = "taxes",
            name = "Taxes",
            parentId = "financial",
            isSystemDefault = true,
            sortOrder = 88
        ),
    )

    // ==================== TRANSFERS ====================
    val transfers = listOf(
        Category(
            id = "transfer",
            name = "Transfers",
            parentId = null,
            isSystemDefault = true,
            icon = "swap",
            color = "#607D8B",
            sortOrder = 90
        ),
        Category(
            id = "transfer_out",
            name = "Transfer Out",
            parentId = "transfer",
            isSystemDefault = true,
            sortOrder = 91
        ),
        Category(
            id = "transfer_in",
            name = "Transfer In",
            parentId = "transfer",
            isSystemDefault = true,
            sortOrder = 92
        ),
    )

    // ==================== OTHER ====================
    val other = listOf(
        Category(
            id = "uncategorized",
            name = "Uncategorized",
            parentId = null,
            isSystemDefault = true,
            icon = "help",
            color = "#9E9E9E",
            sortOrder = 99
        ),
    )

    /**
     * Returns a category by ID from the defaults, or null if not found.
     */
    fun findById(id: String): Category? = all.find { it.id == id }

    /**
     * Returns all top-level default categories.
     */
    fun getTopLevel(): List<Category> = all.filter { it.parentId == null }

    /**
     * Returns children of a given parent category from defaults.
     */
    fun getChildren(parentId: String): List<Category> = all.filter { it.parentId == parentId }
}
