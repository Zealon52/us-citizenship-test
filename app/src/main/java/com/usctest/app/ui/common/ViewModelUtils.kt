package com.usctest.app.ui.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Small helper for manual-DI ViewModel creation, avoiding a Hilt dependency for this app's size. */
@Composable
inline fun <reified VM : ViewModel> rememberViewModel(crossinline creator: () -> VM): VM {
    val factory = viewModelFactory { initializer { creator() } }
    return viewModel(factory = factory)
}
