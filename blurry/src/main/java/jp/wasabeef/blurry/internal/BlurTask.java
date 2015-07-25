package jp.wasabeef.blurry.internal;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.view.View;

import java.lang.ref.WeakReference;

/**
 * Copyright (C) 2015 Wasabeef
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class BlurTask extends AsyncTask<Void, Void, BitmapDrawable> {

    public interface Callback {

        void done(BitmapDrawable drawable);
    }

    private Resources res;
    private WeakReference<View> captureWeakRef;
    private WeakReference<Context> contextWeakRef;
    private BlurFactor factor;
    private Callback callback;


    public static void execute(View capture, BlurFactor factor, Callback callback) {
        new BlurTask(capture, factor, callback).execute();
    }

    private BlurTask(View capture, BlurFactor factor, Callback callback) {
        captureWeakRef = new WeakReference<>(capture);
        contextWeakRef = new WeakReference<>(capture.getContext());
        this.res = capture.getResources();
        this.factor = factor;
        this.callback = callback;
    }

    @Override
    protected BitmapDrawable doInBackground(Void... params) {
        Context context = contextWeakRef.get();
        View capture = captureWeakRef.get();
        if (context != null && capture != null) {
            return new BitmapDrawable(res, Blur.rs(capture, factor));
        }

        return null;
    }

    @Override
    protected void onPostExecute(BitmapDrawable bitmapDrawable) {
        super.onPostExecute(bitmapDrawable);
        if (callback != null) {
            callback.done(bitmapDrawable);
        }
    }
}
