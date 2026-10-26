# Bergamot translation: spike report

**Question:** should translation run on Mozilla's Bergamot engine (the one in Firefox's offline translations) instead of, or before, the on-device language model?

**Status:** spike only. Nothing is added to the app until this is approved, as `docs/dev/process/research-workflow.md` asks for a new dependency.

## What it is
- `browsermt/bergamot-translator` **v0.4.5** (commit `9271618`), built on the Bergamot fork of the Marian NMT engine.
- Made by the University of Edinburgh, Charles University and the University of Tartu in the EU-funded Bergamot project, together with Mozilla.
- Small per-language-pair models (Mozilla's `firefox-translations-models`), translated sentence by sentence with 8-bit integer matrix products.

## Licences (all compatible with EUPL-1.2)

| Part | Licence |
| --- | --- |
| bergamot-translator | MPL-2.0 (listed in EUPL-1.2's compatible licences) |
| marian-dev (browsermt fork) | MIT |
| ssplit-cpp (sentence splitter) | Apache-2.0, plus its prefix lists' own terms |
| sentencepiece | Apache-2.0 |
| ruy (Arm matrix kernels) | Apache-2.0. A Google *library*, not a Google *service*; no network, no Play Services. |
| simd_utils | zlib |
| intgemm (x86 only, not shipped on Arm) | MIT |
| PCRE2 (bundled by ssplit) | BSD-3 |

## Size, measured on the host
- **Build:** `cmake -G Ninja -DCMAKE_BUILD_TYPE=Release -DGIT_SUBMODULE=OFF -DCOMPILE_TESTS=OFF -DBUILD_ARCH=x86-64 -DSSPLIT_USE_INTERNAL_PCRE2=ON`. It needs only the submodules marian-dev, ssplit-cpp, sentencepiece, intgemm/ruy and simd_utils, plus no BLAS (Marian falls back to its own kernels).
- **Result:** the full engine plus its command-line front end, statically linked and stripped, is **10.8 MB** on x86_64 (4.6 MB gzip -9).

**Estimate for arm64 (not built here: no NDK in this environment):**
- The Arm build swaps intgemm for ruy plus simd_utils, so expect about **9–11 MB** for `libbergamot.so`.
- The app keeps native libraries uncompressed in the APK (page-aligned, for Android 15's 16 KB pages), so that is what the APK grows by. The download grows by roughly 4–5 MB after store compression.
- For scale: `libmatrix_sdk_ffi.so` is the largest library today, and the llama.cpp CPU variants together are a few MB.

## Models
- **Per pair and direction:** about **15–40 MB**, for example fr→en and en→fr. Each is a model file, a shortlist and a vocabulary, published on Mozilla's model repository.
- **Import:** the user would import them the way the other models are imported, checked by SHA-256 against a list in the app. They aren't reachable from here (Git LFS), so no hashes are listed yet.

## Speed
- **Not measured.** That needs an arm64 build and a model, which needs the NDK and the model files.
- **Expected (Mozilla's published figures):** a sentence in well under a second on a phone CPU, so a whole message translates in about the time the language model takes to read the prompt.
- **What it adds:** the language model, about 2.5 GB, takes several seconds per message and needs its 2 GB loaded. Bergamot needs about 50 MB of memory per pair.

## Risks
- **The Arm native path is less used.** Firefox runs Bergamot as WebAssembly; the ruy-based native build is maintained but used by fewer projects.
- **The engine is large for one feature**, about 10 MB of native code.
- **Pairs only:** no automatic detection across many languages. The app would pick the pair from the user's language and the message's script/language guess, and fall back to the language model when no pair is installed.

## Proposed integration (if approved)
1. `app/src/main/cpp/bergamot-translator` as a pinned submodule, built for arm64 only with `USE_RUY`, linked into a new `libphalanx_mt.so` with a small JNI surface: `load(model, shortlist, vocab)`, `translate(text) → text`, `free`.
2. `core/ai/translation/TranslationModelStore`, a list of language pairs, each checked by hash (`VerifiedModelFile` per file).
3. `LocalSummarizer.translate` asks Bergamot first when the pair is installed, and the language model otherwise.
4. Later: "Auto-translate this chat", translating incoming messages inline, with the original one tap away.

## Recommendation
Worth it if translation is used often. It gives near-instant translation for about 10 MB of APK plus 15–40 MB per pair the user chooses. If translation is occasional, the language model already covers it and Bergamot can wait.
