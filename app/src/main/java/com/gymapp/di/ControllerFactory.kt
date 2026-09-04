package com.gymapp.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

fun <Controller : ViewModel> controllerFactory(
    create: () -> Controller,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}

