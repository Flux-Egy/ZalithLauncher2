/*
 * Zalith Launcher 2
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.renderer.upscale

import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.TextureView
import androidx.annotation.RequiresApi
import com.movtery.zalithlauncher.utils.logging.Logger

/**
 * How the game image (rendered at the "resolution scale" size) is brought to the
 * screen size.
 */
enum class GameUpscaler {
    /** System bilinear stretch (previous behaviour) */
    OFF,

    /** Snapdragon Game Super Resolution 1 (single-pass spatial upscaler with edge sharpening) */
    SGSR,

    /** Bilinear stretch followed by AMD FidelityFX Contrast Adaptive Sharpening */
    CAS
}

/**
 * Applies a [GameUpscaler] to the game's TextureView with an AGSL RenderEffect
 * (Android 13+).
 *
 * The game already renders into a buffer of the "resolution scale" size
 * (SurfaceTexture.setDefaultBufferSize); the TextureView normally stretches it to the
 * view with bilinear filtering. For SGSR the view transform instead draws the buffer
 * 1:1 into the top-left corner, and the shader reconstructs every output pixel from
 * the low-resolution pixels. View layout and touch mapping are unchanged, and
 * TextureView.getBitmap (input magnifier) ignores the transform.
 *
 * Cost: one extra full-screen shader pass per frame at the display resolution, done
 * by the UI renderer. Whether that pays off depends on the device and the scale;
 * it is not measured here.
 */
object GameUpscalerEffect {
    private const val TAG = "GameUpscaler"

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * @param bufferWidth game render width (pixels)
     * @param bufferHeight game render height (pixels)
     * @param sharpness 0..1 (CAS only)
     */
    fun apply(view: TextureView, upscaler: GameUpscaler, bufferWidth: Int, bufferHeight: Int, sharpness: Float) {
        if (!isSupported()) return
        runCatching {
            applyApi33(view, upscaler, bufferWidth, bufferHeight, sharpness)
        }.onFailure { e ->
            Logger.warning(TAG, "Upscaler $upscaler could not be applied; using the system stretch", e)
            clear(view)
        }
    }

    fun clear(view: TextureView) {
        if (!isSupported()) return
        view.setTransform(null)
        view.setRenderEffect(null)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyApi33(view: TextureView, upscaler: GameUpscaler, inW: Int, inH: Int, sharpness: Float) {
        val outW = view.width
        val outH = view.height
        if (upscaler == GameUpscaler.OFF || outW <= 0 || outH <= 0 || inW <= 0 || inH <= 0) {
            clear(view)
            return
        }
        val upscaling = inW < outW || inH < outH
        when (upscaler) {
            GameUpscaler.SGSR -> {
                if (!upscaling) {
                    // nothing to reconstruct at 100 % or above
                    clear(view)
                    return
                }
                view.setTransform(Matrix().apply { setScale(inW.toFloat() / outW, inH.toFloat() / outH) })
                val shader = RuntimeShader(SGSR1_AGSL).apply {
                    setFloatUniform("inSize", inW.toFloat(), inH.toFloat())
                    setFloatUniform("outSize", outW.toFloat(), outH.toFloat())
                }
                view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
            }
            GameUpscaler.CAS -> {
                // sharpen the normally stretched image
                view.setTransform(null)
                val shader = RuntimeShader(CAS_AGSL).apply {
                    val s = sharpness.coerceIn(0f, 1f)
                    setFloatUniform("peak", -1f / (8f + (5f - 8f) * s))
                }
                view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
            }
            GameUpscaler.OFF -> clear(view)
        }
    }

    /**
     * AGSL port of Snapdragon Game Super Resolution 1 (sgsr1_shader_mobile.frag,
     * OperationMode 1 = RGBA, luma proxy = green).
     *
     * Copyright (c) 2023-2025, Qualcomm Innovation Center, Inc. All rights reserved.
     * SPDX-License-Identifier: BSD-3-Clause (full text: res/raw/license_snapdragon_gsr.txt)
     *
     * AGSL has no textureGather and no guaranteed sampler filtering for the RenderEffect
     * input, so texels are fetched at their centers and the GLSL conventions (bottom-left
     * texture origin, gather component order) are reproduced exactly.
     */
    private const val SGSR1_AGSL = """
uniform shader content;
uniform float2 inSize;
uniform float2 outSize;

// GL texel (i, j), j counted from the bottom row, clamped to the image
float4 texelGL(float i, float j) {
    float x = clamp(i, 0.0, inSize.x - 1.0) + 0.5;
    float y = (inSize.y - 1.0 - clamp(j, 0.0, inSize.y - 1.0)) + 0.5;
    return float4(content.eval(float2(x, y)));
}

// textureGather(tex, uv, 1): green of the 2x2 footprint, GL component order
float4 gatherG(float2 uv) {
    float2 p = uv * inSize - 0.5;
    float i0 = floor(p.x);
    float j0 = floor(p.y);
    return float4(texelGL(i0, j0 + 1.0).g, texelGL(i0 + 1.0, j0 + 1.0).g,
                  texelGL(i0 + 1.0, j0).g, texelGL(i0, j0).g);
}

// textureLod(tex, uv, 0) with bilinear filtering
float3 bilinearGL(float2 uv) {
    float2 p = uv * inSize - 0.5;
    float2 f = fract(p);
    float i0 = floor(p.x);
    float j0 = floor(p.y);
    float3 a = mix(texelGL(i0, j0).rgb, texelGL(i0 + 1.0, j0).rgb, f.x);
    float3 b = mix(texelGL(i0, j0 + 1.0).rgb, texelGL(i0 + 1.0, j0 + 1.0).rgb, f.x);
    return mix(a, b, f.y);
}

float fastLanczos2(float x) {
    float wA = x - 4.0;
    float wB = x * wA - wA;
    wA *= wA;
    return wB * wA;
}

float2 weightY(float dx, float dy, float c, float stdv) {
    float x = ((dx * dx) + (dy * dy)) * 0.55 + clamp(abs(c) * stdv, 0.0, 1.0);
    float w = fastLanczos2(x);
    return float2(w, w * c);
}

half4 main(float2 fragCoord) {
    const float edgeThreshold = 8.0 / 255.0;
    const float edgeSharpness = 2.0;
    float2 texel = 1.0 / inSize;

    // GL texture coordinate of this output pixel (origin bottom-left)
    float2 uv = float2(fragCoord.x / outSize.x, 1.0 - fragCoord.y / outSize.y);
    float3 color = bilinearGL(uv);
    float centerG = color.g;

    float2 imgCoord = uv * inSize + float2(-0.5, 0.5);
    float2 imgCoordPixel = floor(imgCoord);
    float2 coord = imgCoordPixel * texel;
    float2 pl = imgCoord - imgCoordPixel;
    float4 left = gatherG(coord);

    float edgeVote = abs(left.z - left.y) + abs(centerG - left.y) + abs(centerG - left.z);
    if (edgeVote > edgeThreshold) {
        coord.x += texel.x;

        float4 right = gatherG(coord + float2(texel.x, 0.0));
        float4 upDown;
        upDown.xy = gatherG(coord + float2(0.0, -texel.y)).wz;
        upDown.zw = gatherG(coord + float2(0.0, texel.y)).yx;

        float mean = (left.y + left.z + right.x + right.w) * 0.25;
        left = left - float4(mean);
        right = right - float4(mean);
        upDown = upDown - float4(mean);
        float centerW = centerG - mean;

        float sum = abs(left.x) + abs(left.y) + abs(left.z) + abs(left.w)
                  + abs(right.x) + abs(right.y) + abs(right.z) + abs(right.w)
                  + abs(upDown.x) + abs(upDown.y) + abs(upDown.z) + abs(upDown.w);
        float stdv = 2.181818 / max(sum, 1e-5);

        float2 aWY = weightY(pl.x, pl.y + 1.0, upDown.x, stdv);
        aWY += weightY(pl.x - 1.0, pl.y + 1.0, upDown.y, stdv);
        aWY += weightY(pl.x - 1.0, pl.y - 2.0, upDown.z, stdv);
        aWY += weightY(pl.x, pl.y - 2.0, upDown.w, stdv);
        aWY += weightY(pl.x + 1.0, pl.y - 1.0, left.x, stdv);
        aWY += weightY(pl.x, pl.y - 1.0, left.y, stdv);
        aWY += weightY(pl.x, pl.y, left.z, stdv);
        aWY += weightY(pl.x + 1.0, pl.y, left.w, stdv);
        aWY += weightY(pl.x - 1.0, pl.y - 1.0, right.x, stdv);
        aWY += weightY(pl.x - 2.0, pl.y - 1.0, right.y, stdv);
        aWY += weightY(pl.x - 2.0, pl.y, right.z, stdv);
        aWY += weightY(pl.x - 1.0, pl.y, right.w, stdv);

        float finalY = aWY.y / aWY.x;
        float maxY = max(max(left.y, left.z), max(right.x, right.w));
        float minY = min(min(left.y, left.z), min(right.x, right.w));
        finalY = clamp(edgeSharpness * finalY, minY, maxY);

        float deltaY = clamp(finalY - centerW, -23.0 / 255.0, 23.0 / 255.0);
        color = clamp(color + deltaY, 0.0, 1.0);
    }
    return half4(half3(color), 1.0);
}
"""

    /**
     * AGSL port of the AMD FidelityFX CAS filter (no-scaling path, green weight),
     * applied after the normal stretch.
     *
     * Copyright (c) 2017-2019 Advanced Micro Devices, Inc. All rights reserved.
     * MIT License (full text: res/raw/license_amd_cas.txt)
     */
    private const val CAS_AGSL = """
uniform shader content;
uniform float peak; // -1 / lerp(8, 5, sharpness)

half4 main(float2 p) {
    //  a b c
    //  d e f
    //  g h i
    float3 b = float3(content.eval(p + float2(0.0, -1.0)).rgb);
    float3 d = float3(content.eval(p + float2(-1.0, 0.0)).rgb);
    float3 e = float3(content.eval(p).rgb);
    float3 f = float3(content.eval(p + float2(1.0, 0.0)).rgb);
    float3 h = float3(content.eval(p + float2(0.0, 1.0)).rgb);

    float3 mn = min(min(min(d, e), min(f, b)), h);
    float3 mx = max(max(max(d, e), max(f, b)), h);
    float3 amp = sqrt(clamp(min(mn, 1.0 - mx) / max(mx, float3(1e-5)), 0.0, 1.0));
    float w = amp.g * peak;
    float rcpWeight = 1.0 / (1.0 + 4.0 * w);
    float3 outColor = clamp((b * w + d * w + f * w + h * w + e) * rcpWeight, 0.0, 1.0);
    return half4(half3(outColor), 1.0);
}
"""
}
