package jp.wasabeef.blurry

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

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
class BlurTask {
    interface Callback {
        fun done(bitmap: Bitmap?)
    }

    private val contextWeakRef: WeakReference<Context>
    private val factor: BlurFactor
    private val bitmap: Bitmap
    private val callback: Callback?

    constructor(target: View, factor: BlurFactor, callback: Callback?) {
        this.factor = factor
        this.callback = callback
        contextWeakRef = WeakReference(target.context)
        target.isDrawingCacheEnabled = true
        target.destroyDrawingCache()
        target.drawingCacheQuality = View.DRAWING_CACHE_QUALITY_LOW
        bitmap = target.drawingCache
    }

    constructor(context: Context, bitmap: Bitmap, factor: BlurFactor, callback: Callback?) {
        this.factor = factor
        this.callback = callback
        contextWeakRef = WeakReference(context)
        this.bitmap = bitmap
    }

    fun execute() {
        THREAD_POOL.execute {
            if (callback != null) {
                Handler(Looper.getMainLooper()).post {
                    callback.done(
                        Blur.of(
                          contextWeakRef.get(),
                            bitmap,
                            factor
                        )
                    )
                }
            }
        }
    }

    companion object {
        private val THREAD_POOL = Executors.newCachedThreadPool()
    }
}