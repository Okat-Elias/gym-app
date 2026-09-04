package com.gymapp.model.domain

/** Unit selected for displaying and entering a weight. Persisted weights remain in grams. */
enum class WeightUnit(val symbol: String) {
    KILOGRAMS("kg"),
    POUNDS("lb"),
}
