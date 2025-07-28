package com.yehyun.whatshouldiweartoday.util

open class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set

    /**
     * 아직 처리되지 않은 경우에만 콘텐츠를 반환하고, 처리된 것으로 표시합니다.
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * 처리 여부와 관계없이 항상 콘텐츠를 반환합니다.
     */
    fun peekContent(): T = content
}