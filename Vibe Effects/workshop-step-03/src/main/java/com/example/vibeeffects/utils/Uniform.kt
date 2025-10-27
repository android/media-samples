package com.example.vibeeffects.utils

open class Uniform<T>(val name: String, val type: String, val uiName: String, val uiDescription: String, val defaultValue: T?) {
    private var value: T? = defaultValue
    private val listeners: MutableList<OnChangeListener<T?>> = ArrayList<OnChangeListener<T?>>()

    /**
     * Interface for a listener to be notified when the uniform's value changes.
     * @param <T> The data type of the uniform's value.
    </T> */
    interface OnChangeListener<T> {
        fun onValueChanged(newValue: T?)
    }

    /**
     * Returns the current value of the uniform.
     */
    fun getValue(): T? {
        return value
    }

    /**
     * Sets the value of the uniform and notifies listeners of the change.
     * This method should be called by UI handlers when the user interacts with a UI element.
     * @param newValue The new value to set.
     */
    fun setValue(newValue: T?) {
        // Check for actual change to avoid redundant updates.
        if (this.value == null && newValue == null) {
            return
        }
        if (this.value != null && this.value == newValue) {
            return
        }

        this.value = newValue
        for (listener in listeners) {
            listener.onValueChanged(newValue)
        }
    }

    /**
     * Adds a listener to be notified of value changes.
     * @param listener The listener to add.
     */
    fun addOnChangeListener(listener: OnChangeListener<T?>?) {
        listeners.add(listener!!)
    }

    /**
     * Removes a previously added listener.
     * @param listener The listener to remove.
     */
    fun removeOnChangeListener(listener: OnChangeListener<T?>?) {
        listeners.remove(listener)
    }
}