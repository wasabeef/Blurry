package jp.wasabeef.blurry.internal;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Copyright (C) 2017 Wasabeef
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

public class BlurTask {

  public interface Callback {

    void done(BitmapDrawable drawable);
  }

  private Resources res;
  private WeakReference<Context> contextWeakRef;
  private BlurFactor factor;
  private Bitmap bitmap;
  private Callback callback;
  private static ExecutorService THREAD_POOL = Executors.newCachedThreadPool();

  public BlurTask(View target, BlurFactor factor, Callback callback) {
    this.res = target.getResources();
    this.factor = factor;
    this.callback = callback;
    this.contextWeakRef = new WeakReference<>(target.getContext());

    target.setDrawingCacheEnabled(true);
    target.destroyDrawingCache();
    target.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);
    bitmap = target.getDrawingCache();
  }

  public BlurTask(Context context, Bitmap bitmap, BlurFactor factor, Callback callback) {
    this.res = context.getResources();
    this.factor = factor;
    this.callback = callback;
    this.contextWeakRef = new WeakReference<>(context);

    this.bitmap = bitmap;
  }

  public void execute() {
    THREAD_POOL.execute(new Runnable() {
      @Override public void run() {
        Context context = contextWeakRef.get();
        final BitmapDrawable bitmapDrawable =
            new BitmapDrawable(res, Blur.of(context, bitmap, factor));

        if (callback != null) {
          new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
              callback.done(bitmapDrawable);
            }
          });
        }
      }
    });
  }
}
