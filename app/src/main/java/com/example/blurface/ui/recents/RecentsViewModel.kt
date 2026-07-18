package com.example.blurface.ui.recents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blurface.data.history.RecentEditsStore
import com.example.blurface.domain.model.EditType
import com.example.blurface.domain.model.RecentEdit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

enum class DateFilter(val label: String) {
    TODAY("Today"), THIS_WEEK("This Week"), THIS_MONTH("This Month")
}

enum class SortOption(val label: String) {
    RECENTLY_UPDATED("Recently Updated"), OLDEST_FIRST("Oldest First"), NAME("Name")
}

class RecentsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RecentEditsStore(application)

    private val _allEdits = MutableStateFlow<List<RecentEdit>>(emptyList())

    private val _typeFilter = MutableStateFlow<EditType?>(null) // null = All
    val typeFilter: StateFlow<EditType?> = _typeFilter.asStateFlow()

    private val _dateFilter = MutableStateFlow(DateFilter.THIS_WEEK)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.RECENTLY_UPDATED)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val visibleEdits: StateFlow<List<RecentEdit>> =
        combine(_allEdits, _typeFilter, _dateFilter, _sortOption, _searchQuery) { all, type, date, sort, query ->
            all.filter { type == null || it.editType == type }
                .filter { withinDateRange(it.timestampMillis, date) }
                .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
                .let { list ->
                    when (sort) {
                        SortOption.RECENTLY_UPDATED -> list.sortedByDescending { it.timestampMillis }
                        SortOption.OLDEST_FIRST -> list.sortedBy { it.timestampMillis }
                        SortOption.NAME -> list.sortedBy { it.title.lowercase() }
                    }
                }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
    }

    fun refresh() {
        _allEdits.value = store.getAll()
    }

    fun setTypeFilter(type: EditType?) {
        _typeFilter.value = type
    }

    fun setDateFilter(filter: DateFilter) {
        _dateFilter.value = filter
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun delete(edit: RecentEdit) {
        runCatching {
            getApplication<Application>().contentResolver.delete(
                android.net.Uri.parse(edit.mediaUri), null, null
            )
        }
        store.delete(edit.id)
        refresh()
    }

    private fun withinDateRange(timestampMillis: Long, filter: DateFilter): Boolean {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        val boundary = when (filter) {
            DateFilter.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            DateFilter.THIS_WEEK -> now - 7L * 24 * 60 * 60 * 1000
            DateFilter.THIS_MONTH -> now - 30L * 24 * 60 * 60 * 1000
        }
        return timestampMillis >= boundary
    }
}