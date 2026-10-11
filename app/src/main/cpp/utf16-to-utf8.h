/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
#ifndef FCITX5_ANDROID_UTF16_TO_UTF8_H
#define FCITX5_ANDROID_UTF16_TO_UTF8_H

#include <cstddef>
#include <cstdint>
#include <string>

// The [n] UTF-16 [units] as UTF-8. A surrogate without its pair has no UTF-8 at all, and becomes
// U+FFFD. Needs neither JNI nor fcitx, so that app/src/test/cpp/host-test.sh compiles it alone.
inline std::string utf8FromUtf16(const char16_t *units, size_t n) {
    std::string out;
    // CJK is three bytes a unit
    out.reserve(n * 3);
    for (size_t i = 0; i < n; i++) {
        uint32_t c = units[i];
        if (c >= 0xD800 && c < 0xE000) {
            if (c < 0xDC00 && i + 1 < n && units[i + 1] >= 0xDC00 && units[i + 1] < 0xE000) {
                c = 0x10000 + ((c - 0xD800) << 10) + (units[++i] - 0xDC00);
            } else {
                c = 0xFFFD;
            }
        }
        if (c < 0x80) {
            out += static_cast<char>(c);
        } else if (c < 0x800) {
            out += static_cast<char>(0xC0 | (c >> 6));
            out += static_cast<char>(0x80 | (c & 0x3F));
        } else if (c < 0x10000) {
            out += static_cast<char>(0xE0 | (c >> 12));
            out += static_cast<char>(0x80 | ((c >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (c & 0x3F));
        } else {
            out += static_cast<char>(0xF0 | (c >> 18));
            out += static_cast<char>(0x80 | ((c >> 12) & 0x3F));
            out += static_cast<char>(0x80 | ((c >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (c & 0x3F));
        }
    }
    return out;
}

#endif // FCITX5_ANDROID_UTF16_TO_UTF8_H
