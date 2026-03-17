package com.yehyun.whatshouldiweartoday.ui.closet

import com.yehyun.whatshouldiweartoday.R

enum class ShoppingPlatform(
    val displayName: String,
    val iconRes: Int,
    val loginUrl: String,
    val purchaseHistoryUrl: String,
    val loginUrlPatterns: List<String>,
    val supportsRedirect: Boolean = true
) {
    COUPANG(
        "쿠팡",
        R.drawable.shopping_coupang,
        "https://login.coupang.com/login/login.pang?rtnUrl=https%3A%2F%2Fmc.coupang.com%2Fssr%2Fmobile%2Forder%2Flist",
        "https://mc.coupang.com/ssr/mobile/order/list",
        listOf("login.coupang.com")
    ),
    MUSINSA(
        "무신사",
        R.drawable.shopping_musinsa,
        "https://www.musinsa.com/mypage",
        "https://www.musinsa.com/order/order-list",
        listOf("musinsa.com/member/login", "musinsa.com/auth", "musinsa.com/login"),
        supportsRedirect = false
    ),
    HIVER(
        "하이버",
        R.drawable.shopping_hiver,
        "https://member.hiver.co.kr/login?redirect=%2Fmypage%2Forders",
        "https://member.hiver.co.kr/mypage/orders",
        listOf("member.hiver.co.kr/login")
    ),
    NAVER(
        "네이버스토어",
        R.drawable.shopping_naver,
        "https://nid.naver.com/nidlogin.login?mode=form&url=https://shopping.naver.com/my/order",
        "https://shopping.naver.com/my/order",
        listOf("nid.naver.com/nidlogin")
    ),
    ABLY(
        "에이블리",
        R.drawable.shopping_ably,
        "https://m.a-bly.com/login?redirect=/orders",
        "https://m.a-bly.com/orders",
        listOf("a-bly.com/login")
    ),
    CM29(
        "29CM",
        R.drawable.shopping_29cm,
        "https://m.wconcept.co.kr/Member/Login?rUrl=/MyPage/MyOrderList",
        "https://m.wconcept.co.kr/MyPage/MyOrderList",
        listOf("wconcept.co.kr/Member/Login")
    ),
    GOOGLE(
        "구글",
        R.drawable.shopping_google,
        "https://www.google.com",
        "https://www.google.com",
        emptyList()
    );

    fun isLoginPage(url: String?): Boolean {
        if (url == null || loginUrlPatterns.isEmpty()) return false
        return loginUrlPatterns.any { pattern -> url.contains(pattern, ignoreCase = true) }
    }
}
