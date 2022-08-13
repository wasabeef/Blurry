package jp.wasabeef.blurry;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RSRuntimeException;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.View;

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

    Bitmap bitmap = Bitmap.createBitmap(context.getResources().getDisplayMetrics(), width, height, Bitmap.Config.ARGB_8888);

    Canvas canvas = new Canvas(bitmap);
    canvas.scale(1 / (float) factor.sampling, 1 / (float) factor.sampling);
    Paint paint = new Paint();
    paint.setFlags(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    PorterDuffColorFilter filter =
      new PorterDuffColorFilter(factor.color, PorterDuff.Mode.SRC_ATOP);
    paint.setColorFilter(filter);
    canvas.drawBitmap(source, 0, 0, paint);

    try {
      bitmap = Blur.rs(context, bitmap, factor.radius);
    } catch (RSRuntimeException e) {
      bitmap = Blur.stack(bitmap, factor.radius, true);
    }

    if (factor.hotRect != null) {
      paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
      canvas.drawBitmap(source, factor.hotRect, factor.hotRect, paint);
    }

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
}
