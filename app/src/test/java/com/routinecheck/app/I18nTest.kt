package com.routinecheck.app

import com.routinecheck.app.domain.AppLanguage
import com.routinecheck.app.domain.DayMask
import com.routinecheck.app.domain.I18n
import com.routinecheck.app.domain.TimeText
import com.routinecheck.app.domain.tr
import com.routinecheck.app.domain.tr2
import java.time.DayOfWeek
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.8.0 영어 지원: 번역 · 자리표시자 · 언어 선택 */
class I18nTest {

    @After
    fun backToKorean() = I18n.apply(AppLanguage.KOREAN, "ko")

    @Test
    fun korean_isDefault_andFormatsPlaceholders() {
        I18n.apply(AppLanguage.KOREAN, "en")
        assertEquals("메시지 3개가 더 있어요", tr("메시지 {0}개가 더 있어요", 3))
        assertEquals("오전 7:05", TimeText.display("07:05"))
        assertEquals("월", DayMask.shortName(DayOfWeek.MONDAY))
    }

    @Test
    fun english_translatesAndHandlesPluralAndMonth() {
        I18n.apply(AppLanguage.ENGLISH, "ko")
        assertEquals("Settings", tr("설정"))
        assertEquals("1 more message", tr("메시지 {0}개가 더 있어요", 1))
        assertEquals("2 more messages", tr("메시지 {0}개가 더 있어요", 2))
        assertEquals("Memo for Nov 11 (Wed)", tr("{0}월 {1}일 ({2}) 메모", 11, 11, "Wed"))
        assertEquals("7:05 AM", TimeText.display("07:05"))
        assertEquals("12:30 PM", TimeText.display("12:30"))
        assertEquals("Mon", DayMask.shortName(DayOfWeek.MONDAY))
        assertEquals("Every day", DayMask.label(DayMask.ALL))
    }

    @Test
    fun missingTranslation_fallsBackToKorean() {
        I18n.apply(AppLanguage.ENGLISH, "ko")
        assertEquals("사전에 없는 문구", tr("사전에 없는 문구"))
        assertEquals("Sun", tr2("일", "Sun"))
    }

    @Test
    fun systemLanguage_koreanPhoneIsKorean_othersEnglish() {
        I18n.apply(AppLanguage.SYSTEM, "ko")
        assertFalse(I18n.isEnglish)
        I18n.apply(AppLanguage.SYSTEM, "ja")
        assertTrue(I18n.isEnglish)
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
    }
}
