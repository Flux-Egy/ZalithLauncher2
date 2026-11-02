# NDK_TOOLCHAIN_VERSION := 4.9
APP_PLATFORM := android-21
APP_STL := c++_shared
# APP_ABI := armeabi-v7a arm64-v8a x86 x86_64

# Android 15+ devices may use a 16 KB memory page size. Shared libraries whose
# ELF LOAD segments are aligned to less than 16 KB fail to load there.
# NDK r25 still defaults to 4 KB, so request 16 KB alignment explicitly for every
# module built by this ndk-build project (harmless on 4 KB devices and 32-bit ABIs).
# Verified in CI by scripts/check-16kb-alignment.sh.
APP_LDFLAGS += -Wl,-z,max-page-size=16384
