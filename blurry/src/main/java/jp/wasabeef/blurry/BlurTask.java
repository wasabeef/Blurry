package jp.wasabeef.blurry;

import android.annotation.SuppressLint;
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

  private static final String TAG = "Blurry_BlurTask";
  private final Context context;
  private final BlurFactor factor;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Bitmap bitmap;
  private final Callback callback;
  private static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();
  private boolean usingPixelCopyWithDelayedExecute;

  /**
   * @param activity Nullable, will fall back to non-surface deprecated drawing-cache when no activity is supplied
   */
  public BlurTask(Activity activity /*nullable*/, View target, BlurFactor factor, Callback callback) {
    this.factor = factor;
    this.callback = callback;
    this.context = target.getContext().getApplicationContext();
    // When PixelCopy runs this bitmap is just the reference, its still empty
    this.bitmap = extractBitmapWithAsyncPixelCopy(activity, target);
  }

  public BlurTask(Context context, Bitmap bitmap, BlurFactor factor, Callback callback) {
    this.factor = factor;
    this.callback = callback;
    this.context = context;
    this.bitmap = bitmap;
  }

  private Bitmap extractBitmapWithAsyncPixelCopy(Activity activity, View target) {
    Bitmap bitmap;
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
        PixelCopy.request(window, rect, bitmap, createPixelCopyListener(target), handler);
        usingPixelCopyWithDelayedExecute = true; // Must be after successful request, so if surface isn't ready, then the execute() actually executes request
        if (Blurry.DO_LOG) Log.d(TAG, "PixelCopy: Registered for callback");
      } catch (IllegalArgumentException e) {
        // Handle missing surface. See Android source-code https://github.com/aosp-mirror/platform_frameworks_base/blob/master/graphics/java/android/view/PixelCopy.java
        // thus avoid IllegalArgumentException("Window doesn't have a backing surface!")
        if (Blurry.DO_LOG) Log.d(TAG, "PixelCopy error (will fallback to manual extraction)", e);
        bitmap = extractBitmapByDeprecatedDrawingCache(target);
      }
    } else {
      bitmap = extractBitmapByDeprecatedDrawingCache(target);
    }
    if (Blurry.DO_LOG)
      Log.d(TAG, "Time to extract  bitmap: " + (System.currentTimeMillis() - start) + "ms"); // 25-60 ms
    return bitmap;
  }

  // @android.support.annotation.RequiresApi(api = Build.VERSION_CODES.N)
  private PixelCopy.OnPixelCopyFinishedListener createPixelCopyListener(final View target) {
    return copyResult -> {
      // This runs on main thread, just as we expect the BlurTask constructor to run on
      @SuppressLint("InlinedApi") boolean isPixelCopySuccessful = copyResult == PixelCopy.SUCCESS;
      if (!isPixelCopySuccessful) {
        if (Blurry.DO_LOG) Log.w(TAG, "PixelCopy failed, fallback to manual extraction");
        this.bitmap = extractBitmapByDeprecatedDrawingCache(target);
      } else {
        if (Blurry.DO_LOG) Log.d(TAG, "PixelCopy success");
      }
      executeInnerOnBackgroundThreadPool();
    };
  }

  // This must run on main thread, as it accesses view-methods
  private Bitmap extractBitmapByDeprecatedDrawingCache(View target) {
    long start = System.currentTimeMillis();
    target.setDrawingCacheEnabled(true);
    target.destroyDrawingCache();
    target.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);
    final Bitmap bitmap = target.getDrawingCache();
    if (Blurry.DO_LOG)
      Log.d(TAG, "Time to extract  bitmap: " + (System.currentTimeMillis() - start) + "ms"); // 25-60 ms
    return bitmap;
  }

  public void execute() {
    if (!usingPixelCopyWithDelayedExecute) {
      executeInnerOnBackgroundThreadPool();
    }
  }

  private void executeInnerOnBackgroundThreadPool() {
    THREAD_POOL.execute(() -> {
      // Do the work outside main-thread
      Bitmap output = Blur.of(context, bitmap, factor);
      if (callback != null) {
        handler.post(() -> callback.done(output)); // Run callback on main-thread
      }
    });
  }
}
