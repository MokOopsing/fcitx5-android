#!/usr/bin/env bash
# The C++ unit tests of native-lib's helpers that need neither JNI nor fcitx, run on this machine
# rather than a phone: googletest (as lib/sherpa-onnx/host-test.sh pins it, SHA-256 checked)
# compiled with the host's g++. Usage: app/src/test/cpp/host-test.sh
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
src=$(cd "$here/../../main/cpp" && pwd)
work=$(cd "$here/../../.." && pwd)/build/host-test
mkdir -p "$work"
cd "$work"

gtest=googletest-1.13.0
if [ ! -d $gtest ]; then
  curl -sSL -o $gtest.tar.gz https://github.com/google/googletest/archive/refs/tags/v1.13.0.tar.gz
  echo "ad7fdba11ea011c1d925b3289cf4af2c66a352e18d4c7264392fead75e919363  $gtest.tar.gz" | sha256sum -c --quiet
  # unpacked aside, then renamed: a tar cut short must not leave a $gtest that later runs take for whole
  rm -rf $gtest.part
  mkdir $gtest.part
  tar xzf $gtest.tar.gz -C $gtest.part
  mv $gtest.part/$gtest .
  rmdir $gtest.part
fi
[ -f libgtest.a ] || {
  g++ -std=c++17 -O1 -c -I $gtest/googletest/include -I $gtest/googletest $gtest/googletest/src/gtest-all.cc -o gtest-all.o
  g++ -std=c++17 -O1 -c -I $gtest/googletest/include $gtest/googletest/src/gtest_main.cc -o gtest_main.o
  # archived aside, then renamed, as the tar above: an ar cut short must not leave a libgtest.a taken for whole
  rm -f libgtest.a.part
  ar rcs libgtest.a.part gtest-all.o gtest_main.o
  mv libgtest.a.part libgtest.a
}

g++ -std=c++17 -O1 -I "$src" -I $gtest/googletest/include \
  "$here/utf16-to-utf8-test.cc" libgtest.a -lpthread -o utf16-to-utf8-test
./utf16-to-utf8-test
