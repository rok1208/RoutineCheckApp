package com.routinecheck.app.domain

/** 웹사이트 주소 정리 (순수 로직 — 단위 테스트 대상) */
object LinkUtils {

    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://")
    private val WEB_SCHEMES = setOf("http", "https")

    /**
     * 사용자가 입력한 주소를 열 수 있는 형태로 바꿉니다.
     *  "naver.com" → "https://naver.com",  "http://a.com" → 그대로
     * 공백이 있거나 점(.)이 없는 등 주소로 보기 어려우면 null
     * v0.7.1: http·https 웹 주소만 허용 (file://, intent://, content:// 등은 거부)
     *         "user@host" 형태(아이디가 섞인 주소)도 거부 — 실제 접속 주소를 헷갈리게 만드는 방식이라
     */
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val withScheme = if (SCHEME.containsMatchIn(text)) text else "https://$text"
        if (!isWebUrl(withScheme)) return null
        val authority = withScheme.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        if ('@' in authority) return null
        val host = hostOf(withScheme) ?: return null
        if (!host.contains('.') && host != "localhost") return null
        return withScheme
    }

    /** http:// 또는 https:// 로 시작하는 웹 주소인지 (대소문자 무시) */
    fun isWebUrl(url: String): Boolean {
        val scheme = SCHEME.find(url.trim())?.groupValues?.get(1) ?: return false
        return scheme.lowercase() in WEB_SCHEMES
    }

    /** 입력에 http·https 가 아닌 스킴이 붙어 있는지 (오류 문구를 구분하려고) */
    fun hasUnsupportedScheme(input: String): Boolean {
        val scheme = SCHEME.find(input.trim())?.groupValues?.get(1) ?: return false
        return scheme.lowercase() !in WEB_SCHEMES
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
