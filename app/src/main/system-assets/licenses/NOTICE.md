# Bundled components

- sherpa-onnx Android 1.12.23: Apache-2.0. Source: https://github.com/k2-fsa/sherpa-onnx/tree/v1.12.23
- Kokoro v1.1-zh, ONNX int8 conversion: Apache-2.0. Model card: https://huggingface.co/hexgrad/Kokoro-82M-v1.1-zh . Converted archive: https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2
- ONNX Runtime bundled with sherpa: MIT. https://github.com/microsoft/onnxruntime
- Kotlin standard library 1.9.25: Apache-2.0. https://github.com/JetBrains/kotlin/tree/v1.9.25
- eSpeak NG pronunciation data and backend included upstream: GPL-3.0-or-later. Corresponding upstream source: https://github.com/espeak-ng/espeak-ng and sherpa build integration: https://github.com/k2-fsa/sherpa-onnx/tree/v1.12.23/cmake

The complete Apache-2.0 text is included as sherpa-LICENSE and kokoro-LICENSE. The original model LICENSE and README are retained in the assets. This build exposes six of the 103 bundled speaker embeddings: 58/59/60 (Chinese male) and 3/4/5 (Chinese female), according to sherpa's published speaker map.

## Added flow voice

The recommended continuous-reading voice uses matcha-icefall-zh-baker and vocos-22khz-univ.onnx from the official sherpa-onnx model releases. The model card states that its training dataset is for non-commercial use only; this personal-use build retains that notice in assets/matcha/README.md and assets/licenses/matcha-MODEL-NOTICE.md. This voice is not cleared for commercial redistribution.

Sources:
https://huggingface.co/csukuangfj/matcha-icefall-zh-baker
https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/matcha-icefall-zh-baker.tar.bz2
https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos-22khz-univ.onnx
