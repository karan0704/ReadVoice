package com.readvoice.app.ui.common

import com.readvoice.app.domain.model.AppError
import com.readvoice.app.domain.model.AppResult
import com.readvoice.app.domain.model.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a list screen needs to draw itself. */
data class ListUiState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val canLoadMore: Boolean = false
)

/**
 * Loads a cursor-paged list one page at a time. A refresh replaces the list
 * and cancels whatever was loading; loading more appends the next page.
 */
class PagedLoader<T>(
    private val scope: CoroutineScope,
    private val fetch: suspend (after: String?) -> AppResult<Page<T>>
) {
    private val _state = MutableStateFlow(ListUiState<T>())
    val state: StateFlow<ListUiState<T>> = _state

    private var cursor: String? = null
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job = scope.launch { load(replace = true) }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || !current.canLoadMore) return
        job = scope.launch { load(replace = false) }
    }

    private suspend fun load(replace: Boolean) {
        _state.update { it.copy(isLoading = true, error = null) }
        when (val result = fetch(if (replace) null else cursor)) {
            is AppResult.Success -> {
                cursor = result.value.nextCursor
                _state.update { previous ->
                    ListUiState(
                        items = if (replace) result.value.items else previous.items + result.value.items,
                        canLoadMore = cursor != null
                    )
                }
            }
            is AppResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
        }
    }
}
