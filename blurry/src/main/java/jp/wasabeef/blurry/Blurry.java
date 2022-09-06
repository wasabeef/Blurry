package jp.wasabeef.blurry;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

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

public class Blurry {

  private static final String TAG = Blurry.class.getSimpleName();
  public static boolean DO_LOG = BuildConfig.DEBUG;

  //@androidx.annotation.ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
  public static boolean isSurfaceCaptureSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

  /**
   * This variant supports PixelCopy for Android API-26 and newer, and supports copying GoogleMaps.
   */
  public static Composer with(Activity activity) {
    return new Composer(activity);
  }

  @Deprecated // ("Use activity variant for API-26 and above")
  public static Composer with(Context context) {
    //noinspection deprecation
    return new Composer(context);
  }

  public static void delete(ViewGroup target) {
    View view = target.findViewWithTag(TAG);
    if (view != null) {
      target.removeView(view);
    }
  }

  public static class Composer {

    private final View blurredView;
    private final Activity activity; // Nullable
    private final Context context;
    private final BlurFactor factor;
    private boolean async;
    private boolean animate;
    private int duration = 300;

    private Composer(Activity activity, Context context) {
      this.activity = activity;
      this.context = context;
      blurredView = new View(context);
      blurredView.setTag(TAG);
      factor = new BlurFactor();
    }

    public Composer(Activity activity) {
      this(activity, activity.getBaseContext());
    }

    @Deprecated // ("Use activity variant for API-26 and above")
    public Composer(Context context) {
      this(null, context);
    }

    public Composer radius(int radius) {
      factor.radius = radius;
      return this;
    }

    public Composer sampling(int sampling) {
      factor.sampling = sampling;
      return this;
    }

    public Composer color(int color) {
      factor.color = color;
      return this;
    }

    public Composer async() {
      async = true;
      return this;
    }

    public Composer animate() {
      animate = true;
      return this;
    }

    public Composer animate(int duration) {
      animate = true;
      this.duration = duration;
      return this;
    }

    public ImageComposer capture(View capture) {
      return new ImageComposer(activity, context, capture, factor, async);
    }

    public BitmapComposer from(Bitmap bitmap) {
      return new BitmapComposer(context, bitmap, factor, async);
    }

    public void onto(final ViewGroup target) {
      factor.width = target.getMeasuredWidth();
      factor.height = target.getMeasuredHeight();

      if (async) {
        BlurTask task = new BlurTask(activity, target, factor, bitmap -> {
          final BitmapDrawable drawable =
            new BitmapDrawable(target.getResources(), Blur.of(context, bitmap, factor));
          addView(target, drawable);
        });
        task.execute();
      } else {
        Drawable drawable = new BitmapDrawable(context.getResources(), Blur.of(target, factor));
        addView(target, drawable);
      }
    }

    private void addView(ViewGroup target, Drawable drawable) {
      blurredView.setBackground(drawable);
      target.addView(blurredView);

      if (animate) {
        Helper.animate(blurredView, duration);
      }
    }
  }

  public static class BitmapComposer {

    private final Context context;
    private final Bitmap bitmap;
    private final BlurFactor factor;
    private final boolean async;

    public BitmapComposer(Context context, Bitmap bitmap, BlurFactor factor, boolean async) {
      this.context = context;
      this.bitmap = bitmap;
      this.factor = factor;
      this.async = async;
    }

    public void into(final ImageView target) {
      factor.width = bitmap.getWidth();
      factor.height = bitmap.getHeight();

      if (async) {
        BlurTask task = new BlurTask(target.getContext(), bitmap, factor, bitmap -> {
          BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
          target.setImageDrawable(drawable);
        });
        task.execute();
      } else {
        Drawable drawable = new BitmapDrawable(context.getResources(),
          Blur.of(target.getContext(), bitmap, factor));
        target.setImageDrawable(drawable);
      }
    }
  }

  public static class ImageComposer {

    private final Activity activity;
    private final Context context;
    private final View capture;
    private final BlurFactor factor;
    private final boolean async;

    public ImageComposer(Activity activity, Context context, View capture, BlurFactor factor, boolean async) {
      this.activity = activity;
      this.context = context;
      this.capture = capture;
      this.factor = factor;
      this.async = async;
    }

    public void into(final ImageView target) {
      factor.width = capture.getMeasuredWidth();
      factor.height = capture.getMeasuredHeight();

      if (async) {
        BlurTask task = new BlurTask(activity, capture, factor, bitmap -> {
          BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
          target.setImageDrawable(drawable);
        });
        task.execute();
      } else {
        Drawable drawable = new BitmapDrawable(context.getResources(), Blur.of(capture, factor));
        target.setImageDrawable(drawable);
      }
    }

    public Bitmap get() {
      if (async) throw new IllegalArgumentException("Use getAsync() instead of async().");
      factor.width = capture.getMeasuredWidth();
      factor.height = capture.getMeasuredHeight();
      return Blur.of(capture, factor);
    }

    public void getAsync(BlurTask.Callback callback) {
      factor.width = capture.getMeasuredWidth();
      factor.height = capture.getMeasuredHeight();
      new BlurTask(activity, capture, factor, callback).execute();
    }
  }
}
