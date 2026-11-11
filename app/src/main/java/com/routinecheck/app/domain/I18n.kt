package com.routinecheck.app.domain

/**
 * 앱 언어 (v0.8.0) — 설정의 "언어" 항목
 *  SYSTEM: 휴대폰 언어가 한국어면 한국어, 그 밖에는 영어
 */
enum class AppLanguage(val code: String) {
    SYSTEM("system"), KOREAN("ko"), ENGLISH("en");

    companion object {
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/**
 * 화면 문구 번역 (순수 로직 — 단위 테스트 대상)
 *
 * 코드에는 한국어 문구를 그대로 두고 tr("한국어 문구") 로 감쌉니다.
 * 영어일 때는 EnStrings 사전에서 같은 문구를 찾아 바꾸고, 없으면 한국어를 그대로 씁니다.
 *
 * 자리표시자: {0}, {1} … (영어에서 순서를 바꿀 수 있음)
 *  - {0:mon}        숫자 월 → 영어 월 약어 (11 → Nov)
 *  - {0:day|days}   숫자가 1이면 앞, 아니면 뒤 (영어 단수·복수)
 *
 * 언어는 앱 프로세스가 시작될 때 한 번 정해집니다. 설정에서 언어를 바꾸면 앱을 다시 시작해서
 * 미리 만들어 둔 목록(배지 이름 등)까지 모두 새 언어로 바뀌게 합니다.
 */
object I18n {
    @Volatile
    var isEnglish: Boolean = false
        private set

    /** @param systemLanguage 휴대폰 언어 코드 (Locale.getDefault().language) */
    fun apply(setting: AppLanguage, systemLanguage: String) {
        isEnglish = when (setting) {
            AppLanguage.KOREAN -> false
            AppLanguage.ENGLISH -> true
            AppLanguage.SYSTEM -> systemLanguage != "ko"
        }
    }

    private val PLACEHOLDER = Regex("""\{(\d+)(?::([^}]*))?\}""")
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun format(template: String, args: Array<out Any?>): String {
        if (args.isEmpty()) return template
        return PLACEHOLDER.replace(template) { m ->
            val index = m.groupValues[1].toInt()
            val arg = args.getOrNull(index)
            val spec = m.groupValues[2]
            when {
                spec.isEmpty() -> arg.toString()
                spec == "mon" -> (arg as? Number)?.toInt()?.let { MONTHS.getOrNull(it - 1) } ?: arg.toString()
                '|' in spec -> {
                    val (one, many) = spec.split('|', limit = 2)
                    if ((arg as? Number)?.toLong() == 1L) one else many
                }
                else -> arg.toString()
            }
        }
    }

    /** 영어 사전에 빠진 문구가 있는지 테스트에서 확인할 때 사용 */
    fun hasEnglish(ko: String): Boolean = EnStrings.MAP.containsKey(ko)
}

/** 한국어 문구를 현재 언어로. 영어 사전에 없으면 한국어 그대로 */
fun tr(ko: String, vararg args: Any?): String =
    I18n.format(if (I18n.isEnglish) EnStrings.MAP[ko] ?: ko else ko, args)

/** 같은 한국어 문구가 다른 뜻으로 쓰일 때(예: "월" = 월요일 / 달) 영어를 직접 지정 */
fun tr2(ko: String, en: String, vararg args: Any?): String =
    I18n.format(if (I18n.isEnglish) en else ko, args)
