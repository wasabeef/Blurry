package jp.wasabeef.blurry;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Copyright (C) 2020 Wasabeef
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class BlurTask {

  public interface Callback {
    void done(Bitmap bitmap);
  }

  private final WeakReference<Context> contextWeakRef;
  private final BlurFactor factor;
  private Bitmap bitmap;
  private final Callback callback;
  private static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();
  private boolean bitmapExtractCompleted = false;

  /**
   * @param activity Nullable, will fall back to non-surface deprecated drawing-cache when no activity is supplied
   */
  public BlurTask(Activity activity, View target, BlurFactor factor, Callback callback) {
    this.factor = factor;
    this.callback = callback;
    this.contextWeakRef = new WeakReference<>(target.getContext());

    long start = System.currentTimeMillis();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null && activity.getWindow().peekDecorView() != null) {
      // Use PixelCopy, PixelCopy copies from the surface, and can thus handle GoogleMap
      bitmap = Bitmap.createBitmap(target.getWidth(), target.getHeight(), Bitmap.Config.ARGB_8888);

      int[] locations = new int[2];
      target.getLocationInWindow(locations);
      Rect rect = new Rect(locations[0], locations[1], locations[0] + target.getWidth(), locations[1] + target.getHeight());

      Window window = activity.getWindow();
      // See javadoc on PixelCopy.request: https://developer.android.com/reference/android/view/PixelCopy#request(android.view.Window,%20android.graphics.Rect,%20android.graphics.Bitmap,%20android.view.PixelCopy.OnPixelCopyFinishedListener,%20android.os.Handler)
      // - it requires that the window's decorView is already defined (handled in if-statement above)
      // - and it requires that the window has a backing surface, they recommend postponing till after first onDraw. In this case catch error an fall back to deprecated drawing cache.
      //   Alternatively we or the user must delay til after first Draw.
      try {
        PixelCopy.request(window, rect, bitmap, copyResult -> {
          if (copyResult != PixelCopy.SUCCESS) {
            bitmap = extractBitmapByDeprecatedDrawingCache(target);
          }
          bitmapExtractCompleted = true;
          execute();
        }, new Handler(Looper.getMainLooper())); // We will get the callback on the handler, probably don't use main, maybe it has to be main if we want to extract bitmap old fashioned way on error
      } catch (IllegalArgumentException e) {
        // Handle missing surface. See Android source-code https://github.com/aosp-mirror/platform_frameworks_base/blob/master/graphics/java/android/view/PixelCopy.java
        // thus avoid IllegalArgumentException("Window doesn't have a backing surface!")
        bitmap = extractBitmapByDeprecatedDrawingCache(target);
      }
    } else {
      bitmap = extractBitmapByDeprecatedDrawingCache(target);
      bitmapExtractCompleted = true;
    }
    if (BuildConfig.DEBUG) Log.i("Blurry", "Time to extract  bitmap: " + (System.currentTimeMillis() - start) + "ms"); // 25-60 ms
  }

  private Bitmap extractBitmapByDeprecatedDrawingCache(View target) {
    long start = System.currentTimeMillis();
    target.setDrawingCacheEnabled(true);
    target.destroyDrawingCache();
    target.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);
    final Bitmap bitmap = target.getDrawingCache();
    if (BuildConfig.DEBUG) Log.i("Blurry", "Time to extract  bitmap: " + (System.currentTimeMillis() - start) + "ms"); // 25-60 ms
    return bitmap;
  }

  public BlurTask(Context context, Bitmap bitmap, BlurFactor factor, Callback callback) {
    this.factor = factor;
    this.callback = callback;
    this.contextWeakRef = new WeakReference<>(context);

    this.bitmap = bitmap;
  }

  public void execute() {
    if (bitmapExtractCompleted) {
      THREAD_POOL.execute(() -> {
        Context context = contextWeakRef.get();
        // Do the work outside main-thread
        Bitmap output = Blur.of(context, bitmap, factor);
        if (callback != null) {
          new Handler(Looper.getMainLooper()).post(() -> callback.done(output));
        }
      });
    }
  }
}
