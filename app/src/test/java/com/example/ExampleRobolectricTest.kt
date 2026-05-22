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
  fun `test medical explanations parsing`() {
    val mockQuestions = getDefaultMockQuestions()
    for (q in mockQuestions) {
      val exp = q.explanation
      if (exp != null) {
        val (summary, optionBreakdowns) = parseMedicalExplanation(exp)
        assertNotNull(summary)
        val blocks = parseHtmlToBlocks(summary)
        assertNotNull(blocks)
        
        optionBreakdowns.forEach { breakdown ->
          val breakdownBlocks = parseHtmlToBlocks(breakdown.content)
          assertNotNull(breakdownBlocks)
        }
      }
    }
  }

  @Test
  fun `test viewModel initialization`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val app = context.applicationContext as android.app.Application
    val viewModel = McqViewModel(app)
    assertNotNull(viewModel)
  }
}
