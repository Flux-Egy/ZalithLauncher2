//
// Created by Vera-Firefly on 17.01.2025.
//
#include <EGL/egl.h>
#include <stdbool.h>
#include <string.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <android/dlext.h>
#include "nsbypass.h"
#include "GL/gl.h"
#include <stdio.h>
#include <vulkan/vulkan.h>

//#define ADRENO_POSSIBLE
#ifdef ADRENO_POSSIBLE

bool checkAdrenoGraphics() {
    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY || eglInitialize(eglDisplay, NULL, NULL) != EGL_TRUE) 
        return false;

    EGLint egl_attributes[] = {
        EGL_BLUE_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_RED_SIZE, 8,
        EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, 24, EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_NONE
    };

    EGLint num_configs = 0;
    if (eglChooseConfig(eglDisplay, egl_attributes, NULL, 0, &num_configs) != EGL_TRUE || num_configs == 0) {
        eglTerminate(eglDisplay);
        return false;
    }

    EGLConfig eglConfig;
    eglChooseConfig(eglDisplay, egl_attributes, &eglConfig, 1, &num_configs);

    const EGLint egl_context_attributes[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    EGLContext context = eglCreateContext(eglDisplay, eglConfig, EGL_NO_CONTEXT, egl_context_attributes);
    if (context == EGL_NO_CONTEXT) {
        eglTerminate(eglDisplay);
        return false;
    }

    if (eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, context) != EGL_TRUE) {
        eglDestroyContext(eglDisplay, context);
        eglTerminate(eglDisplay);
        return false;
    }

    const char* vendor = (const char*)glGetString(GL_VENDOR);
    const char* renderer = (const char*)glGetString(GL_RENDERER);

    bool is_adreno = (vendor && renderer && strcmp(vendor, "Qualcomm") == 0 && strstr(renderer, "Adreno") != NULL);

    eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(eglDisplay, context);
    eglTerminate(eglDisplay);

    return is_adreno;
}

/**
 * Number of physical devices the Vulkan loader in [libvulkan] reports (0 on any failure).
 * Creates and destroys a minimal Vulkan 1.0 instance.
 */
static uint32_t turnipDeviceCount(void* libvulkan) {
    PFN_vkCreateInstance createInstance = (PFN_vkCreateInstance) dlsym(libvulkan, "vkCreateInstance");
    PFN_vkEnumeratePhysicalDevices enumerateDevices =
        (PFN_vkEnumeratePhysicalDevices) dlsym(libvulkan, "vkEnumeratePhysicalDevices");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance) dlsym(libvulkan, "vkDestroyInstance");
    if (!createInstance || !enumerateDevices || !destroyInstance) return 0;

    VkApplicationInfo app_info = {
        .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pApplicationName = "ZalithTurnipProbe",
        .apiVersion = VK_API_VERSION_1_0
    };
    VkInstanceCreateInfo create_info = {
        .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        .pApplicationInfo = &app_info
    };
    VkInstance instance = VK_NULL_HANDLE;
    if (createInstance(&create_info, NULL, &instance) != VK_SUCCESS || instance == VK_NULL_HANDLE) return 0;

    uint32_t count = 0;
    VkResult result = enumerateDevices(instance, &count, NULL);
    destroyInstance(instance, NULL);
    return result == VK_SUCCESS ? count : 0;
}

void* loadTurnipVulkan(const char* driver_path, const char* native_dir, const char* cache_dir) {
    if (!checkAdrenoGraphics())
        return NULL;

    if (!native_dir || !linker_ns_load(native_dir)) return NULL;

    void* linkerhook = linker_ns_dlopen("liblinkerhook.so", RTLD_LOCAL | RTLD_NOW);
    if (!linkerhook) return NULL;

    const char* target_driver = (driver_path && strlen(driver_path) > 0) ? driver_path : "libvulkan_freedreno.so";
    void* turnip_driver_handle = linker_ns_dlopen(target_driver, RTLD_LOCAL | RTLD_NOW);

    if (!turnip_driver_handle) {
        dlclose(linkerhook);
        return NULL;
    }

    void* dl_android = linker_ns_dlopen("libdl_android.so", RTLD_LOCAL | RTLD_LAZY);
    if (!dl_android) {
        dlclose(linkerhook);
        dlclose(turnip_driver_handle);
        return NULL;
    }

    void* android_get_exported_namespace = dlsym(dl_android, "android_get_exported_namespace");
    void (*linkerhookPassHandles)(void*, void*, void*) = dlsym(linkerhook, "linker_hook_set_handles");

    if (!linkerhookPassHandles || !android_get_exported_namespace) {
        dlclose(dl_android);
        dlclose(linkerhook);
        dlclose(turnip_driver_handle);
        return NULL;
    }

    linkerhookPassHandles(turnip_driver_handle, android_dlopen_ext, android_get_exported_namespace);

    void* libvulkan = linker_ns_dlopen_unique(cache_dir, "libvulkan.so", RTLD_LOCAL | RTLD_NOW);
    if (!libvulkan) {
        dlclose(dl_android);
        dlclose(linkerhook);
        dlclose(turnip_driver_handle);
        return NULL;
    }

    // A Turnip build can load fine but not support this GPU (e.g. Mesa 24.3 on Adreno 8xx:
    // vkEnumeratePhysicalDevices returns VK_ERROR_INITIALIZATION_FAILED with no devices).
    // Returning it anyway leaves Zink / Vulkan mods with no device at all, so verify that it
    // exposes a GPU and otherwise fall back to the system driver (callers dlopen libvulkan.so).
    uint32_t device_count = turnipDeviceCount(libvulkan);
    if (device_count == 0) {
        printf("AdrenoSupp: Turnip loaded but exposes no Vulkan device on this GPU; using the system driver\n");
        dlclose(libvulkan);
        dlclose(dl_android);
        dlclose(linkerhook);
        dlclose(turnip_driver_handle);
        return NULL;
    }
    printf("AdrenoSupp: Turnip exposes %u Vulkan device(s)\n", device_count);

    return libvulkan;
}

#endif