package com.example.tweakly.ui.onboarding

import androidx.lifecycle.ViewModel
import com.example.tweakly.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(val settings: SettingsRepository) : ViewModel()
