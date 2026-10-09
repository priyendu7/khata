package com.openhand.khata.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openhand.khata.core.data.CategoryRepository
import com.openhand.khata.core.data.DuplicateCategoryNameException
import com.openhand.khata.core.data.RenameResult
import com.openhand.khata.core.data.TagRepository
import com.openhand.khata.core.model.Category
import com.openhand.khata.core.model.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

@HiltViewModel
class CategoriesViewModel @Inject constructor(private val categories: CategoryRepository) :
    ViewModel() {
    val all: StateFlow<List<Category>?> = categories.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** The editor refuses a taken name first; one taken meanwhile is simply not saved. */
    fun save(category: Category, defaultNames: Map<String, String>) {
        viewModelScope.launch {
            try {
                categories.save(category, defaultNames)
            } catch (_: DuplicateCategoryNameException) {
            }
        }
    }

    fun setArchived(category: Category, archived: Boolean) {
        viewModelScope.launch { categories.setArchived(category.id, archived) }
    }
}

@HiltViewModel
class TagsViewModel @Inject constructor(private val tags: TagRepository) : ViewModel() {
    val all: StateFlow<List<Tag>?> = tags.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** [onResult] runs on the main thread; a taken name comes back so the UI can offer a merge. */
    fun rename(tag: Tag, newName: String, onResult: (RenameResult) -> Unit) {
        viewModelScope.launch { onResult(tags.rename(tag.id, newName)) }
    }

    fun merge(from: Tag, into: Tag) {
        viewModelScope.launch { tags.merge(from.id, into.id) }
    }

    fun delete(tag: Tag) {
        viewModelScope.launch { tags.delete(tag.id) }
    }
}
