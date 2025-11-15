// file: app/src/main/java/com/FEdev/i221279_i220809/models/User.kt
package com.FEdev.i221279_i220809

data class User(
    val uid: String = "",
    val name: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val nameLower: String = ""
)