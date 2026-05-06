cmake_minimum_required(VERSION 3.10)
project(native_shim)

# Ensure the shared linker produces ELF PT_LOAD segment alignment compatible with
# 16 KB page sizes required by some devices / Google Play uploads. The linker flag
# below requests a 16KB page size when creating shared objects. If your linker
# does not support --page-size, you may need to upgrade the NDK or switch to a
# different linker (lld). This is a best-effort setting to make the produced
# .so files pass the Play Console check for 16 KB device compatibility.
## Prefer GNU gold linker for this target when available and request a 16KB page size.
## clang/ld.lld in some NDK releases does not accept --page-size; gold supports it.
## (Removed forcing -fuse-ld=gold) don't force a specific linker; instead we
## pass a page-size option only when targeting Android and when CMake supports
## doing so. For many NDK toolchains passing -Wl,--page-size=16384 is sufficient.

add_library(native_shim SHARED
    src/main/cpp/native_shim.cpp
)

find_library(log-lib log)

target_link_libraries(native_shim
    ${log-lib}
    dl
)

# Apply page-size linker option for Android targets only. Use target_link_options
# when available to avoid affecting other targets; otherwise fall back to
# CMAKE_SHARED_LINKER_FLAGS.
if(ANDROID)
    if (CMAKE_VERSION VERSION_GREATER_EQUAL "3.18")
        target_link_options(native_shim PRIVATE "-Wl,-z,max-page-size=16384")
    else()
        set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
    endif()
endif()

