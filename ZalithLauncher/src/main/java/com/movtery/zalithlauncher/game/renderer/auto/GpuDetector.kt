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

package com.movtery.zalithlauncher.game.renderer.auto

import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import androidx.annotation.WorkerThread
import com.movtery.zalithlauncher.setting.launcherMMKV
import com.movtery.zalithlauncher.utils.logging.Logger

private const val TAG = "GpuDetector"

/**
 * Detects the GPU via a throw-away GLES context and caches the result per build fingerprint
 * in the multi-process launcher MMKV, so the :game process sees the same answer without
 * creating an EGL context itself.
 */
object GpuDetector {
    private const val KEY_FINGERPRINT = "gpu_detect_fingerprint"
    private const val KEY_GL_VENDOR = "gpu_detect_gl_vendor"
    private const val KEY_GL_RENDERER = "gpu_detect_gl_renderer"
    private const val KEY_VULKAN = "gpu_detect_vulkan_1_1"

    /** Vulkan 1.1 in VkPhysicalDeviceProperties.apiVersion encoding */
    private const val VULKAN_1_1 = 0x401000

    @Volatile
    private var memory: GpuInfo? = null

    /** Cached result for this device build, or null if detection has not run yet */
    fun cached(): GpuInfo? {
        memory?.let { return it }
        return runCatching {
            val mmkv = launcherMMKV()
            if (mmkv.getString(KEY_FINGERPRINT, null) != Build.FINGERPRINT) return null
            val vulkan = if (mmkv.containsKey(KEY_VULKAN)) mmkv.getBoolean(KEY_VULKAN, false) else null
            GpuInfo.fromGlStrings(
                mmkv.getString(KEY_GL_VENDOR, ""),
                mmkv.getString(KEY_GL_RENDERER, ""),
                vulkan
            )
        }.getOrNull()?.also { memory = it }
    }

    /** Detects (if not cached for this build) and stores the result. Call off the main thread. */
    @WorkerThread
    fun detectAndCache(context: Context): GpuInfo {
        cached()?.let { return it }
        val (vendor, renderer) = queryGlStrings() ?: (null to null)
        val vulkan = runCatching {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, VULKAN_1_1)
        }.getOrNull()
        val info = GpuInfo.fromGlStrings(vendor, renderer, vulkan)
        // only cache a real answer, so a transient EGL failure is retried next start
        if (vendor != null || renderer != null) {
            runCatching {
                launcherMMKV().apply {
                    putString(KEY_GL_VENDOR, vendor.orEmpty())
                    putString(KEY_GL_RENDERER, renderer.orEmpty())
                    vulkan?.let { putBoolean(KEY_VULKAN, it) } ?: removeValueForKey(KEY_VULKAN)
                    putString(KEY_FINGERPRINT, Build.FINGERPRINT)
                }
            }
            memory = info
        }
        Logger.info(TAG, "GPU: ${info.vendor} model=${info.model} vulkan1.1=${info.vulkanSupported} (\"$vendor\" / \"$renderer\")")
        return info
    }

    /**
     * GL_VENDOR / GL_RENDERER from a temporary GLES2 pbuffer-less context, or null on failure.
     * (Moved from LocalUtils.isAdrenoGPU, which now delegates here.)
     */
    @WorkerThread
    fun queryGlStrings(): Pair<String?, String?>? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            Logger.error(TAG, "Failed to get EGL display")
            return null
        }
        if (!EGL14.eglInitialize(display, null, 0, null, 0)) {
            Logger.error(TAG, "Failed to initialize EGL")
            return null
        }
        try {
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            val attributes = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
            if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
                Logger.error(TAG, "Failed to choose an EGL config")
                return null
            }
            val context = EGL14.eglCreateContext(
                display, configs[0]!!, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
            if (context == EGL14.EGL_NO_CONTEXT) {
                Logger.error(TAG, "Failed to create EGL context")
                return null
            }
            try {
                if (!EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, context)) {
                    Logger.error(TAG, "Failed to make EGL context current")
                    return null
                }
                return GLES20.glGetString(GLES20.GL_VENDOR) to GLES20.glGetString(GLES20.GL_RENDERER)
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroyContext(display, context)
            }
        } finally {
            EGL14.eglTerminate(display)
        }
    }
}
