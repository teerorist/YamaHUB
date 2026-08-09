package com.yamahub.app

data class InputCfgItem(
    val inNum: Int,
    val mode: Int,
    val outNum: Int,
    val name: String
)

fun InputCfgItem.displayName(): String =
    name.replace('_', ' ').trim().ifBlank { "IN_$inNum" }
