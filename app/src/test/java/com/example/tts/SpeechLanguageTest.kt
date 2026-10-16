package com.example.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechLanguageTest {
  @Test fun autoRecognizesScripts() {
    assertEquals("km-KH", SpeechLanguage.resolve("សួស្តី", "auto", "en-US"))
    assertEquals("th-TH", SpeechLanguage.resolve("สวัสดี", "auto", "en-US"))
    assertEquals("ja-JP", SpeechLanguage.resolve("こんにちは", "auto", "en-US"))
    assertEquals("ko-KR", SpeechLanguage.resolve("안녕하세요", "auto", "en-US"))
    assertEquals("ar-SA", SpeechLanguage.resolve("مرحبا", "auto", "en-US"))
    assertEquals("en-US", SpeechLanguage.resolve("Hello world", "auto", "en-US"))
  }

  @Test fun explicitLanguageWinsForSharedLatinScript() {
    assertEquals("fr-FR", SpeechLanguage.resolve("Bonjour le monde", "fr-FR", "en-US"))
    assertEquals("es-ES", SpeechLanguage.resolve("Hola", "auto", "es-ES"))
    assertEquals("de-DE", SpeechLanguage.resolve("Hallo", "de-DE", "en-US"))
  }
}
