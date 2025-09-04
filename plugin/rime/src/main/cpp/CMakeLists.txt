cmake_minimum_required(VERSION 3.10)
project(rime)

# 必须至少有一个源文件
add_library(rime SHARED wrapper.cc)

# 预编译库目录
set(PREBUILT_DIR ${CMAKE_SOURCE_DIR}/../../../../lib/fcitx5/src/main/cpp/prebuilt)

# include 路径
target_include_directories(rime PRIVATE
    ${PREBUILT_DIR}/include
)

# 链接依赖
target_link_libraries(rime
    ${PREBUILT_DIR}/lib/${ANDROID_ABI}/librime.a
    ${PREBUILT_DIR}/lib/${ANDROID_ABI}/libopencc.a
    ${PREBUILT_DIR}/lib/${ANDROID_ABI}/liblua_static.a
    ${PREBUILT_DIR}/lib/${ANDROID_ABI}/libleveldb.a
    ${PREBUILT_DIR}/lib/${ANDROID_ABI}/libmarisa.a
    ${PREBUILT_DIR}/lib/${ANDROID_ABI}/libyaml-cpp.a
    log
)
