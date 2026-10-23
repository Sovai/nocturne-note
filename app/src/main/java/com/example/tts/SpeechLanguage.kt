package com.example.tts

import java.util.Locale

/** Unicode-script detection for Auto; explicit locale selection handles ambiguous Latin text. */
object SpeechLanguage {
  fun resolve(text: String, setting: String, deviceLocale: String = Locale.getDefault().toLanguageTag()): String {
    if (setting != "auto") return setting
    val cp = text.codePoints().toArray().firstOrNull { Character.isLetter(it) }
      ?: return preferredDeviceLocale(deviceLocale)
    return when (Character.UnicodeScript.of(cp)) {
      Character.UnicodeScript.KHMER -> "km-KH"
      Character.UnicodeScript.THAI -> "th-TH"
      Character.UnicodeScript.LAO -> "lo-LA"
      Character.UnicodeScript.MYANMAR -> "my-MM"
      Character.UnicodeScript.HANGUL -> "ko-KR"
      Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> "ja-JP"
      Character.UnicodeScript.HAN -> "zh-CN"
      Character.UnicodeScript.ARABIC -> "ar-SA"
      Character.UnicodeScript.HEBREW -> "he-IL"
      Character.UnicodeScript.DEVANAGARI -> "hi-IN"
      Character.UnicodeScript.BENGALI -> "bn-IN"
      Character.UnicodeScript.TAMIL -> "ta-IN"
      Character.UnicodeScript.TELUGU -> "te-IN"
      Character.UnicodeScript.GUJARATI -> "gu-IN"
      Character.UnicodeScript.GURMUKHI -> "pa-IN"
      Character.UnicodeScript.KANNADA -> "kn-IN"
      Character.UnicodeScript.MALAYALAM -> "ml-IN"
      Character.UnicodeScript.SINHALA -> "si-LK"
      Character.UnicodeScript.GREEK -> "el-GR"
      Character.UnicodeScript.CYRILLIC -> when (deviceLocale.substringBefore('-')) {
        "uk" -> "uk-UA"
        "bg" -> "bg-BG"
        else -> "ru-RU"
      }
      Character.UnicodeScript.ARMENIAN -> "hy-AM"
      Character.UnicodeScript.GEORGIAN -> "ka-GE"
      Character.UnicodeScript.ETHIOPIC -> "am-ET"
      else -> preferredDeviceLocale(deviceLocale)
    }
  }

  private fun preferredDeviceLocale(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    if (locale.language.isBlank() || locale.language == "und") return "en-US"
    return if (locale.country.isBlank()) {
      when (locale.language) {
        "km" -> "km-KH"
        "en" -> "en-US"
        "fr" -> "fr-FR"
        "es" -> "es-ES"
        "pt" -> "pt-BR"
        "zh" -> "zh-CN"
        else -> locale.language + "-" + locale.language.uppercase(Locale.ROOT)
      }
    } else locale.toLanguageTag()
  }
}
