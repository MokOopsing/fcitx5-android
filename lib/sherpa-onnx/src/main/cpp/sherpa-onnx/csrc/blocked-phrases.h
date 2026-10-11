// sherpa-onnx/csrc/blocked-phrases.h
//
// youmo: the phrases of OfflineRecognizer::CreateStream(hotwords, blocked),
// and its hotwords where there is no BPE encoder

#ifndef SHERPA_ONNX_CSRC_BLOCKED_PHRASES_H_
#define SHERPA_ONNX_CSRC_BLOCKED_PHRASES_H_

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

#include "sherpa-onnx/csrc/symbol-table.h"
#include "sherpa-onnx/csrc/utils.h"
#include "ssentencepiece/csrc/ssentencepiece.h"

namespace sherpa_onnx {

/* Whether EncodeHotwords may call the BPE encoder for `phrase`. Under
 * cjkchar+bpe it does only for a piece that starts with a letter, so a phrase
 * with no letter (all Hanzi) needs none.
 */
inline bool NeedsBpeEncoder(const std::string &phrase,
                            const std::string &modeling_unit) {
  if (modeling_unit == "cjkchar") return false;
  if (modeling_unit != "cjkchar+bpe") return true;
  return std::any_of(phrase.begin(), phrase.end(),
                     [](unsigned char c) { return std::isalpha(c) != 0; });
}

/* The lines of `hotwords` that need no BPE encoder (NeedsBpeEncoder), the
 * phrases EncodeBlockedPhrases keeps without one: under cjkchar+bpe those with
 * no letter.
 *
 * @param kept  The lines kept are appended to it, each with a '\n'.
 * @return  How many were left out; a blank line is no hotword.
 */
inline int32_t WithoutBpeHotwords(const std::string &hotwords,
                                  const std::string &modeling_unit,
                                  std::string *kept) {
  int32_t left_out = 0;
  std::istringstream all(hotwords);
  for (std::string line; std::getline(all, line);) {
    if (!NeedsBpeEncoder(line, modeling_unit)) {
      *kept += line;
      *kept += '\n';
    } else if (line.find_first_not_of(" \t\v\f\r") != std::string::npos) {
      ++left_out;
    }
  }
  return left_out;
}

/* Encodes each "/"-separated phrase of `blocked` as a hotword is, one at a
 * time: a phrase with a token the model lacks is left out, not shortened to
 * what it has (which would block something else). So is a phrase of two
 * lines, one with ':', '#' or '@' (the hotword grammar would read a score, a
 * threshold or a keyword, shortening it or throwing), and, when there is no
 * `bpe_encoder`, every phrase that needs it (NeedsBpeEncoder).
 *
 * @param phrases  The encoded phrases are appended to it.
 * @return  How many phrases were left out; a blank one is no phrase.
 */
inline int32_t EncodeBlockedPhrases(
    const std::string &blocked, const std::string &modeling_unit,
    const SymbolTable &symbol_table,
    const ssentencepiece::Ssentencepiece *bpe_encoder,
    std::vector<std::vector<int32_t>> *phrases) {
  int32_t left_out = 0;
  std::istringstream all(blocked);
  for (std::string line; std::getline(all, line, '/');) {
    // a blank one, which EncodeHotwords skips: no phrase, none left out
    if (line.find_first_not_of(" \t\n\v\f\r") == std::string::npos) continue;
    std::istringstream is(line);
    std::vector<std::vector<int32_t>> encoded;
    std::vector<float> unused_scores;
    if ((bpe_encoder != nullptr || !NeedsBpeEncoder(line, modeling_unit)) &&
        line.find_first_of(":#@") == std::string::npos &&
        EncodeHotwords(is, modeling_unit, symbol_table, bpe_encoder, &encoded,
                       &unused_scores) &&
        encoded.size() == 1) {
      phrases->push_back(std::move(encoded[0]));
    } else {
      ++left_out;
    }
  }
  return left_out;
}

}  // namespace sherpa_onnx

#endif  // SHERPA_ONNX_CSRC_BLOCKED_PHRASES_H_
