package com.example.tweakly.ui.navigation

import androidx.lifecycle.ViewModel
import com.example.tweakly.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavHelperViewModel @Inject constructor(
    val settingsRepo: SettingsRepository
) : ViewModel()
