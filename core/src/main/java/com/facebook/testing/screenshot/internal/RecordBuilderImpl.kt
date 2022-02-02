package com.facebook.testing.screenshot.internal

import android.graphics.Bitmap
import android.view.View
import com.facebook.testing.screenshot.RecordBuilder
import java.io.File
import java.nio.charset.Charset
import java.util.*


/**
 * A builder for all the metadata associated with a screenshot.
 *
 *
 * Use Screenshot#snap() or Screenshot#snapActivity() to get an instance of this, and commit the
 * record with #record().
 */
class RecordBuilderImpl internal constructor(private val mScreenshotImpl: ScreenshotImpl) :
  RecordBuilder {
  private val mExtras: MutableMap<String, String> = HashMap()
  var description: String? = null
    private set
  private var mName: String? = null
  var testClass: String? = null
    private set
  var testName: String? = null
    private set

  /** Get's any error that was encountered while creating the screenshot.  */
  var error: String? = null
    private set
  var group: String? = null
    private set
  var includeAccessibilityInfo = true
    private set
  var tiling = Tiling(1, 1)
    private set
  var view: View? = null
    private set

  /** @return The maximum number of pixels that is expected to be produced by this screenshot
   */
  var maxPixels = DEFAULT_MAX_PIXELS
    private set

  /** @inherit   */
  override fun setDescription(description: String): RecordBuilderImpl {
    this.description = description
    return this
  }

  @Suppress("InjectedReferences")
  val name: String
    get() = mName ?: testClass + "_" + testName

  /** @inherit
   */
  @Suppress("InjectedReferences")
  override fun setName(name: String): RecordBuilderImpl {
    val charsetEncoder = Charset.forName("latin-1").newEncoder()
    require(charsetEncoder.canEncode(name)) { "Screenshot names must have only latin characters: $name" }
    require(!name.contains(File.separator)) { "Screenshot names cannot contain '" + File.separator + "': " + name }
    mName = name
    return this
  }

  /**
   * Set the name of the test from which this screenshot is generated. This should be detected by
   * default most of the time.
   */
  fun setTestName(testName: String?): RecordBuilderImpl {
    this.testName = testName
    return this
  }

  /**
   * Set the class name of the TestCase from which this screenshot is generated. This should be
   * detected by default most of the time.
   */
  fun setTestClass(testClass: String?): RecordBuilderImpl {
    this.testClass = testClass
    return this
  }

  /** @inherit
   */
  override fun getBitmap(): Bitmap {
    return mScreenshotImpl.getBitmap(this)
  }

  /** @inherit
   */
  override fun setMaxPixels(maxPixels: Long): RecordBuilderImpl {
    this.maxPixels = maxPixels
    return this
  }

  /**
   * Returns true if this record has been given an explicit name using setName(). If false,
   * getName() will still generate a name.
   */
  fun hasExplicitName(): Boolean {
    return mName != null
  }

  fun setError(error: String?): RecordBuilderImpl {
    this.error = error
    return this
  }

  /** @inherit
   */
  override fun record() {
    mScreenshotImpl.record(this)
    checkState()
  }

  /** Sanity checks that the record is ready to be persisted  */
  fun checkState() {
    if (error != null) {
      return
    }
    for (i in 0 until tiling.width) {
      for (j in 0 until tiling.height) {
        checkNotNull(tiling.getAt(i, j)) { "expected all tiles to be filled" }
      }
    }
  }

  fun setView(view: View?): RecordBuilderImpl {
    this.view = view
    return this
  }

  fun setTiling(tiling: Tiling): RecordBuilderImpl {
    this.tiling = tiling
    return this
  }

  override fun addExtra(key: String, value: String): RecordBuilderImpl {
    mExtras[key] = value
    return this
  }

  val extras: Map<String, String>
    get() = mExtras

  override fun setGroup(groupName: String): RecordBuilderImpl {
    group = groupName
    return this
  }

  /** @inherit
   */
  override fun setIncludeAccessibilityInfo(includeAccessibilityInfo: Boolean): RecordBuilderImpl {
    this.includeAccessibilityInfo = includeAccessibilityInfo
    return this
  }

  companion object {
    /** @return The largest amount of pixels we'll capture, otherwise an exception will be thrown.
     */
    const val DEFAULT_MAX_PIXELS = 10000000L

  }
}
