[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-Blurry-brightgreen.svg?style=flat)](https://android-arsenal.com/details/1/2192)
[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/jp.wasabeef/blurry/badge.svg)](https://search.maven.org/artifact/jp.wasabeef/blurry)

`Blurry` is an easy blur library for `Android`.

![logo](art/blurry.png)

Screenshot
---

![Demo](art/blurry.gif)

How do I use it?
---

### Setup

##### Dependencies
```groovy
repositories {
  mavenCentral()
}

dependencies {
    compile 'jp.wasabeef:blurry:4.0.1'
}
```

### Functions

**Overlay**

Parent must be ViewGroup

```kotlin
Blurry.with(activity).radius(25).sampling(2).onto(rootView)
```

**Into**  
```kotlin
// from View
Blurry.with(activity).capture(view).into(imageView)
```

```kotlin
// from Bitmap 
Blurry.with(activity).from(bitmap).into(imageView)
```

**Blur Options**

- Radius
- Down Sampling
- Color Filter
- Asynchronous Support
- Animation (Overlay Only)

```java
Blurry.with(activity)
  .radius(10)
  .sampling(8)
  .color(Color.argb(66, 255, 255, 0))
  .async()
  .animate(500)
  .onto(rootView);
```

**Get a bitmap directly**
```kotlin
// Sync
val bitmap = Blurry.with(activity)
  .radius(10)
  .sampling(8)
  .capture(findViewById(R.id.right_bottom)).get()
imageView.setImageDrawable(BitmapDrawable(resources, bitmap))

// Async
Blurry.with(activity)
  .radius(25)
  .sampling(4)
  .color(Color.argb(66, 255, 255, 0))
  .capture(findViewById(R.id.left_bottom))
  .getAsync {
    imageView.setImageDrawable(BitmapDrawable(resources, it))
  }
```

**Blur a view that contains a Google Map or other surface**

On API-26 and newer, Blurry uses PixelCopy to copy directly from the surface of the window,  
and can thus obtain a bitmap that contains a GoogleMap. Blurry automatically use PixelCopy when using  
`Blurry.with(activity)` instead of the deprecated `Blurry.with(context)`. To use get a blurred view for any API,  
something like this can be done:

```kotlin
fun runBlurry() {
    // Async to stay off UI thread
    Blurry.with(activity)
        .sampling(4) // This makes it much faster and more blurry, so less radius is needed, and less radius also makes it faster
        .radius(5)
        .capture(rootViewContainerToBlur)
        .getAsync { 
            val drawable = BitmapDrawable(target.resources, it)
            blurView.setImageDrawable(drawable)
        }
}

if (!Blurry.isSurfaceCaptureSupported) {
    // Before API-26, lets help Blurry out and capture the GoogleMap Surface into a regular ImageView, and proceed when ready
    blurryMapCaptureView.onMapCaptured {
        runBlurry()
    }
} else {
    runBlurry()
}

// Let blurryMapCaptureView cover the Google Map Fragment. 
val blurryMapCaptureView: BlurryMapCaptureView
class BlurryMapCaptureView : AppCompatImageView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private lateinit var map: GoogleMap

    fun initialize(map: GoogleMap) {
        this.map = map
    }

    fun onMapCaptured(onReadyToCaptureMapAction: () -> Unit) {
        map.snapshot { bitmap: Bitmap? ->
            visibility = VISIBLE
            setImageBitmap(bitmap)
            doOnPreDraw {
                onReadyToCaptureMapAction()
                visibility = GONE
                setImageBitmap(null)
            }
        }
    }

}```



Requirements
--------------
Android 5.+ (API 21)

Developed By
-------
Daichi Furiya (Wasabeef) - <dadadada.chop@gmail.com>

<a href="https://twitter.com/wasabeef_jp">
<img alt="Follow me on Twitter"
src="https://raw.githubusercontent.com/wasabeef/art/master/twitter.png" width="75"/>
</a>

License
-------

    Copyright (C) 2020 Wasabeef

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
