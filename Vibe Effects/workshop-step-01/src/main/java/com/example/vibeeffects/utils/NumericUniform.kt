package com.example.vibeeffects.utils

/**
 * A specialized Uniform for NUMERIC types that adds range (min/max) support.
 * @param <T> The data type, which MUST be a subclass of Number (e.g., Float, Integer).
</T> */
class NumericUniform<T : Number>(
    name: String,
    type: String,
    uiName: String,
    uiDescription: String,
    defaultValue: T?,
    val range: Range<T>? = null
) : Uniform<T>(
    name, type, uiName, uiDescription, defaultValue
)