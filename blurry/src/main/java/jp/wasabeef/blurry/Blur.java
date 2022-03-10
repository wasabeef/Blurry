package jp.wasabeef.blurry;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RSRuntimeException;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
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

class Blur {

  public static Bitmap of(View view, BlurFactor factor) {
    view.setDrawingCacheEnabled(true);
    view.destroyDrawingCache();
    view.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_LOW);
    Bitmap cache = view.getDrawingCache();
    Bitmap bitmap = of(view.getContext(), cache, factor);
    cache.recycle();
    return bitmap;
  }

  public static Bitmap of(Context context, Bitmap source, BlurFactor factor) {
    int width = factor.width / factor.sampling;
    int height = factor.height / factor.sampling;

    if (Helper.hasZero(width, height)) {
      return null;
    }
    // Note that timing has only been added in debug, but app may run slower in debug than in release Profile claims (maybe due to proguard which AndroidBooking does not use)
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

    long startDrawToCanvas = System.currentTimeMillis();
    Canvas canvas = new Canvas(bitmap);
    canvas.scale(1 / (float) factor.sampling, 1 / (float) factor.sampling);
    Paint paint = new Paint();
    paint.setFlags(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    PorterDuffColorFilter filter =
      new PorterDuffColorFilter(factor.color, PorterDuff.Mode.SRC_ATOP);
    paint.setColorFilter(filter);
    canvas.drawBitmap(source, 0, 0, paint);
    if (BuildConfig.DEBUG) Log.i("Blurry", "Time to draw source to Canvas: " + (System.currentTimeMillis() - startDrawToCanvas) + "ms");

    long startBlur = System.currentTimeMillis();
    if (Build.VERSION.SDK_INT >= 31) {
      // Render script is deprecated in Android S
      bitmap = Blur.optimizedStack(bitmap, factor.radius, true);
    } else {
      try {
        // RenderScript is hardware accelerated up to Android-11. See https://developer.android.com/guide/topics/renderscript/compute
        bitmap = Blur.rs(context, bitmap, factor.radius);
      } catch (RSRuntimeException e) {
        bitmap = Blur.optimizedStack(bitmap, factor.radius, true);
      }
    }
    if (BuildConfig.DEBUG) Log.i("Blurry", "Time to blur: " + (System.currentTimeMillis() - startBlur) + "ms");

    if (factor.sampling == BlurFactor.DEFAULT_SAMPLING) {
      return bitmap;
    } else {
      Bitmap scaled = Bitmap.createScaledBitmap(bitmap, factor.width, factor.height, true);
      bitmap.recycle();
      return scaled;
    }
  }

  private static Bitmap rs(Context context, Bitmap bitmap, int radius) throws RSRuntimeException {
    RenderScript rs = null;
    Allocation input = null;
    Allocation output = null;
    ScriptIntrinsicBlur blur = null;
    try {
      rs = RenderScript.create(context);
      rs.setMessageHandler(new RenderScript.RSMessageHandler());
      input = Allocation.createFromBitmap(rs, bitmap, Allocation.MipmapControl.MIPMAP_NONE,
        Allocation.USAGE_SCRIPT);
      output = Allocation.createTyped(rs, input.getType());
      blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));

      blur.setInput(input);
      blur.setRadius(radius);
      blur.forEach(output);
      output.copyTo(bitmap);
    } finally {
      if (rs != null) {
        rs.destroy();
      }
      if (input != null) {
        input.destroy();
      }
      if (output != null) {
        output.destroy();
      }
      if (blur != null) {
        blur.destroy();
      }
    }

    return bitmap;
  }

  private static Bitmap stack(Bitmap sentBitmap, int radius, boolean canReuseInBitmap) {

    // Stack Blur v1.0 from
    // http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html
    //
    // Java Author: Mario Klingemann <mario at quasimondo.com>
    // http://incubator.quasimondo.com
    // created Feburary 29, 2004
    // Android port : Yahel Bouaziz <yahel at kayenko.com>
    // http://www.kayenko.com
    // ported april 5th, 2012

    // This is a compromise between Gaussian Blur and Box blur
    // It creates much better looking blurs than Box Blur, but is
    // 7x faster than my Gaussian Blur implementation.
    //
    // I called it Stack Blur because this describes best how this
    // filter works internally: it creates a kind of moving stack
    // of colors whilst scanning through the image. Thereby it
    // just has to add one new block of color to the right side
    // of the stack and remove the leftmost color. The remaining
    // colors on the topmost layer of the stack are either added on
    // or reduced by one, depending on if they are on the right or
    // on the left side of the stack.
    //
    // If you are using this algorithm in your code please add
    // the following line:
    //
    // Stack Blur Algorithm by Mario Klingemann <mario@quasimondo.com>

    Bitmap bitmap;
    if (canReuseInBitmap) {
      bitmap = sentBitmap;
    } else {
      bitmap = sentBitmap.copy(sentBitmap.getConfig(), true);
    }

    if (radius < 1) {
      return (null);
    }

    int w = bitmap.getWidth();
    int h = bitmap.getHeight();

    int[] pix = new int[w * h];
    bitmap.getPixels(pix, 0, w, 0, 0, w, h);

    int wm = w - 1;
    int hm = h - 1;
    int wh = w * h;
    int div = radius + radius + 1;

    int[] r = new int[wh];
    int[] g = new int[wh];
    int[] b = new int[wh];
    int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
    int[] vmin = new int[Math.max(w, h)];

    int divsum = (div + 1) >> 1;
    divsum *= divsum;
    int[] dv = new int[256 * divsum];
    for (i = 0; i < 256 * divsum; i++) {
      dv[i] = (i / divsum);
    }

    yw = yi = 0;

    int[][] stack = new int[div][3];
    int stackpointer;
    int stackstart;
    int[] sir;
    int rbs;
    int r1 = radius + 1;
    int routsum, goutsum, boutsum;
    int rinsum, ginsum, binsum;

    for (y = 0; y < h; y++) {
      rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
      for (i = -radius; i <= radius; i++) {
        p = pix[yi + Math.min(wm, Math.max(i, 0))];
        sir = stack[i + radius];
        sir[0] = (p & 0xff0000) >> 16;
        sir[1] = (p & 0x00ff00) >> 8;
        sir[2] = (p & 0x0000ff);
        rbs = r1 - Math.abs(i);
        rsum += sir[0] * rbs;
        gsum += sir[1] * rbs;
        bsum += sir[2] * rbs;
        if (i > 0) {
          rinsum += sir[0];
          ginsum += sir[1];
          binsum += sir[2];
        } else {
          routsum += sir[0];
          goutsum += sir[1];
          boutsum += sir[2];
        }
      }
      stackpointer = radius;

      for (x = 0; x < w; x++) {

        r[yi] = dv[rsum];
        g[yi] = dv[gsum];
        b[yi] = dv[bsum];

        rsum -= routsum;
        gsum -= goutsum;
        bsum -= boutsum;

        stackstart = stackpointer - radius + div;
        sir = stack[stackstart % div];

        routsum -= sir[0];
        goutsum -= sir[1];
        boutsum -= sir[2];

        if (y == 0) {
          vmin[x] = Math.min(x + radius + 1, wm);
        }
        p = pix[yw + vmin[x]];

        sir[0] = (p & 0xff0000) >> 16;
        sir[1] = (p & 0x00ff00) >> 8;
        sir[2] = (p & 0x0000ff);

        rinsum += sir[0];
        ginsum += sir[1];
        binsum += sir[2];

        rsum += rinsum;
        gsum += ginsum;
        bsum += binsum;

        stackpointer = (stackpointer + 1) % div;
        sir = stack[(stackpointer) % div];

        routsum += sir[0];
        goutsum += sir[1];
        boutsum += sir[2];

        rinsum -= sir[0];
        ginsum -= sir[1];
        binsum -= sir[2];

        yi++;
      }
      yw += w;
    }
    for (x = 0; x < w; x++) {
      rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
      yp = -radius * w;
      for (i = -radius; i <= radius; i++) {
        yi = Math.max(0, yp) + x;

        sir = stack[i + radius];

        sir[0] = r[yi];
        sir[1] = g[yi];
        sir[2] = b[yi];

        rbs = r1 - Math.abs(i);

        rsum += r[yi] * rbs;
        gsum += g[yi] * rbs;
        bsum += b[yi] * rbs;

        if (i > 0) {
          rinsum += sir[0];
          ginsum += sir[1];
          binsum += sir[2];
        } else {
          routsum += sir[0];
          goutsum += sir[1];
          boutsum += sir[2];
        }

        if (i < hm) {
          yp += w;
        }
      }
      yi = x;
      stackpointer = radius;
      for (y = 0; y < h; y++) {
        // Preserve alpha channel: ( 0xff000000 & pix[yi] )
        pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

        rsum -= routsum;
        gsum -= goutsum;
        bsum -= boutsum;

        stackstart = stackpointer - radius + div;
        sir = stack[stackstart % div];

        routsum -= sir[0];
        goutsum -= sir[1];
        boutsum -= sir[2];

        if (x == 0) {
          vmin[y] = Math.min(y + r1, hm) * w;
        }
        p = x + vmin[y];

        sir[0] = r[p];
        sir[1] = g[p];
        sir[2] = b[p];

        rinsum += sir[0];
        ginsum += sir[1];
        binsum += sir[2];

        rsum += rinsum;
        gsum += ginsum;
        bsum += binsum;

        stackpointer = (stackpointer + 1) % div;
        sir = stack[stackpointer];

        routsum += sir[0];
        goutsum += sir[1];
        boutsum += sir[2];

        rinsum -= sir[0];
        ginsum -= sir[1];
        binsum -= sir[2];

        yi += w;
      }
    }

    bitmap.setPixels(pix, 0, w, 0, 0, w, h);

    return (bitmap);
  }

  public static Bitmap optimizedStack(Bitmap sentBitmap, int radius, boolean canReuseInBitmap) {

    /*
     * An optimized version of stack blur, 2x faster than the original.
     *
     * @author Enrique López Mañas <eenriquelopez@gmail.com>
     * http://www.neo-tech.es
     *
     * Author of the original algorithm: Mario Klingemann <mario.quasimondo.com>
     *
     * Based heavily on http://vitiy.info/Code/stackblur.cpp
     * See http://vitiy.info/stackblur-algorithm-multi-threaded-blur-for-cpp/
     *
     * @copyright: Enrique López Mañas
     * @license: Apache License 2.0
     */

    Bitmap bitmap;
    if (canReuseInBitmap) {
      bitmap = sentBitmap;
    } else {
      bitmap = sentBitmap.copy(sentBitmap.getConfig(), true);
    }

    int w = bitmap.getWidth();
    int h = bitmap.getHeight();
    int[] src = new int[w * h];
    bitmap.getPixels(src, 0, w, 0, 0, w, h);
    int cores = EXECUTOR_THREADS;

    // The multi-threaded version produces images with small errors, hence disabled
    if (cores > 1 && false) {
      ExecutorService threadPool = Executors.newFixedThreadPool(cores); // Maybe move out into static Blurry, instead of recreating on each run.
      // The cores may not be equally fast, so we make more jobs than cores. Its significantly faster to use cores*5 than cores job on Samsung S20 FE.
      int jobs = cores * 10;
      List<Callable<Object>> todo = new ArrayList<>(jobs);

      for (int i = 0; i < jobs; i++) {
        final int job = i;
        todo.add(() -> {
          internal_optimized_stack_iteration(src, w, h, radius, jobs, job, 1);
          internal_optimized_stack_iteration(src, w, h, radius, jobs, job, 2);
          if (BuildConfig.DEBUG) Log.i("Blurry", job+" finished, thread="+Thread.currentThread().getName());
          return null;
        });
      }
      try {
        threadPool.invokeAll(todo);
      } catch (InterruptedException e) {
      }
      if (BuildConfig.DEBUG) Log.i("Blurry", "All jobs finished");
    } else {
      // it runs in same thread, so just say theres 1 core
      internal_optimized_stack_iteration(src, w, h, radius, 1, 0, 1);
      internal_optimized_stack_iteration(src, w, h, radius, 1, 0, 2);
    }
    return Bitmap.createBitmap(src, w, h, Bitmap.Config.ARGB_8888);
  }

  private static void internal_optimized_stack_iteration(int[] src, int w, int h, int radius, int cores, int core, int step) {
    int x, y, xp, yp, i;
    int sp;
    int stack_start;
    int stack_i;

    int src_i;
    int dst_i;

    long sum_r, sum_g, sum_b,
      sum_in_r, sum_in_g, sum_in_b,
      sum_out_r, sum_out_g, sum_out_b;

    int wm = w - 1;
    int hm = h - 1;
    int div = (radius * 2) + 1;
    int mul_sum = stackblur_mul[radius];
    byte shr_sum = stackblur_shr[radius];
    int[] stack = new int[div];

    if (step == 1)
    {
      int minY = core * h / cores;
      int maxY = (core + 1) * h / cores;

      for(y = minY; y < maxY; y++)
      {
        sum_r = sum_g = sum_b =
          sum_in_r = sum_in_g = sum_in_b =
            sum_out_r = sum_out_g = sum_out_b = 0;

        src_i = w * y; // start of line (0,y)

        for(i = 0; i <= radius; i++)
        {
          stack_i    = i;
          stack[stack_i] = src[src_i];
          sum_r += ((src[src_i] >>> 16) & 0xff) * (i + 1);
          sum_g += ((src[src_i] >>> 8) & 0xff) * (i + 1);
          sum_b += (src[src_i] & 0xff) * (i + 1);
          sum_out_r += ((src[src_i] >>> 16) & 0xff);
          sum_out_g += ((src[src_i] >>> 8) & 0xff);
          sum_out_b += (src[src_i] & 0xff);
        }


        for(i = 1; i <= radius; i++)
        {
          if (i <= wm) src_i += 1;
          stack_i = i + radius;
          stack[stack_i] = src[src_i];
          sum_r += ((src[src_i] >>> 16) & 0xff) * (radius + 1 - i);
          sum_g += ((src[src_i] >>> 8) & 0xff) * (radius + 1 - i);
          sum_b += (src[src_i] & 0xff) * (radius + 1 - i);
          sum_in_r += ((src[src_i] >>> 16) & 0xff);
          sum_in_g += ((src[src_i] >>> 8) & 0xff);
          sum_in_b += (src[src_i] & 0xff);
        }


        sp = radius;
        xp = radius;
        if (xp > wm) xp = wm;
        src_i = xp + y * w; //   img.pix_ptr(xp, y);
        dst_i = y * w; // img.pix_ptr(0, y);
        for(x = 0; x < w; x++)
        {
          src[dst_i] = (int)
            ((src[dst_i] & 0xff000000) |
              ((((sum_r * mul_sum) >>> shr_sum) & 0xff) << 16) |
              ((((sum_g * mul_sum) >>> shr_sum) & 0xff) << 8) |
              ((((sum_b * mul_sum) >>> shr_sum) & 0xff)));
          dst_i += 1;

          sum_r -= sum_out_r;
          sum_g -= sum_out_g;
          sum_b -= sum_out_b;

          stack_start = sp + div - radius;
          if (stack_start >= div) stack_start -= div;
          stack_i = stack_start;

          sum_out_r -= ((stack[stack_i] >>> 16) & 0xff);
          sum_out_g -= ((stack[stack_i] >>> 8) & 0xff);
          sum_out_b -= (stack[stack_i] & 0xff);

          if(xp < wm)
          {
            src_i += 1;
            ++xp;
          }

          stack[stack_i] = src[src_i];

          sum_in_r += ((src[src_i] >>> 16) & 0xff);
          sum_in_g += ((src[src_i] >>> 8) & 0xff);
          sum_in_b += (src[src_i] & 0xff);
          sum_r    += sum_in_r;
          sum_g    += sum_in_g;
          sum_b    += sum_in_b;

          ++sp;
          if (sp >= div) sp = 0;
          stack_i = sp;

          sum_out_r += ((stack[stack_i] >>> 16) & 0xff);
          sum_out_g += ((stack[stack_i] >>> 8) & 0xff);
          sum_out_b += (stack[stack_i] & 0xff);
          sum_in_r  -= ((stack[stack_i] >>> 16) & 0xff);
          sum_in_g  -= ((stack[stack_i] >>> 8) & 0xff);
          sum_in_b  -= (stack[stack_i] & 0xff);
        }

      }
    }
    // step 2
    else if (step == 2)
    {
      int minX = core * w / cores;
      int maxX = (core + 1) * w / cores;

      for(x = minX; x < maxX; x++)
      {
        sum_r =    sum_g =    sum_b =
          sum_in_r = sum_in_g = sum_in_b =
            sum_out_r = sum_out_g = sum_out_b = 0;

        src_i = x; // x,0
        for(i = 0; i <= radius; i++)
        {
          stack_i    = i;
          stack[stack_i] = src[src_i];
          sum_r           += ((src[src_i] >>> 16) & 0xff) * (i + 1);
          sum_g           += ((src[src_i] >>> 8) & 0xff) * (i + 1);
          sum_b           += (src[src_i] & 0xff) * (i + 1);
          sum_out_r       += ((src[src_i] >>> 16) & 0xff);
          sum_out_g       += ((src[src_i] >>> 8) & 0xff);
          sum_out_b       += (src[src_i] & 0xff);
        }
        for(i = 1; i <= radius; i++)
        {
          if(i <= hm) src_i += w; // +stride

          stack_i = i + radius;
          stack[stack_i] = src[src_i];
          sum_r += ((src[src_i] >>> 16) & 0xff) * (radius + 1 - i);
          sum_g += ((src[src_i] >>> 8) & 0xff) * (radius + 1 - i);
          sum_b += (src[src_i] & 0xff) * (radius + 1 - i);
          sum_in_r += ((src[src_i] >>> 16) & 0xff);
          sum_in_g += ((src[src_i] >>> 8) & 0xff);
          sum_in_b += (src[src_i] & 0xff);
        }

        sp = radius;
        yp = radius;
        if (yp > hm) yp = hm;
        src_i = x + yp * w; // img.pix_ptr(x, yp);
        dst_i = x;               // img.pix_ptr(x, 0);
        for (y = 0; y < h; y++) {
          src[dst_i] = (int)
            ((src[dst_i] & 0xff000000) |
              ((((sum_r * mul_sum) >>> shr_sum) & 0xff) << 16) |
              ((((sum_g * mul_sum) >>> shr_sum) & 0xff) << 8) |
              ((((sum_b * mul_sum) >>> shr_sum) & 0xff)));
          dst_i += w;

          sum_r -= sum_out_r;
          sum_g -= sum_out_g;
          sum_b -= sum_out_b;

          stack_start = sp + div - radius;
          if (stack_start >= div) stack_start -= div;
          stack_i = stack_start;

          sum_out_r -= ((stack[stack_i] >>> 16) & 0xff);
          sum_out_g -= ((stack[stack_i] >>> 8) & 0xff);
          sum_out_b -= (stack[stack_i] & 0xff);

          if (yp < hm) {
            src_i += w; // stride
            ++yp;
          }

          stack[stack_i] = src[src_i];

          sum_in_r += ((src[src_i] >>> 16) & 0xff);
          sum_in_g += ((src[src_i] >>> 8) & 0xff);
          sum_in_b += (src[src_i] & 0xff);
          sum_r += sum_in_r;
          sum_g += sum_in_g;
          sum_b += sum_in_b;

          ++sp;
          if (sp >= div) sp = 0;
          stack_i = sp;

          sum_out_r += ((stack[stack_i] >>> 16) & 0xff);
          sum_out_g += ((stack[stack_i] >>> 8) & 0xff);
          sum_out_b += (stack[stack_i] & 0xff);
          sum_in_r -= ((stack[stack_i] >>> 16) & 0xff);
          sum_in_g -= ((stack[stack_i] >>> 8) & 0xff);
          sum_in_b -= (stack[stack_i] & 0xff);
        }
      }
    }
  }

  private static final int EXECUTOR_THREADS = Runtime.getRuntime().availableProcessors();
  private static final short[] stackblur_mul = {
    512, 512, 456, 512, 328, 456, 335, 512, 405, 328, 271, 456, 388, 335, 292, 512,
    454, 405, 364, 328, 298, 271, 496, 456, 420, 388, 360, 335, 312, 292, 273, 512,
    482, 454, 428, 405, 383, 364, 345, 328, 312, 298, 284, 271, 259, 496, 475, 456,
    437, 420, 404, 388, 374, 360, 347, 335, 323, 312, 302, 292, 282, 273, 265, 512,
    497, 482, 468, 454, 441, 428, 417, 405, 394, 383, 373, 364, 354, 345, 337, 328,
    320, 312, 305, 298, 291, 284, 278, 271, 265, 259, 507, 496, 485, 475, 465, 456,
    446, 437, 428, 420, 412, 404, 396, 388, 381, 374, 367, 360, 354, 347, 341, 335,
    329, 323, 318, 312, 307, 302, 297, 292, 287, 282, 278, 273, 269, 265, 261, 512,
    505, 497, 489, 482, 475, 468, 461, 454, 447, 441, 435, 428, 422, 417, 411, 405,
    399, 394, 389, 383, 378, 373, 368, 364, 359, 354, 350, 345, 341, 337, 332, 328,
    324, 320, 316, 312, 309, 305, 301, 298, 294, 291, 287, 284, 281, 278, 274, 271,
    268, 265, 262, 259, 257, 507, 501, 496, 491, 485, 480, 475, 470, 465, 460, 456,
    451, 446, 442, 437, 433, 428, 424, 420, 416, 412, 408, 404, 400, 396, 392, 388,
    385, 381, 377, 374, 370, 367, 363, 360, 357, 354, 350, 347, 344, 341, 338, 335,
    332, 329, 326, 323, 320, 318, 315, 312, 310, 307, 304, 302, 299, 297, 294, 292,
    289, 287, 285, 282, 280, 278, 275, 273, 271, 269, 267, 265, 263, 261, 259
  };

  private static final byte[] stackblur_shr = {
    9, 11, 12, 13, 13, 14, 14, 15, 15, 15, 15, 16, 16, 16, 16, 17,
    17, 17, 17, 17, 17, 17, 18, 18, 18, 18, 18, 18, 18, 18, 18, 19,
    19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 19, 20, 20, 20,
    20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 21,
    21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21,
    21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 22, 22, 22, 22, 22, 22,
    22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22,
    22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 23,
    23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23,
    23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23,
    23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23, 23,
    23, 23, 23, 23, 23, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
    24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
    24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
    24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
    24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24
  };
}
