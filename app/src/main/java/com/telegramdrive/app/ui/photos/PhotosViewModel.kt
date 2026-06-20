package com.telegramdrive.app.ui.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telegramdrive.app.data.local.entity.FileEntity
import com.telegramdrive.app.data.repository.AccountRepository
import com.telegramdrive.app.data.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val fileRepo: FileRepository,
    private val accountRepo: AccountRepository
) : ViewModel() {

    val photos: StateFlow<List<FileEntity>> = accountRepo.observeActive()
        .flatMapLatest { acc ->
            if (acc == null) flowOf(emptyList())
            else fileRepo.observeMedia(acc.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
