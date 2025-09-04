cmake_minimum_required(VERSION 3.18)
project(rime LANGUAGES CXX)

# Android 常用设置
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# 指定依赖库路径
set(PREBUILT_DIR /path/to/prebuilt)
set(ANDROID_ABI arm64-v8a)  # 可改成 armeabi-v7a 等

# Rime 依赖库
set(Rime_dependencies
    log
    "${PREBUILT_DIR}/glog/${ANDROID_ABI}/lib/libglog.a"
    "${PREBUILT_DIR}/leveldb/${ANDROID_ABI}/lib/libleveldb.a"
    "${PREBUILT_DIR}/lua/${ANDROID_ABI}/lib/liblua_static.a"
    "${PREBUILT_DIR}/marisa/${ANDROID_ABI}/lib/libmarisa.a"
    "${PREBUILT_DIR}/opencc/${ANDROID_ABI}/lib/libopencc.a"
    "${PREBUILT_DIR}/yaml-cpp/${ANDROID_ABI}/lib/libyaml-cpp.a"
)

# 包含头文件目录（注意要加所有依赖的 include）
include_directories(
    "${PREBUILT_DIR}/glog/${ANDROID_ABI}/include"
    "${PREBUILT_DIR}/leveldb/${ANDROID_ABI}/include"
    "${PREBUILT_DIR}/lua/${ANDROID_ABI}/include"
    "${PREBUILT_DIR}/marisa/${ANDROID_ABI}/include"
    "${PREBUILT_DIR}/opencc/${ANDROID_ABI}/include"
    "${PREBUILT_DIR}/yaml-cpp/${ANDROID_ABI}/include"
    "${CMAKE_CURRENT_SOURCE_DIR}/src"  # librime 源码目录
)

# 源码文件（也可以用 file(GLOB_RECURSE ...)）
file(GLOB_RECURSE RIME_SOURCES src/*.cc)

# 构建动态库 librime.so
add_library(rime SHARED ${RIME_SOURCES})
target_link_libraries(rime ${Rime_dependencies})
