package jp.wasabeef.blurry

import android.content.Context
import jp.wasabeef.blurry.Helper.animate
import android.view.ViewGroup
import jp.wasabeef.blurry.Blurry
import jp.wasabeef.blurry.BlurFactor
import jp.wasabeef.blurry.Blurry.ImageComposer
import android.graphics.Bitmap
import jp.wasabeef.blurry.Blurry.BitmapComposer
import jp.wasabeef.blurry.BlurTask
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView

/**
 * Copyright (C) 2020 Wasabeef
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
object Blurry {
  private val TAG = Blurry::class.java.simpleName
  fun with(context: Context): Composer {
    return Composer(context)
  }

  fun delete(target: ViewGroup) {
    val view = target.findViewWithTag<View>(TAG)
    if (view != null) {
      target.removeView(view)
    }
  }

  class Composer(private val context: Context) {
    private val blurredView: View
    private val factor: BlurFactor
    private var async = false
    private var animate = false
    private var duration = 300
    fun radius(radius: Int): Composer {
      factor.radius = radius
      return this
    }

    fun sampling(sampling: Int): Composer {
      factor.sampling = sampling
      return this
    }

    fun color(color: Int): Composer {
      factor.color = color
      return this
    }

    fun async(): Composer {
      async = true
      return this
    }

    fun animate(): Composer {
      animate = true
      return this
    }

    fun animate(duration: Int): Composer {
      animate = true
      this.duration = duration
      return this
    }

    fun capture(capture: View): ImageComposer {
      return ImageComposer(context, capture, factor, async)
    }

    fun from(bitmap: Bitmap): BitmapComposer {
      return BitmapComposer(context, bitmap, factor, async)
    }

    fun onto(target: ViewGroup) {
      factor.width = target.measuredWidth
      factor.height = target.measuredHeight
      if (async) {
        val task = BlurTask(target, factor, object : BlurTask.Callback {
          override fun done(bitmap: Bitmap?) {
            val drawable = BitmapDrawable(target.resources, Blur.of(context, bitmap, factor))
            addView(target, drawable)
          }
        })
        task.execute()
      } else {
        val drawable: Drawable = BitmapDrawable(context.resources, Blur.of(target, factor))
        addView(target, drawable)
      }
    }

    private fun addView(target: ViewGroup, drawable: Drawable) {
      blurredView.background = drawable
      target.addView(blurredView)
      if (animate) {
        animate(blurredView, duration)
      }
    }

    init {
      blurredView = View(context)
      blurredView.tag = TAG
      factor = BlurFactor()
    }
  }

  class BitmapComposer(
    private val context: Context,
    private val bitmap: Bitmap,
    private val factor: BlurFactor,
    private val async: Boolean
  ) {
    fun into(target: ImageView) {
      factor.width = bitmap.width
      factor.height = bitmap.height
      if (async) {
        val task = BlurTask(target.context, bitmap, factor, object : BlurTask.Callback {
          override fun done(bitmap: Bitmap?) {
            val drawable = BitmapDrawable(context.resources, bitmap)
            target.setImageDrawable(drawable)
          }
        })
        task.execute()
      } else {
        val drawable: Drawable = BitmapDrawable(
          context.resources,
          Blur.of(target.context, bitmap, factor)
        )
        target.setImageDrawable(drawable)
      }
    }
  }

  class ImageComposer(
    private val context: Context,
    private val capture: View,
    private val factor: BlurFactor,
    private val async: Boolean
  ) {
    fun into(target: ImageView) {
      factor.width = capture.measuredWidth
      factor.height = capture.measuredHeight
      if (async) {
        val task = BlurTask(capture, factor, object : BlurTask.Callback{
          override fun done(bitmap: Bitmap?) {
            val drawable = BitmapDrawable(context.resources, bitmap)
            target.setImageDrawable(drawable)
          }
        })
        task.execute()
      } else {
        val drawable: Drawable = BitmapDrawable(
          context.resources, Blur.of(
            capture, factor
          )
        )
        target.setImageDrawable(drawable)
      }
    }

    fun get(): Bitmap? {
      require(!async) { "Use getAsync() instead of async()." }
      factor.width = capture.measuredWidth
      factor.height = capture.measuredHeight
      return Blur.of(capture, factor)
    }

     fun getAsync(callback: BlurTask.Callback?) {
      factor.width = capture.measuredWidth
      factor.height = capture.measuredHeight
      BlurTask(capture, factor, callback).execute()
    }
  }
}