package com.gymapp.controller

import kotlinx.coroutines.flow.StateFlow

/** Contract shared by MVC controllers exposed to a Compose view. */
interface ScreenController<State, Action> {
    val state: StateFlow<State>
    fun onAction(action: Action)
}

