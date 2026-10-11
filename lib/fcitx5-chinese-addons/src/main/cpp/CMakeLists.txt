cmake_minimum_required(VERSION 3.18)

project(fcitx5-android-lib-fcitx5-chinese-addons VERSION ${VERSION_NAME})

# For reproducible build
add_link_options("LINKER:--hash-style=gnu,--build-id=none")

# prefab dependency
find_package(fcitx5 REQUIRED CONFIG)
get_target_property(FCITX5_CMAKE_MODULES fcitx5::cmake INTERFACE_INCLUDE_DIRECTORIES)
set(CMAKE_MODULE_PATH ${FCITX5_CMAKE_MODULES} ${CMAKE_MODULE_PATH})

find_package(ECM MODULE)
find_package(Fcitx5Core MODULE)
find_package(Fcitx5Module MODULE)

# dummy target to export src/main/cpp/cmake
add_custom_target(cmake)

# prebuilt marisa-tire, OpenCC needs it
set(Marisa_DIR "${PREBUILT_DIR}/marisa/${ANDROID_ABI}/lib/cmake/Marisa")
find_package(Marisa REQUIRED CONFIG)

# prebuilt opencc, by CONFIG: never the pkg-config FindOpenCC.cmake in fcitx5-chinese-addons/cmake
set(OpenCC_DIR "${PREBUILT_DIR}/opencc/${ANDROID_ABI}/lib/cmake/opencc")
find_package(OpenCC REQUIRED CONFIG)

# fcitx5-chinese-addons' own top level wants libime, for its pinyin and table input methods and
# pinyinhelper, all of which the app's engine replaces: only the rest is built, set up as it does
set(ADDONS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/fcitx5-chinese-addons")
# that setup is copied from its CMakeLists.txt and config.h.in as of submodule 0d3fd040: a bump that
# changes either stops here until the copy below is checked against them and these hashes updated
set(ADDONS_COPIED_FROM
    "CMakeLists.txt:3d1dd8b3f96b481b24dab38ffdc448b40344072a3c8ea53f55348eee847f6ec0"
    "config.h.in:e2777b05df83beb9ba9609b557b53e6834435e3af1076aefecacaa53b8e0970c")
foreach(file CMakeLists.txt config.h.in)
    file(READ "${ADDONS_DIR}/${file}" ADDONS_FILE)
    # a Windows checkout may have CRLF
    string(REPLACE "\r\n" "\n" ADDONS_FILE "${ADDONS_FILE}")
    string(SHA256 ADDONS_FILE_SHA256 "${ADDONS_FILE}")
    list(APPEND ADDONS_COPIED_SHA256 "${file}:${ADDONS_FILE_SHA256}")
endforeach()
if (NOT ADDONS_COPIED_SHA256 STREQUAL ADDONS_COPIED_FROM)
    message(FATAL_ERROR "fcitx5-chinese-addons changed (${ADDONS_COPIED_SHA256}): "
        "check the setup copied in ${CMAKE_CURRENT_LIST_FILE}, then update ADDONS_COPIED_FROM")
endif()
set(CMAKE_MODULE_PATH ${ECM_MODULE_PATH} "${ADDONS_DIR}/cmake" ${CMAKE_MODULE_PATH})
include(GNUInstallDirs)
# write_basic_package_version_file, for fcitx5_export_module
include(CMakePackageConfigHelpers)
find_package(Gettext REQUIRED)
include("${FCITX_INSTALL_CMAKECONFIG_DIR}/Fcitx5Utils/Fcitx5CompilerSettings.cmake")
set(ENABLE_OPENCC ON)
configure_file("${ADDONS_DIR}/config.h.in" "${CMAKE_CURRENT_BINARY_DIR}/config.h")
include_directories(${CMAKE_CURRENT_BINARY_DIR})
add_definitions(-DFCITX_GETTEXT_DOMAIN=\"fcitx5-chinese-addons\")
fcitx5_add_i18n_definition()
# what its project() would give the modules: the .conf Version= and core version, and the po/ that
# fcitx5_translate_desktop_file takes Name[zh_CN] and the like from
file(READ "${ADDONS_DIR}/CMakeLists.txt" ADDONS_TOP_LEVEL)
string(REGEX MATCH "project\\(fcitx5-chinese-addons VERSION ([0-9.]+)\\)" _ "${ADDONS_TOP_LEVEL}")
set(PROJECT_VERSION "${CMAKE_MATCH_1}")
string(REGEX MATCH "set\\(REQUIRED_FCITX_VERSION ([0-9.]+)\\)" _ "${ADDONS_TOP_LEVEL}")
set(REQUIRED_FCITX_VERSION "${CMAKE_MATCH_1}")
set(PROJECT_SOURCE_DIR "${ADDONS_DIR}")
foreach(module chttrans punctuation fullwidth)
    add_subdirectory("${ADDONS_DIR}/modules/${module}" "modules/${module}")
endforeach()
add_subdirectory("${ADDONS_DIR}/tools" tools)
add_subdirectory("${ADDONS_DIR}/po" po)
# rename to include executable in apk
set_target_properties(scel2org5 PROPERTIES OUTPUT_NAME libscel2org5.so)
