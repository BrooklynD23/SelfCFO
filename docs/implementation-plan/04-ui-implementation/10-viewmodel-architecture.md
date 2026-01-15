# 10: ViewModel Architecture

## Overview

Implement the shared ViewModel pattern for Compose Multiplatform with proper state management, lifecycle handling, and dependency injection.

---

## Implementation Steps

### Step 1: Base ViewModel

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/viewmodel/BaseViewModel.kt
package com.ledgerlens.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel with common functionality.
 * Uses SupervisorJob so child coroutine failures don't cancel siblings.
 */
abstract class BaseViewModel {

    protected val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _events = MutableSharedFlow<ViewModelEvent>()
    val events = _events.asSharedFlow()

    /**
     * Emit a one-time event (navigation, snackbar, etc.)
     */
    protected fun emitEvent(event: ViewModelEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    /**
     * Called when ViewModel is no longer needed.
     * Override to clean up resources.
     */
    open fun onCleared() {
        viewModelScope.cancel()
    }
}

/**
 * Sealed interface for one-time UI events.
 */
sealed interface ViewModelEvent {
    data class ShowSnackbar(val message: String) : ViewModelEvent
    data class NavigateTo(val route: String) : ViewModelEvent
    data object NavigateBack : ViewModelEvent
    data class ShowError(val message: String) : ViewModelEvent
}
```

### Step 2: UI State Pattern

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/viewmodel/UiState.kt
package com.ledgerlens.viewmodel

/**
 * Wrapper for async UI state.
 */
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val retry: (() -> Unit)? = null) : UiState<Nothing>()

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
}

/**
 * Extension to transform UiState data.
 */
fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> {
    return when (this) {
        is UiState.Loading -> UiState.Loading
        is UiState.Success -> UiState.Success(transform(data))
        is UiState.Error -> UiState.Error(message, retry)
    }
}

/**
 * Execute async operation and wrap result in UiState.
 */
suspend fun <T> asUiState(
    onError: (Exception) -> String = { it.message ?: "Unknown error" },
    block: suspend () -> T
): UiState<T> {
    return try {
        UiState.Success(block())
    } catch (e: Exception) {
        UiState.Error(onError(e))
    }
}
```

### Step 3: ViewModel with Refresh Support

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/viewmodel/RefreshableViewModel.kt
package com.ledgerlens.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base for ViewModels that support pull-to-refresh.
 */
abstract class RefreshableViewModel<T> : BaseViewModel() {

    private val _state = MutableStateFlow<UiState<T>>(UiState.Loading)
    val state: StateFlow<UiState<T>> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        load()
    }

    /**
     * Initial load.
     */
    protected fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = asUiState { loadData() }
        }
    }

    /**
     * Pull-to-refresh reload.
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _state.value = asUiState { loadData() }
            _isRefreshing.value = false
        }
    }

    /**
     * Implement to load the data.
     */
    protected abstract suspend fun loadData(): T
}
```

### Step 4: Koin Dependency Injection Setup

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/di/ViewModelModule.kt
package com.ledgerlens.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    // Dashboard
    viewModelOf(::DashboardViewModel)

    // Transactions
    viewModelOf(::TransactionsViewModel)
    viewModel { (transactionId: String) ->
        TransactionDetailViewModel(transactionId, get(), get())
    }

    // Import
    viewModelOf(::ImportViewModel)
    viewModel { (jobId: String) ->
        ImportProgressViewModel(jobId, get())
    }
    viewModel { (jobId: String) ->
        ImportReviewViewModel(jobId, get(), get())
    }

    // Review
    viewModelOf(::ReviewInboxViewModel)
    viewModel { (transactionId: String) ->
        ReviewItemViewModel(transactionId, get(), get(), get())
    }

    // Receipts
    viewModelOf(::ReceiptsViewModel)
    viewModel { (splitId: String) ->
        ReceiptSplitViewModel(splitId, get(), get(), get(), get(), get())
    }

    // Categories & Rules
    viewModelOf(::CategoriesViewModel)
    viewModel { (categoryId: String?) ->
        CategoryEditViewModel(categoryId, get())
    }
    viewModelOf(::RulesViewModel)
    viewModel { (ruleId: String?) ->
        RuleEditViewModel(ruleId, get(), get())
    }

    // Settings
    viewModelOf(::SettingsViewModel)
    viewModelOf(::DataManagementViewModel)
    viewModelOf(::AccountSettingsViewModel)
}
```

### Step 5: Composable ViewModel Integration

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/util/ViewModelComposables.kt
package com.ledgerlens.ui.util

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Collect StateFlow as Compose State with lifecycle awareness.
 */
@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T> {
    return collectAsState()
}

/**
 * Collect events with side effects.
 */
@Composable
fun CollectEvents(
    events: SharedFlow<ViewModelEvent>,
    onEvent: (ViewModelEvent) -> Unit
) {
    LaunchedEffect(Unit) {
        events.collect { event ->
            onEvent(event)
        }
    }
}

/**
 * Handle common events like snackbar and navigation.
 */
@Composable
fun HandleViewModelEvents(
    viewModel: BaseViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    CollectEvents(viewModel.events) { event ->
        when (event) {
            is ViewModelEvent.ShowSnackbar -> {
                snackbarHostState.showSnackbar(event.message)
            }
            is ViewModelEvent.NavigateTo -> {
                onNavigate(event.route)
            }
            is ViewModelEvent.NavigateBack -> {
                onNavigateBack()
            }
            is ViewModelEvent.ShowError -> {
                snackbarHostState.showSnackbar(
                    message = event.message,
                    withDismissAction = true
                )
            }
        }
    }
}

/**
 * Remember ViewModel with cleanup on dispose.
 */
@Composable
inline fun <reified T : BaseViewModel> rememberViewModel(
    crossinline factory: () -> T
): T {
    val viewModel = remember { factory() }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.onCleared()
        }
    }

    return viewModel
}
```

### Step 6: State Holder Pattern

```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/ui/util/StateHolder.kt
package com.ledgerlens.ui.util

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope

/**
 * State holder for complex screen state.
 * Use when state is UI-only and doesn't need ViewModel.
 */
@Stable
class ScreenStateHolder(
    private val coroutineScope: CoroutineScope
) {
    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun setLoading(loading: Boolean) {
        isLoading = loading
    }

    fun setError(message: String?) {
        error = message
    }

    fun clearError() {
        error = null
    }
}

@Composable
fun rememberScreenStateHolder(
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): ScreenStateHolder {
    return remember(coroutineScope) {
        ScreenStateHolder(coroutineScope)
    }
}

/**
 * Dialog state holder.
 */
@Stable
class DialogState<T> {
    var isVisible by mutableStateOf(false)
        private set

    var data by mutableStateOf<T?>(null)
        private set

    fun show(data: T? = null) {
        this.data = data
        isVisible = true
    }

    fun dismiss() {
        isVisible = false
        data = null
    }
}

@Composable
fun <T> rememberDialogState(): DialogState<T> {
    return remember { DialogState() }
}
```

### Step 7: Testing Support

```kotlin
// shared/src/commonTest/kotlin/com/ledgerlens/viewmodel/ViewModelTestUtils.kt
package com.ledgerlens.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*

/**
 * Test rule for ViewModels.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTestRule {
    private val testDispatcher = StandardTestDispatcher()

    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    fun tearDown() {
        Dispatchers.resetMain()
    }

    fun advanceUntilIdle() {
        testDispatcher.scheduler.advanceUntilIdle()
    }
}

/**
 * Test extension for StateFlow assertions.
 */
suspend fun <T> StateFlow<T>.awaitValue(
    predicate: (T) -> Boolean,
    timeoutMs: Long = 1000
): T {
    var result: T? = null
    val startTime = System.currentTimeMillis()

    while (System.currentTimeMillis() - startTime < timeoutMs) {
        val current = value
        if (predicate(current)) {
            result = current
            break
        }
        kotlinx.coroutines.delay(10)
    }

    return result ?: throw AssertionError("Timeout waiting for value")
}
```

---

## Usage Examples

### Basic ViewModel

```kotlin
class MyViewModel(
    private val repository: MyRepository
) : BaseViewModel() {

    private val _state = MutableStateFlow(MyState())
    val state: StateFlow<MyState> = _state.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                val data = repository.getData()
                _state.update { it.copy(isLoading = false, data = data) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
                emitEvent(ViewModelEvent.ShowError(e.message ?: "Error"))
            }
        }
    }
}
```

### Screen with ViewModel

```kotlin
@Composable
fun MyScreen(
    viewModel: MyViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleViewModelEvents(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        onNavigate = { /* handle navigation */ },
        onNavigateBack = { /* handle back */ }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> LoadingState()
            state.error != null -> ErrorState(state.error!!)
            else -> MyContent(state.data)
        }
    }
}
```

---

## Acceptance Criteria

- [ ] BaseViewModel provides coroutine scope
- [ ] ViewModelEvents support snackbar, navigation
- [ ] UiState wrapper handles loading/success/error
- [ ] Koin DI configured for all ViewModels
- [ ] ViewModels cleaned up on dispose
- [ ] StateFlow collected with lifecycle awareness
- [ ] Dialog state holder works
- [ ] Test utilities available

---

## Testing

### Unit Tests
```kotlin
class ViewModelTest {
    private val testRule = ViewModelTestRule()

    @BeforeTest
    fun setup() = testRule.setup()

    @AfterTest
    fun tearDown() = testRule.tearDown()

    @Test
    fun `loading state transitions correctly`() = runTest {
        val viewModel = MyViewModel(FakeRepository())

        assertEquals(true, viewModel.state.value.isLoading)

        testRule.advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.data)
    }
}
```

---

## Estimated Complexity

**Medium** - Core architecture pattern with DI integration.

