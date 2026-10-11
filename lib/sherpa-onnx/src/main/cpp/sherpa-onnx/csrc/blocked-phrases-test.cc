// sherpa-onnx/csrc/blocked-phrases-test.cc
//
// youmo: EncodeBlockedPhrases blocks each phrase as the tokens it encodes to,
// or leaves it out whole, never a shortened one that would block another word

#include "sherpa-onnx/csrc/blocked-phrases.h"

#include <sstream>
#include <string>
#include <vector>

#include "gtest/gtest.h"
#include "sherpa-onnx/csrc/symbol-table.h"
#include "ssentencepiece/csrc/ssentencepiece.h"

namespace sherpa_onnx {

static const char kTokens[] = "<blk> 0\n开 1\n饭 2\n放 3\n";

TEST(EncodeBlockedPhrases, EachSlashSeparatedPhraseIsOneRunOfTokens) {
  SymbolTable table(std::string(kTokens), false);
  std::vector<std::vector<int32_t>> phrases;
  EXPECT_EQ(EncodeBlockedPhrases("开 放//饭", "cjkchar", table, nullptr,
                                 &phrases),
            0);
  EXPECT_EQ(phrases, (std::vector<std::vector<int32_t>>{{1, 3}, {2}}));
}

// 龘 is not in the table; a phrase of two lines would encode to two phrases
TEST(EncodeBlockedPhrases, AnUnencodablePhraseIsLeftOutWhole) {
  SymbolTable table(std::string(kTokens), false);
  std::vector<std::vector<int32_t>> phrases;
  EXPECT_EQ(EncodeBlockedPhrases("开龘/开\n饭/饭", "cjkchar", table, nullptr,
                                 &phrases),
            2);
  EXPECT_EQ(phrases, (std::vector<std::vector<int32_t>>{{2}}));
}

// read as a score or a keyword they shorten the phrase to 开; '#' throws
TEST(EncodeBlockedPhrases, APhraseWithTheHotwordGrammarIsLeftOut) {
  SymbolTable table(std::string(kTokens), false);
  std::vector<std::vector<int32_t>> phrases;
  EXPECT_EQ(EncodeBlockedPhrases("开 :2.0/开@/开#/饭", "cjkchar", table,
                                 nullptr, &phrases),
            3);
  EXPECT_EQ(phrases, (std::vector<std::vector<int32_t>>{{2}}));
}

// EncodeHotwords would call the missing encoder
TEST(EncodeBlockedPhrases, ABpeUnitWithoutItsEncoderLeavesEveryPhraseOut) {
  SymbolTable table(std::string(kTokens), false);
  std::vector<std::vector<int32_t>> phrases;
  EXPECT_EQ(EncodeBlockedPhrases("开/饭", "bpe", table, nullptr, &phrases), 2);
  EXPECT_TRUE(phrases.empty());
}

// cjkchar+bpe calls the encoder only for a piece starting with a letter
TEST(EncodeBlockedPhrases, ACjkcharBpePhraseWithNoLetterNeedsNoEncoder) {
  SymbolTable table(std::string(kTokens), false);
  std::vector<std::vector<int32_t>> phrases;
  EXPECT_EQ(EncodeBlockedPhrases("开 放/饭a/饭", "cjkchar+bpe", table, nullptr,
                                 &phrases),
            1);
  EXPECT_EQ(phrases, (std::vector<std::vector<int32_t>>{{1, 3}, {2}}));
}

// EncodeHotwords reads a phrase of spaces as no line at all
TEST(EncodeBlockedPhrases, ABlankPhraseIsNoPhrase) {
  SymbolTable table(std::string(kTokens), false);
  std::vector<std::vector<int32_t>> phrases;
  EXPECT_EQ(EncodeBlockedPhrases("开/ /\t\n/饭", "cjkchar", table, nullptr,
                                 &phrases),
            0);
  EXPECT_EQ(phrases, (std::vector<std::vector<int32_t>>{{1}, {2}}));
}

// the app's side: bpe with its bpe.vocab. 饭 is a piece only at a word's
// start, so 开饭 encodes to ▁开 and <unk>s, left out rather than blocking ▁开
TEST(EncodeBlockedPhrases, WithItsEncoderABpePhraseIsTheRunOfItsPieces) {
  SymbolTable table(std::string("<blk> 0\n▁开 1\n放 2\n▁饭 3\n"), false);
  std::istringstream vocab("▁开 -1\n放 -1\n▁饭 -1\n");
  ssentencepiece::Ssentencepiece bpe(vocab, 1);
  std::vector<std::vector<int32_t>> phrases;
  EXPECT_EQ(
      EncodeBlockedPhrases("开放/开饭/饭", "bpe", table, &bpe, &phrases), 1);
  EXPECT_EQ(phrases, (std::vector<std::vector<int32_t>>{{1, 2}, {3}}));
}

// a line is kept as EncodeBlockedPhrases keeps a phrase: the hotword
// grammar's score is no letter
TEST(WithoutBpeHotwords, ACjkcharBpeUnitKeepsTheLinesWithNoLetter) {
  std::string kept;
  EXPECT_EQ(
      WithoutBpeHotwords("开 放 :2.0\n饭a\n饭", "cjkchar+bpe", &kept), 1);
  EXPECT_EQ(kept, "开 放 :2.0\n饭\n");
}

TEST(WithoutBpeHotwords, ACjkcharUnitKeepsEveryLine) {
  std::string kept;
  EXPECT_EQ(WithoutBpeHotwords("开a\n饭", "cjkchar", &kept), 0);
  EXPECT_EQ(kept, "开a\n饭\n");
}

// a blank line is no hotword, so none left out
TEST(WithoutBpeHotwords, ABpeUnitLeavesEveryLineOutButCountsNoBlankOne) {
  std::string kept;
  EXPECT_EQ(WithoutBpeHotwords("开\n \t\n\n饭", "bpe", &kept), 2);
  EXPECT_TRUE(kept.empty());
}

}  // namespace sherpa_onnx
