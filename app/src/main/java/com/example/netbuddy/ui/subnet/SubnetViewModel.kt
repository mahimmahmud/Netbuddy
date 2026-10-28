package com.example.netbuddy.ui.subnet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.netbuddy.domain.model.SubnetCalculator
import com.example.netbuddy.domain.model.SubnetResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SubnetUiState(
    val address: String = "",
    val prefix: String = "24",
    val result: SubnetResult? = null,
    val error: String? = null
)

class SubnetViewModel(private val saved: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(SubnetUiState(
        address = saved["address"] ?: "", prefix = saved["prefix"] ?: "24"))
    val state = mutableState.asStateFlow()

    fun setAddress(value: String) {
        saved["address"] = value
        mutableState.value = mutableState.value.copy(address = value, result = null, error = null)
    }

    fun setPrefix(value: String) {
        saved["prefix"] = value
        mutableState.value = mutableState.value.copy(prefix = value, result = null, error = null)
    }

    fun calculate() {
        val current = mutableState.value
        try {
            val raw = current.prefix.trim().removePrefix("/")
            require(raw.isNotEmpty() && raw.all { it in '0'..'9' }) { "Enter a CIDR prefix from 0 to 32." }
            val prefix = raw.toIntOrNull()
            require(prefix != null && prefix in 0..32) { "CIDR prefix must be between 0 and 32." }
            mutableState.value = current.copy(result = SubnetCalculator.calculate(current.address, prefix), error = null)
        } catch (error: IllegalArgumentException) {
            mutableState.value = current.copy(result = null, error = error.message ?: "Invalid input.")
        }
    }

    fun reset() {
        saved["address"] = ""
        saved["prefix"] = "24"
        mutableState.value = SubnetUiState()
    }
}
