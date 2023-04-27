package jp.wasabeef.blurry

import android.view.View
import android.view.animation.AlphaAnimation

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
internal object Helper {
    @JvmStatic
    fun hasZero(vararg args: Int): Boolean {
        for (num in args) {
            if (num == 0) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun animate(v: View, duration: Int) {
        val alpha = AlphaAnimation(0f, 1f)
        alpha.duration = duration.toLong()
        v.startAnimation(alpha)
    }
}