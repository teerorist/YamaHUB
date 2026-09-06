package com.yamahub.app

data class InputCfgItem(
    val inNum: Int,
    val mode: Int,
    val outNum: Int,
    val name: String,
    val outputEnabled: Boolean = false,
    val outputNum: Int = outNum,
    val functionId: Int = 0,
    val outSecondary: Int = 0,
    val isFixed: Boolean = false,
    val isOutLocked: Boolean = false
)

fun InputCfgItem.displayName(): String =
    name.replace('_', ' ').trim().ifBlank { "IN_$inNum" }
