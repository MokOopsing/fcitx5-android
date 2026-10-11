/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
#include "utf16-to-utf8.h"

#include <string>

#include <gtest/gtest.h>

namespace {

std::string utf8(const std::u16string &units) { return utf8FromUtf16(units.data(), units.size()); }

const std::string replacement = "\xEF\xBF\xBD";

} // namespace

TEST(Utf8FromUtf16, EncodesEachLengthAtItsBounds) {
    EXPECT_EQ(utf8(u""), "");
    // one byte, not modified UTF-8's two
    EXPECT_EQ(utf8(std::u16string(1, u'\0')), std::string(1, '\0'));
    EXPECT_EQ(utf8(u"\u007F"), "\x7F");
    EXPECT_EQ(utf8(u"\u0080"), "\xC2\x80");
    EXPECT_EQ(utf8(u"\u07FF"), "\xDF\xBF");
    EXPECT_EQ(utf8(u"\u0800"), "\xE0\xA0\x80");
    EXPECT_EQ(utf8(u"\uFFFF"), "\xEF\xBF\xBF");
    EXPECT_EQ(utf8(u"\U00010000"), "\xF0\x90\x80\x80");
    EXPECT_EQ(utf8(u"\U0010FFFF"), "\xF4\x8F\xBF\xBF");
}

TEST(Utf8FromUtf16, KeepsTheCharactersEitherSideOfTheSurrogates) {
    EXPECT_EQ(utf8(u"\uD7FF"), "\xED\x9F\xBF");
    EXPECT_EQ(utf8(u"\uE000"), "\xEE\x80\x80");
}

TEST(Utf8FromUtf16, ReadsASurrogatePairAsOneCharacter) {
    // 𠀀, the first of CJK Ext-B, which the tables have
    EXPECT_EQ(utf8(u"\U00020000"), "\xF0\xA0\x80\x80");
    EXPECT_EQ(utf8(u"a\U00020000b"), "a\xF0\xA0\x80\x80" "b");
}

TEST(Utf8FromUtf16, TurnsASurrogateWithoutItsPairIntoTheReplacementCharacter) {
    // a high one last
    EXPECT_EQ(utf8({u'a', char16_t(0xD840)}), "a" + replacement);
    // a low one first
    EXPECT_EQ(utf8({char16_t(0xDC00), u'a'}), replacement + "a");
    // a high one before a character that is no low one
    EXPECT_EQ(utf8({char16_t(0xD840), u'a'}), replacement + "a");
    // a high one before another, whose pair follows
    EXPECT_EQ(utf8({char16_t(0xD840), char16_t(0xD840), char16_t(0xDC00)}), replacement + "\xF0\xA0\x80\x80");
}
