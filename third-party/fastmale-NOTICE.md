# Icefall AISHELL-3 male voice replacements

Model: https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-icefall-zh-aishell3.tar.bz2
Training implementation: https://github.com/k2-fsa/icefall
Dataset: https://www.openslr.org/93/ (Apache-2.0 dataset)
Gender annotations: https://huggingface.co/datasets/AISHELL/AISHELL-3/resolve/main/spk-info.txt

The model's speakers.txt maps zero-based row indices to speaker IDs. This build exposes index 10 / SSB0073, 15 / SSB0139 and 21 / SSB0241, all male according to the dataset annotations. API IDs in the app are 2010, 2015, 2021 respectively. The model is a compact 8 kHz model, prioritizing real-time synthesis; timbre and bandwidth differ from the removed Kokoro voices.

The old Kokoro assets are no longer bundled. Existing local book files are preserved.
