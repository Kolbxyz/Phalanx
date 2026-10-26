#!/usr/bin/env bash
# Fetches the parts of bergamot-translator's nested submodules the app builds, shallowly: Marian,
# the sentence splitter, SentencePiece and the Arm matrix kernels (ruy, with cpuinfo, and
# simd_utils). The rest (CUDA, FBGEMM, ONNX, tests, examples) is never needed. Run from the
# repository root after `git submodule update --init`.
set -euo pipefail
bergamot=app/src/main/cpp/bergamot-translator
marian=$bergamot/3rd_party/marian-dev
git -C "$bergamot" submodule update --init --depth 1 3rd_party/marian-dev 3rd_party/ssplit-cpp
git -C "$marian" submodule update --init --depth 1 \
    src/3rd_party/sentencepiece src/3rd_party/ruy src/3rd_party/simd_utils
git -C "$marian/src/3rd_party/ruy" submodule update --init --depth 1 third_party/cpuinfo
