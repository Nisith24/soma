package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Soma", appName)
  }

  @Test
  fun `test standard vs base64 explanation parsing`() {
    val jsonInputPlain = """
    [
      {
        "subject": "Anatomy",
        "topic": "White Matter",
        "question": "What is the structure?",
        "options": ["Myelinated", "Unmyelinated"],
        "correct_answer": "Myelinated",
        "explanation": "White Matter of Cerebrum contains myelinated nerve fibers."
      }
    ]
    """.trimIndent()

    val jsonInputBase64 = """
    [
      {
        "subject": "Anatomy",
        "topic": "White Matter",
        "question": "What is the structure base64?",
        "options": ["Myelinated", "Unmyelinated"],
        "correct_answer": "Myelinated",
        "explanation": "data:text/plain;base64,V2hpdGUgTWF0dGVyIG9mIENlcmVicnVtIGNvbnRhaW5zIG15ZWxpbmF0ZWQgbmVydmUgZmliZXJzLg=="
      }
    ]
    """.trimIndent()

    val streamPlain = java.io.ByteArrayInputStream(jsonInputPlain.toByteArray(Charsets.UTF_8))
    val streamBase64 = java.io.ByteArrayInputStream(jsonInputBase64.toByteArray(Charsets.UTF_8))

    val parsedPlain = JsonStreamParser.parseMultiple(listOf("plain.json" to streamPlain))
    val parsedBase64 = JsonStreamParser.parseMultiple(listOf("base64.json" to streamBase64))

    assertEquals(1, parsedPlain.size)
    assertEquals("White Matter of Cerebrum contains myelinated nerve fibers.", parsedPlain[0].explanation)

    assertEquals(1, parsedBase64.size)
    assertEquals("White Matter of Cerebrum contains myelinated nerve fibers.", parsedBase64[0].explanation)
  }

  @Test
  fun `test viewModel initialization`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val app = context.applicationContext as android.app.Application
    val viewModel = McqViewModel(app)
    assertNotNull(viewModel)
  }
}
