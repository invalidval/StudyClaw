package com.zlearn.ui.question.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zlearn.domain.usecase.QuestionUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuestionViewModel @Inject constructor(
    private val useCases: QuestionUseCases
) : ViewModel() {
    // TODO: 实现题目相关的UI状态和逻辑
}

