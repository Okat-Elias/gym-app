package com.gymapp.model.usecase

import java.math.BigDecimal

data class SetInput(val weightGrams: Long, val repetitions: Int, val rpe: Double?)

class ParseSetInput {
    operator fun invoke(weight: String, repetitions: String, rpe: String): SetInput? {
        val normalized = weight.trim().replace(',', '.')
        if (!normalized.matches(Regex("\\d+(\\.\\d{1,3})?"))) return null
        val kilograms = normalized.toBigDecimalOrNull() ?: return null
        if (kilograms < BigDecimal.ZERO || kilograms > BigDecimal(1000)) return null
        val count = repetitions.trim().toIntOrNull()?.takeIf { it in 1..1000 } ?: return null
        val effort = if (rpe.isBlank()) null else {
            rpe.trim().replace(',', '.').toDoubleOrNull()
                ?.takeIf { it.isFinite() && it in 1.0..10.0 } ?: return null
        }
        return SetInput(kilograms.movePointRight(3).longValueExact(), count, effort)
    }
}
