package com.yehyun.whatshouldiweartoday.ui

/**
 * 하단 네비게이션 탭이 재선택되었을 때의 동작을 정의하는 인터페이스
 */
interface OnTabReselectedListener {
    /**
     * 탭이 재선택되었을 때 이 함수가 호출됩니다.
     * 프래그먼트는 이 함수를 구현하여, 저장 여부 확인 등의 자체 로직을 수행할 수 있습니다.
     */
    fun onTabReselected()
}