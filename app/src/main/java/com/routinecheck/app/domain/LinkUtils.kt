package com.routinecheck.app.domain

/** 웹사이트 주소 정리 (순수 로직 — 단위 테스트 대상) */
object LinkUtils {

    /**
     * 사용자가 입력한 주소를 열 수 있는 형태로 바꿉니다.
     *  "naver.com" → "https://naver.com",  "http://a.com" → 그대로
     * 공백이 있거나 점(.)이 없는 등 주소로 보기 어려우면 null
     */
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(text)) text else "https://$text"
        val host = hostOf(withScheme) ?: return null
        if (!host.contains('.') && host != "localhost") return null
        return withScheme
    }

    /** "https://m.naver.com/news?x=1" → "m.naver.com" */
    fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", missingDelimiterValue = url)
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringAfter('@').substringBefore(':')
        return host.ifBlank { null }
    }

    /** 이름을 비워 두면 주소에서 이름을 만듭니다: "https://www.naver.com" → "naver.com" */
    fun defaultTitle(url: String): String = (hostOf(url) ?: url).removePrefix("www.")
}
