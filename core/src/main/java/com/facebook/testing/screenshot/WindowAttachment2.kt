package com.facebook.testing.screenshot

import android.annotation.SuppressLint
import android.content.Context
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.*


@SuppressLint("PrivateApi")
object WindowAttachment2 {
  /** Keep track of all the attached windows here so that we don't double attach them.  */
  private val sAttachments = WeakHashMap<View, Boolean>()
  private var sAttachInfo: Any? = null
  private val sInvocationHandler =
    InvocationHandler { project, method, args ->
      if ("getCoverStateSwitch" == method.name) {
        // needed for Samsung version of Android 8.0
        false
      } else null
    }

  /**
   * Dispatch onAttachedToWindow to all the views in the view hierarchy.
   *
   *
   * Detach the view by calling `detach()` on the returned `Detacher`.
   *
   *
   * Note that if the view is already attached (either via WindowAttachment or to a real window),
   * then both the attach and the corresponding detach will be no-ops.
   *
   *
   * Note that this is hacky, after these calls the views will still say that
   * isAttachedToWindow() is false and getWindowToken() == null.
   */
  fun dispatchAttach(view: View): Detacher {
    if (view.windowToken != null || sAttachments.containsKey(view)) {
      // Screenshot tests can often be run against a View that's
      // attached to a real activity, in which case we have nothing to
      // do
      Log.i("WindowAttachment", "Skipping window attach hack since it's really attached")
      return NoopDetacher()
    }
    sAttachments[view] = true
    if (Build.VERSION.SDK_INT < 23) {
      // On older versions of Android, requesting focus on a view that would bring the
      // soft keyboard up prior to attachment would result in a NPE in the view's onAttachedToWindow
      // callback. This is due to the fact that it internally calls
      // InputMethodManager.peekInstance() to
      // grab the singleton instance, however it isn't created at that point, leading to the NPE.
      // So in order to avoid that, we just grab the InputMethodManager from the view's context
      // ahead of time to ensure the instance exists.
      // https://android.googlesource.com/platform/frameworks/base/+/a046faaf38ad818e6b5e981a39fd7394cf7cee03
      view.context.getSystemService(Context.INPUT_METHOD_SERVICE)
    }
    sAttachInfo = generateAttachInfo(view)
    setAttachInfo(view)
    return RealDetacher(view)
  }

  /** Similar to dispatchAttach, except dispatchest the corresponding detach.  */
  private fun dispatchDetach(view: View) {
    invoke(view, "dispatchDetachedFromWindow")
  }

  @Suppress("SameParameterValue")
  private operator fun invoke(view: View, methodName: String) {
    invokeUnchecked(view, methodName)
  }

  private fun invokeUnchecked(view: View, methodName: String) {
    try {
      val method = View::class.java.getDeclaredMethod(methodName)
      method.isAccessible = true
      method.invoke(view)
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  private fun setAttachInfo(view: View) {
    if (sAttachInfo == null) {
      sAttachInfo = generateAttachInfo(view)
    }
    try {
      val dispatch = View::class.java.getDeclaredMethod(
        "dispatchAttachedToWindow", Class.forName("android.view.View\$AttachInfo"),
        Int::class.javaPrimitiveType
      )
      dispatch.isAccessible = true
      dispatch.invoke(view, sAttachInfo, 0)
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  /** Simulates the view as being attached.  */
  private fun generateAttachInfo(view: View): Any? {
    return if (sAttachInfo != null) {
      sAttachInfo
    } else try {
      val cAttachInfo = Class.forName("android.view.View\$AttachInfo")
      val cViewRootImpl = Class.forName("android.view.ViewRootImpl")

      val cIWindowSession = Class.forName("android.view.IWindowSession")
      val cIWindow = Class.forName("android.view.IWindow")
      val cICallbacks = Class.forName("android.view.View\$AttachInfo\$Callbacks")
      val context = view.context
      val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
      val display = wm.defaultDisplay
      val window = createIWindow()
      val viewRootImpl: Any
      val viewRootCtorParams: Array<Class<*>>
      val viewRootCtorValues: Array<Any>
      if (Build.VERSION.SDK_INT >= 26) {
        viewRootImpl = cViewRootImpl
          .getConstructor(Context::class.java, Display::class.java)
          .newInstance(context, display)
        viewRootCtorParams = arrayOf(
          cIWindowSession,
          cIWindow,
          Display::class.java,
          cViewRootImpl,
          Handler::class.java,
          cICallbacks,
          Context::class.java
        )
        viewRootCtorValues = arrayOf(
          stub(cIWindowSession),
          window,
          display,
          viewRootImpl,
          Handler(Looper.getMainLooper()),
          stub(cICallbacks),
          context
        )
      } else {
        viewRootImpl = cViewRootImpl
          .getConstructor(Context::class.java, Display::class.java)
          .newInstance(context, display)
        viewRootCtorParams = arrayOf(
          cIWindowSession, cIWindow,
          Display::class.java, cViewRootImpl,
          Handler::class.java, cICallbacks
        )
        viewRootCtorValues = arrayOf(
          stub(cIWindowSession), window, display, viewRootImpl, Handler(Looper.getMainLooper()), stub(cICallbacks)
        )
      }
      val attachInfo = invokeConstructor(cAttachInfo, viewRootCtorParams, viewRootCtorValues)
      setField(attachInfo, "mHasWindowFocus", true)
      setField(attachInfo, "mWindowVisibility", View.VISIBLE)
      setField(attachInfo, "mInTouchMode", false)
      setField(attachInfo, "mHardwareAccelerated", false)

      attachInfo
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
  }

  @Throws(Exception::class)
  private fun invokeConstructor(clazz: Class<*>, params: Array<Class<*>>, values: Array<Any>): Any {
    val cons = clazz.getDeclaredConstructor(*params)
    cons.isAccessible = true
    return cons.newInstance(*values)
  }

  @Throws(Exception::class)
  private fun createIWindow(): Any {
    val cIWindow = Class.forName("android.view.IWindow")

    // Since IWindow is an interface, I don't need dexmaker for this
    val handler =
      InvocationHandler { proxy, method, args ->
        if (method.name == "asBinder") {
          Binder()
        } else null
      }
    return Proxy.newProxyInstance(cIWindow.classLoader, arrayOf(cIWindow), handler)
  }

  private fun stub(klass: Class<*>): Any {
    require(klass.isInterface) { "Cannot stub an non-interface" }
    return Proxy.newProxyInstance(klass.classLoader, arrayOf(klass), sInvocationHandler)
  }

  @Throws(Exception::class)
  private fun setField(o: Any, fieldName: String, value: Any) {
    val clazz: Class<*> = o.javaClass
    val field = clazz.getDeclaredField(fieldName)
    field.isAccessible = true
    field[o] = value
  }

  interface Detacher {
    fun detach()
  }

  private class NoopDetacher : Detacher {
    override fun detach() {}
  }

  private class RealDetacher(private val mView: View) : Detacher {
    override fun detach() {
      dispatchDetach(mView)
      sAttachments.remove(mView)
    }
  }
}
