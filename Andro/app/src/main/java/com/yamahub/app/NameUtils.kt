package com.yamahub.app

fun displayName(raw: String): String =
    raw.replace('_', ' ').trim()