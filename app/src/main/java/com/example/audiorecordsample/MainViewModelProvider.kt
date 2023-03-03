package com.example.audiorecordsample

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.audiorecordsample.repository.Repository

class MainViewModelProvider(
    private val app: Application,
    private val repository: Repository)
    : ViewModelProvider.AndroidViewModelFactory(app) {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(app, repository) as T
    }
}