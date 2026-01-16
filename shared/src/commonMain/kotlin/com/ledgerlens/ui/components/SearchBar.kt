package com.ledgerlens.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.ledgerlens.ui.theme.ShapePatterns
import kotlinx.coroutines.delay

enum class SearchBarStyle { FILLED, OUTLINED, FLAT }

@Composable
fun SearchBar(
    query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search...",
    style: SearchBarStyle = SearchBarStyle.FILLED, enabled: Boolean = true, autoFocus: Boolean = false,
    debounceMs: Long = 300, onSearch: ((String) -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var debouncedQuery by remember { mutableStateOf(query) }

    LaunchedEffect(query) { delay(debounceMs); debouncedQuery = query }
    LaunchedEffect(autoFocus) { if (autoFocus) focusRequester.requestFocus() }

    val colors = when (style) {
        SearchBarStyle.FILLED -> TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
        SearchBarStyle.OUTLINED -> OutlinedTextFieldDefaults.colors()
        SearchBarStyle.FLAT -> TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
    }

    when (style) {
        SearchBarStyle.OUTLINED -> OutlinedTextField(
            value = query, onValueChange = onQueryChange, modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = { ClearButton(visible = query.isNotEmpty(), onClick = { onQueryChange("") }) },
            singleLine = true, enabled = enabled, shape = ShapePatterns.searchBar,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke(query); focusManager.clearFocus() }))
        else -> TextField(
            value = query, onValueChange = onQueryChange, modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = { ClearButton(visible = query.isNotEmpty(), onClick = { onQueryChange("") }) },
            singleLine = true, enabled = enabled, colors = colors, shape = ShapePatterns.searchBar,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke(query); focusManager.clearFocus() }))
    }
}

@Composable
fun SearchBarCompact(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Search...", onSearch: ((String) -> Unit)? = null) {
    SearchBar(query = query, onQueryChange = onQueryChange, modifier = modifier, placeholder = placeholder, style = SearchBarStyle.FLAT, onSearch = onSearch)
}

@Composable
fun TransactionSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier, onSearch: ((String) -> Unit)? = null) {
    SearchBar(query = query, onQueryChange = onQueryChange, modifier = modifier, placeholder = "Search transactions, merchants...", style = SearchBarStyle.FILLED, onSearch = onSearch)
}

@Composable
private fun ClearButton(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        IconButton(onClick = onClick) { Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
