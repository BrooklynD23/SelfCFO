package com.ledgerlens.categorization

object DefaultCategories {
    // Root categories
    private val income = Category(
        id = Category.INCOME_ID,
        name = "Income",
        parentId = null,
        isSystemDefault = true,
        icon = "trending_up",
        color = "#4CAF50",
        sortOrder = 0
    )

    private val expenses = Category(
        id = "expenses",
        name = "Expenses",
        parentId = null,
        isSystemDefault = true,
        icon = "trending_down",
        color = "#F44336",
        sortOrder = 1
    )

    private val transfer = Category(
        id = Category.TRANSFER_ID,
        name = "Transfers",
        parentId = null,
        isSystemDefault = true,
        icon = "swap_horiz",
        color = "#2196F3",
        sortOrder = 2
    )

    private val uncategorized = Category(
        id = Category.UNCATEGORIZED_ID,
        name = "Uncategorized",
        parentId = null,
        isSystemDefault = true,
        icon = "help_outline",
        color = "#9E9E9E",
        sortOrder = 99
    )

    // Income subcategories
    private val salary = Category(
        id = "salary",
        name = "Salary",
        parentId = Category.INCOME_ID,
        isSystemDefault = true,
        icon = "work",
        color = "#66BB6A",
        sortOrder = 0
    )

    private val freelance = Category(
        id = "freelance",
        name = "Freelance",
        parentId = Category.INCOME_ID,
        isSystemDefault = true,
        icon = "laptop",
        color = "#81C784",
        sortOrder = 1
    )

    private val investments = Category(
        id = "investment_income",
        name = "Investments",
        parentId = Category.INCOME_ID,
        isSystemDefault = true,
        icon = "show_chart",
        color = "#A5D6A7",
        sortOrder = 2
    )

    private val otherIncome = Category(
        id = "other_income",
        name = "Other Income",
        parentId = Category.INCOME_ID,
        isSystemDefault = true,
        icon = "attach_money",
        color = "#C8E6C9",
        sortOrder = 3
    )

    // Expense subcategories (Level 1)
    private val foodDining = Category(
        id = "food_dining",
        name = "Food & Dining",
        parentId = "expenses",
        isSystemDefault = true,
        icon = "restaurant",
        color = "#FF7043",
        sortOrder = 0
    )

    private val transportation = Category(
        id = "transportation",
        name = "Transportation",
        parentId = "expenses",
        isSystemDefault = true,
        icon = "directions_car",
        color = "#42A5F5",
        sortOrder = 1
    )

    private val shopping = Category(
        id = "shopping",
        name = "Shopping",
        parentId = "expenses",
        isSystemDefault = true,
        icon = "shopping_bag",
        color = "#AB47BC",
        sortOrder = 2
    )

    private val billsUtilities = Category(
        id = "bills_utilities",
        name = "Bills & Utilities",
        parentId = "expenses",
        isSystemDefault = true,
        icon = "receipt",
        color = "#5C6BC0",
        sortOrder = 3
    )

    private val entertainment = Category(
        id = "entertainment",
        name = "Entertainment",
        parentId = "expenses",
        isSystemDefault = true,
        icon = "local_movies",
        color = "#EC407A",
        sortOrder = 4
    )

    private val healthFitness = Category(
        id = "health_fitness",
        name = "Health & Fitness",
        parentId = "expenses",
        isSystemDefault = true,
        icon = "fitness_center",
        color = "#26A69A",
        sortOrder = 5
    )

    private val personalCare = Category(
        id = "personal_care",
        name = "Personal Care",
        parentId = "expenses",
        isSystemDefault = true,
        icon = "spa",
        color = "#FF8A65",
        sortOrder = 6
    )

    private val education = Category(
        id = "education",
        name = "Education",
        parentId = "expenses",
        isSystemDefault = true,
        icon = "school",
        color = "#7E57C2",
        sortOrder = 7
    )

    private val travel = Category(
        id = "travel",
        name = "Travel",
        parentId = "expenses",
        isSystemDefault = true,
        icon = "flight",
        color = "#29B6F6",
        sortOrder = 8
    )

    private val homeGarden = Category(
        id = "home_garden",
        name = "Home & Garden",
        parentId = "expenses",
        isSystemDefault = true,
        icon = "home",
        color = "#8D6E63",
        sortOrder = 9
    )

    private val giftsCharity = Category(
        id = "gifts_charity",
        name = "Gifts & Charity",
        parentId = "expenses",
        isSystemDefault = true,
        icon = "card_giftcard",
        color = "#F06292",
        sortOrder = 10
    )

    private val feesCharges = Category(
        id = "fees_charges",
        name = "Fees & Charges",
        parentId = "expenses",
        isSystemDefault = true,
        icon = "account_balance",
        color = "#78909C",
        sortOrder = 11
    )

    private val otherExpenses = Category(
        id = "other_expenses",
        name = "Other Expenses",
        parentId = "expenses",
        isSystemDefault = true,
        icon = "more_horiz",
        color = "#BDBDBD",
        sortOrder = 12
    )

    // Food & Dining subcategories (Level 2)
    private val groceries = Category(
        id = "groceries",
        name = "Groceries",
        parentId = "food_dining",
        isSystemDefault = true,
        icon = "local_grocery_store",
        color = "#FF8A65",
        sortOrder = 0
    )

    private val restaurants = Category(
        id = "restaurants",
        name = "Restaurants",
        parentId = "food_dining",
        isSystemDefault = true,
        icon = "restaurant_menu",
        color = "#FFAB91",
        sortOrder = 1
    )

    private val coffee = Category(
        id = "coffee",
        name = "Coffee Shops",
        parentId = "food_dining",
        isSystemDefault = true,
        icon = "local_cafe",
        color = "#FFCCBC",
        sortOrder = 2
    )

    private val fastFood = Category(
        id = "fast_food",
        name = "Fast Food",
        parentId = "food_dining",
        isSystemDefault = true,
        icon = "fastfood",
        color = "#FFE0B2",
        sortOrder = 3
    )

    // Transportation subcategories (Level 2)
    private val gas = Category(
        id = "gas",
        name = "Gas & Fuel",
        parentId = "transportation",
        isSystemDefault = true,
        icon = "local_gas_station",
        color = "#64B5F6",
        sortOrder = 0
    )

    private val parking = Category(
        id = "parking",
        name = "Parking",
        parentId = "transportation",
        isSystemDefault = true,
        icon = "local_parking",
        color = "#90CAF9",
        sortOrder = 1
    )

    private val publicTransit = Category(
        id = "public_transit",
        name = "Public Transit",
        parentId = "transportation",
        isSystemDefault = true,
        icon = "directions_bus",
        color = "#BBDEFB",
        sortOrder = 2
    )

    private val rideshare = Category(
        id = "rideshare",
        name = "Rideshare",
        parentId = "transportation",
        isSystemDefault = true,
        icon = "local_taxi",
        color = "#E3F2FD",
        sortOrder = 3
    )

    // Transfer subcategories
    private val accountTransfer = Category(
        id = "account_transfer",
        name = "Account Transfer",
        parentId = Category.TRANSFER_ID,
        isSystemDefault = true,
        icon = "sync_alt",
        color = "#64B5F6",
        sortOrder = 0
    )

    private val creditCardPayment = Category(
        id = "credit_card_payment",
        name = "Credit Card Payment",
        parentId = Category.TRANSFER_ID,
        isSystemDefault = true,
        icon = "credit_card",
        color = "#90CAF9",
        sortOrder = 1
    )

    val all: List<Category> = listOf(
        // Root categories
        income,
        expenses,
        transfer,
        uncategorized,
        // Income subcategories
        salary,
        freelance,
        investments,
        otherIncome,
        // Expense subcategories (Level 1)
        foodDining,
        transportation,
        shopping,
        billsUtilities,
        entertainment,
        healthFitness,
        personalCare,
        education,
        travel,
        homeGarden,
        giftsCharity,
        feesCharges,
        otherExpenses,
        // Food & Dining subcategories (Level 2)
        groceries,
        restaurants,
        coffee,
        fastFood,
        // Transportation subcategories (Level 2)
        gas,
        parking,
        publicTransit,
        rideshare,
        // Transfer subcategories
        accountTransfer,
        creditCardPayment
    )

    val roots: List<Category> = listOf(income, expenses, transfer, uncategorized)

    val expenseCategories: List<Category> = all.filter { 
        it.parentId == "expenses" || findRoot(it.id) == "expenses"
    }

    val incomeCategories: List<Category> = all.filter {
        it.id == Category.INCOME_ID || it.parentId == Category.INCOME_ID
    }

    private fun findRoot(categoryId: String): String? {
        var current = all.find { it.id == categoryId }
        while (current?.parentId != null) {
            current = all.find { it.id == current?.parentId }
        }
        return current?.id
    }

    fun getCategoryById(id: String): Category? = all.find { it.id == id }

    fun getChildrenOf(parentId: String): List<Category> = all.filter { it.parentId == parentId }
}
