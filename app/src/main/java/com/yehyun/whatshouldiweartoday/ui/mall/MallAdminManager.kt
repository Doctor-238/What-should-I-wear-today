package com.yehyun.whatshouldiweartoday.ui.mall

object MallAdminManager {
    private const val ADMIN_ID = "root"
    private const val ADMIN_PW = "pass"

    var isLoggedIn = false
        private set

    fun login(id: String, pw: String): Boolean {
        return if (id == ADMIN_ID && pw == ADMIN_PW) {
            isLoggedIn = true
            true
        } else false
    }

    fun logout() {
        isLoggedIn = false
    }
}
