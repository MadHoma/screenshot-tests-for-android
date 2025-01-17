package com.facebook.testing.screenshot

import android.graphics.Bitmap


/** Builds all the information related to a screenshot.  */
interface RecordBuilder {
  /**
   * Set a name (identifier) for the screenshot. If you skip the name a name will be generated based
   * on the Test class and Test method name this is being run from. That means if you have multiple
   * screenshots in the same test, then you have to explicitly specify names to disambiguate.
   */
  fun setName(name: String): RecordBuilder

  /**
   * Set a long description of the what the screenshot is about.
   *
   *
   * This will be shown as part of the report, and in general it can help document a screenshot
   * if you're using it as part of an external tooling.
   */
  fun setDescription(description: String): RecordBuilder

  /**
   * Add extra metadata about this screenshots.
   *
   *
   * There will be no semantic information associated with this metadata, but we'll try to
   * provide this as debugging information whenever you're viewing screenshots.
   */
  fun addExtra(key: String, value: String): RecordBuilder

  /** Groups similar or identical screenshots which makes it easier to compare.  */
  fun setGroup(groupName: String): RecordBuilder

  /**
   * Enables or disables extra information attached to the metadata generated related to
   * accessibility information.
   *
   * @param includeAccessibilityInfo
   */
  fun setIncludeAccessibilityInfo(includeAccessibilityInfo: Boolean): RecordBuilder

  /**
   * Stops the recording and returns the generated bitmap, possibly compressed.
   *
   *
   * You cannot call this after record(), nor can you call record() after this call.
   */
  fun getBitmap(): Bitmap

  /**
   * Set the maximum number of pixels this screenshot should produce. Producing any number higher
   * will throw an exception.
   *
   * @param maxPixels Maximum number of pixels this screenshot should produce. <= 0 for no limit.
   */
  fun setMaxPixels(maxPixels: Long): RecordBuilder

  /** Finish the recording.  */
  fun record()
}
