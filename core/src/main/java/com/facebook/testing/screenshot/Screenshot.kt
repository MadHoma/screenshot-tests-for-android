package com.facebook.testing.screenshot

import android.app.Activity
import android.view.View
import com.facebook.testing.screenshot.internal.ScreenshotImpl

/**
 * A testing tool for taking a screenshot during an Activity instrumentation test. This is really
 * useful while manually investigating how the rendering looks like after setting up some complex
 * set of conditions in the test. (Which might be hard to manually recreate)
 *
 *
 * Eventually we can use this to catch rendering changes, with very little work added to the
 * instrumentation test.
 */
object Screenshot {
  /**
   * Take a snapshot of an already measured and layout-ed view. See adb-logcat for how to pull the
   * screenshot.
   *
   *
   * This method is thread safe.
   */
  fun snap(measuredView: View?): RecordBuilder {
    return ScreenshotImpl.getInstance().snap(measuredView)
  }

  /**
   * Take a snapshot of the activity and store it with the the testName. See the adb-logcat for how
   * to pull the screenshot.
   *
   *
   * This method is thread safe.
   */
  fun snapActivity(activity: Activity?): RecordBuilder {
    return ScreenshotImpl.getInstance().snapActivity(activity)
  }

  /** @return The largest amount of pixels we'll capture, otherwise an exception will be thrown.
   */
  val maxPixels: Long
    get() = ScreenshotImpl.getMaxPixels()
}
