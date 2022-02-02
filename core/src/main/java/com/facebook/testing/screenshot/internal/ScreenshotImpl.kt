package com.facebook.testing.screenshot.internal

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import com.facebook.testing.screenshot.WindowAttachment
import com.facebook.testing.screenshot.WindowAttachment2
import com.facebook.testing.screenshot.layouthierarchy.AccessibilityHierarchyDumper
import com.facebook.testing.screenshot.layouthierarchy.AccessibilityIssuesDumper
import com.facebook.testing.screenshot.layouthierarchy.AccessibilityUtil
import com.facebook.testing.screenshot.layouthierarchy.LayoutHierarchyDumper
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.*
import java.util.concurrent.Callable


/**
 * Implementation for Screenshot class.
 *
 *
 * The Screenshot class has static methods, because that's how the API should look like, this
 * class has all its implementation for testability.
 *
 *
 * This is public only for implementation convenient for using UiThreadHelper.
 */
class ScreenshotImpl internal constructor(
  /** The album of all the screenshots taken in this run.  */
  private val mAlbum: Album
) {
  private var mTileSize = 512
  private var mBitmap: Bitmap? = null
  private var mCanvas: Canvas? = null


  var tileSize: Int
    get() = mTileSize
    set(tileSize) {
      mTileSize = tileSize
      mBitmap = null
      mCanvas = null
    }

  /** Snaps a screenshot of the activity using the testName as the name.  */
  fun snapActivity(activity: Activity?): RecordBuilderImpl {
    if(activity == null) {
      println("snapActivity $activity is null")
    }
    if (!isUiThread) {
      return runCallableOnUiThread { snapActivity(activity) }
        .setTestClass(TestNameDetector.getTestClass())
        .setTestName(TestNameDetector.getTestName())
    }
    val rootView = activity?.window?.decorView
    return snap(rootView)
  }

  /**
   * Snaps a screenshot of the view (which should already be measured and layouted) using testName
   * as the name.
   */
  fun snap(measuredView: View?): RecordBuilderImpl {
    return RecordBuilderImpl(this)
      .setView(measuredView)
      .setTestClass(TestNameDetector.getTestClass())
      .setTestName(TestNameDetector.getTestName())
  }

  // VisibleForTesting
  fun flush() {
    mAlbum.flush()
  }

  private fun storeBitmap(recordBuilder: RecordBuilderImpl) {
    if (recordBuilder.tiling.getAt(0, 0) != null || recordBuilder.error != null) {
      return
    }
    if (!isUiThread) {
      runCallableOnUiThread {
        storeBitmap(recordBuilder)
        null
      }
      return
    }
    val measuredView = recordBuilder.view
    if (measuredView!!.measuredHeight == 0 || measuredView.measuredWidth == 0) {
      throw RuntimeException("Can't take a screenshot, since this view is not measured")
    }
    val detacher = WindowAttachment2.dispatchAttach(measuredView)
    try {
      val width = measuredView.width
      val height = measuredView.height
      assertNotTooLarge(width, height, recordBuilder)
      val maxi = (width + mTileSize - 1) / mTileSize
      val maxj = (height + mTileSize - 1) / mTileSize
      recordBuilder.setTiling(Tiling(maxi, maxj))
      for (i in 0 until maxi) {
        for (j in 0 until maxj) {
          drawTile(measuredView, i, j, recordBuilder)
        }
      }
    } catch (e: IOException) {
      throw RuntimeException(e)
    } finally {
      detacher.detach()
    }
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  @Throws(IOException::class)
  private fun drawTile(measuredView: View?, i: Int, j: Int, recordBuilder: RecordBuilderImpl) {
    val width = measuredView!!.width
    val height = measuredView.height
    val left = i * mTileSize
    val top = j * mTileSize
    val right = Math.min(left + mTileSize, width)
    val bottom = Math.min(top + mTileSize, height)
    lazyInitBitmap()
    mBitmap!!.reconfigure(right - left, bottom - top, Bitmap.Config.ARGB_8888)
    mCanvas = Canvas(mBitmap!!)

    clearCanvas(mCanvas)
    drawClippedView(measuredView, left, top, mCanvas)
    val tempName = mAlbum.writeBitmap(recordBuilder.name, i, j, mBitmap)
      ?: throw NullPointerException()
    recordBuilder.tiling.setAt(left / mTileSize, top / mTileSize, tempName)
  }

  private fun lazyInitBitmap() {
    if (mBitmap != null) {
      return
    }
    mBitmap = Bitmap.createBitmap(mTileSize, mTileSize, Bitmap.Config.ARGB_8888)
    mCanvas = Canvas(mBitmap!!)
  }

  private fun clearCanvas(canvas: Canvas?) {
    canvas!!.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC)
  }

  /**
   * Draw a part of the view, in particular it returns a bitmap of dimensions `
   * (right-left)*(bottom-top)`, with the rendering of the view starting from position (`
   * left`, `top`).
   *
   *
   * For well behaved views, calling this repeatedly shouldn't change the rendering, so it should
   * it okay to render each tile one by one and combine it later.
   */
  private fun drawClippedView(view: View?, left: Int, top: Int, canvas: Canvas?) {
    canvas!!.translate(-left.toFloat(), -top.toFloat())
    view!!.draw(canvas)
    canvas.translate(left.toFloat(), top.toFloat())
  }

  /** Records the RecordBuilderImpl, and verifies if required  */
  fun record(recordBuilder: RecordBuilderImpl) {
    storeBitmap(recordBuilder)
    try {
      val dump = JSONObject()
      val viewDump = LayoutHierarchyDumper.create().dumpHierarchy(recordBuilder.view)
      dump.put("viewHierarchy", viewDump)
      dump.put("version", METADATA_VERSION)
      val axTree =
        if (recordBuilder.includeAccessibilityInfo) AccessibilityUtil.generateAccessibilityTree(
          recordBuilder.view,
          null
        ) else null
      dump.put("axHierarchy", AccessibilityHierarchyDumper.dumpHierarchy(axTree))
      mAlbum.writeViewHierarchyFile(recordBuilder.name, dump.toString(2))
      if (axTree != null) {
        val issues = JSONObject()
        issues.put("axIssues", AccessibilityIssuesDumper.dumpIssues(axTree))
        mAlbum.writeAxIssuesFile(recordBuilder.name, issues.toString(2))
      }
      mAlbum.addRecord(recordBuilder)
    } catch (e: IOException) {
      throw RuntimeException(e)
    } catch (e: JSONException) {
      throw RuntimeException(e)
    }
  }

  fun getBitmap(recordBuilder: RecordBuilderImpl): Bitmap {
    require(recordBuilder.tiling.getAt(0, 0) == null) { "can't call getBitmap() after record()" }
    val view = recordBuilder.view
    val bmp = Bitmap.createBitmap(view!!.width, view.height, Bitmap.Config.ARGB_8888)
    val detacher = WindowAttachment2.dispatchAttach(recordBuilder.view!!)
    try {
      drawClippedView(view, 0, 0, Canvas(bmp))
    } finally {
      detacher.detach()
    }
    return bmp
  }

  private val isUiThread: Boolean
    private get() = Looper.getMainLooper().thread === Thread.currentThread()

  private fun <T> runCallableOnUiThread(callable: Callable<T>): T {
    val ret = arrayOfNulls<Any>(1) as Array<T>
    val e = arrayOfNulls<Exception>(1)
    val lock = Object()
    val handler = Handler(Looper.getMainLooper())
    synchronized(lock) {
      handler.post {
        try {
          ret[0] = callable.call()
        } catch (ee: Exception) {
          e[0] = ee
        }
        synchronized(lock) { lock.notifyAll() }
      }
      try {
        lock.wait()
      } catch (ee: InterruptedException) {
        throw RuntimeException(ee)
      }
    }
    if (e[0] != null) {
      throw RuntimeException(e[0])
    }
    return ret[0]
  }

  companion object {
    /**
     * The version of the metadata file generated. This should be bumped whenever the structure of the
     * metadata file changes in such a way that would cause a comparison between old and new files to
     * be invalid or not useful.
     */
    private const val METADATA_VERSION = 1
    private var sInstance: ScreenshotImpl? = null

    /**
     * Factory method that creates this instance based on what arguments are passed to the
     * instrumentation
     */
    private fun create(context: Context): ScreenshotImpl {
      val album: Album = AlbumImpl.create(context, "default")
      album.cleanup()
      return ScreenshotImpl(album)
    }

    /** Get a singleton instance of the ScreenshotImpl  */
    fun getInstance(): ScreenshotImpl {

        if (sInstance != null) {
          return sInstance!!
        }
        synchronized(ScreenshotImpl::class.java) {
          if (sInstance != null) {
            return sInstance!!
          }
          val instrumentation =
            Registry.getRegistry().instrumentation
          sInstance =
            create(instrumentation.context)
          return sInstance!!
        }
      }

    /**
     * Check if getInstance() has ever been called.
     *
     *
     * This is for a minor optimization to avoid creating a ScreenshotImpl at onDestroy() if it was
     * never called during the run.
     */
    fun hasBeenCreated(): Boolean {
      return sInstance != null
    }

    fun getMaxPixels(): Long{
      return RecordBuilderImpl.DEFAULT_MAX_PIXELS
    }

    private fun assertNotTooLarge(width: Int, height: Int, recordBuilder: RecordBuilderImpl) {
      val maxPixels: Long = recordBuilder.maxPixels
      if (maxPixels <= 0) {
        return
      }
      check(width.toLong() * height <= maxPixels) {
        String.format(
          Locale.US,
          "View too large: (%d, %d)",
          width,
          height
        )
      }
    }
  }
}
