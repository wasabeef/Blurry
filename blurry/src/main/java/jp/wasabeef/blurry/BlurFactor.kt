package jp.wasabeef.blurry

import android.graphics.Color
import jp.wasabeef.blurry.BlurFactor

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
class BlurFactor {
    @JvmField
    var width = 0
    @JvmField
    var height = 0
    @JvmField
    var radius = DEFAULT_RADIUS
    @JvmField
    var sampling = DEFAULT_SAMPLING
    @JvmField
    var color = Color.TRANSPARENT

    companion object {
        const val DEFAULT_RADIUS = 25
        const val DEFAULT_SAMPLING = 1
    }
}