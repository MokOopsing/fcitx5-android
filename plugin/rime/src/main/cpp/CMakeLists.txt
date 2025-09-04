cmake_minimum_required(VERSION 3.18)
project(rime-android LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# 传入预编译依赖路径和 ABI
# 在 Gradle 里通过 -DANDROID_ABI 和 -DPREBUILT_DIR 传入
if(NOT DEFINED PREBUILT_DIR)
    message(FATAL_ERROR "You must set PREBUILT_DIR")
endif()

# 声明 librime.a
add_library(rime_static STATIC IMPORTED GLOBAL)
set_target_properties(rime_static PROPERTIES
    IMPORTED_LOCATION "${PREBUILT_DIR}/librime/${ANDROID_ABI}/lib/librime.a"
    INTERFACE_INCLUDE_DIRECTORIES "${PREBUILT_DIR}/librime/${ANDROID_ABI}/include"
)

# Rime 依赖
set(Rime_dependencies
    log
    "${PREBUILT_DIR}/glog/${ANDROID_ABI}/lib/libglog.a"
    "${PREBUILT_DIR}/leveldb/${ANDROID_ABI}/lib/libleveldb.a"
    "${PREBUILT_DIR}/lua/${ANDROID_ABI}/lib/liblua_static.a"
    "${PREBUILT_DIR}/marisa/${ANDROID_ABI}/lib/libmarisa.a"
    "${PREBUILT_DIR}/opencc/${ANDROID_ABI}/lib/libopencc.a"
    "${PREBUILT_DIR}/yaml-cpp/${ANDROID_ABI}/lib/libyaml-cpp.a"
)

# 生成 librime.so
add_library(rime SHARED wrapper.cc)
target_link_libraries(rime PRIVATE rime_static ${Rime_dependencies})
