import io
import json
import os
from typing import List, Tuple

import numpy as np
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image


def _load_interpreter(model_path: str):
    try:
        # Prefer lightweight runtime if available
        from tflite_runtime.interpreter import Interpreter  # type: ignore
        return Interpreter(model_path=model_path)
    except Exception:
        # Fallback to full TensorFlow package
        from tensorflow.lite import Interpreter  # type: ignore
        return Interpreter(model_path=model_path)


def _softmax(x: np.ndarray) -> np.ndarray:
    x = x.astype(np.float32)
    x = x - np.max(x)
    e = np.exp(x)
    return e / np.sum(e)


class TFLiteClassifier:
    def __init__(self, model_path: str, labels_path: str, image_size: Tuple[int, int] = (224, 224)):
        self.image_size = image_size
        self.interpreter = _load_interpreter(model_path)
        self.interpreter.allocate_tensors()
        self.input_details = self.interpreter.get_input_details()
        self.output_details = self.interpreter.get_output_details()

        # Load labels mapping
        with open(labels_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        # Support different common formats
        if isinstance(data, dict):
            # Could be {label: idx} or {"0": "label", ...}
            # Detect by value type
            if all(isinstance(v, int) for v in data.values()):
                # {label: idx} → invert
                self.index_to_label = {int(v): str(k) for k, v in data.items()}
            else:
                # {idx: label} (keys may be strings)
                self.index_to_label = {int(k): str(v) for k, v in data.items()}
        elif isinstance(data, list):
            self.index_to_label = {i: str(lbl) for i, lbl in enumerate(data)}
        else:
            raise ValueError("Unsupported class_indices.json format")

        # Determine input dtype/scale
        self.input_dtype = self.input_details[0]["dtype"]

        # Optional toggle for MobilenetV2-style [-1,1] normalization
        self.normalize_minus1_1 = os.environ.get("NORMALIZE_MINUS1_1", "1") not in ("0", "false", "False")

    def preprocess(self, img: Image.Image) -> np.ndarray:
        img = img.convert("RGB").resize(self.image_size, Image.BILINEAR)
        arr = np.asarray(img).astype(np.float32) / 255.0  # [0,1]
        if self.normalize_minus1_1:
            arr = (arr - 0.5) * 2.0  # [-1,1]
        arr = np.expand_dims(arr, axis=0)  # [1,H,W,C]
        if self.input_dtype == np.uint8:
            arr = (np.clip((arr + 1.0) * 127.5, 0, 255) if self.normalize_minus1_1 else np.clip(arr * 255.0, 0, 255)).astype(np.uint8)
        return arr

    def predict(self, img: Image.Image, top_k: int = 3):
        inp = self.preprocess(img)
        self.interpreter.set_tensor(self.input_details[0]['index'], inp)
        self.interpreter.invoke()
        out = self.interpreter.get_tensor(self.output_details[0]['index']).squeeze()
        if out.ndim != 1:
            out = out.reshape(-1)
        probs = _softmax(out)
        idxs = np.argsort(-probs)[:top_k]
        results = [
            {
                "index": int(i),
                "label": self.index_to_label.get(int(i), str(i)),
                "confidence": float(probs[i]),
            }
            for i in idxs
        ]
        best = results[0]
        return best, results


BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.environ.get("MODEL_PATH", os.path.join(BASE_DIR, "plant_disease_mobilenetv2.tflite"))
LABELS_PATH = os.environ.get("LABELS_PATH", os.path.join(BASE_DIR, "class_indices.json"))

if not os.path.exists(MODEL_PATH):
    raise RuntimeError(f"Model not found: {MODEL_PATH}. Place 'plant_disease_mobilenetv2.tflite' next to main.py or set MODEL_PATH env var.")
if not os.path.exists(LABELS_PATH):
    raise RuntimeError(f"Labels not found: {LABELS_PATH}. Place 'class_indices.json' next to main.py or set LABELS_PATH env var.")

classifier = TFLiteClassifier(MODEL_PATH, LABELS_PATH, image_size=(224, 224))

app = FastAPI(title="PlantCare TFLite Server", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/predict")
async def predict(file: UploadFile = File(None), image: UploadFile = File(None)):
    try:
        f = file or image
        if f is None:
            raise HTTPException(status_code=400, detail="No file provided (expected field 'file' or 'image')")
        content = await f.read()
        img = Image.open(io.BytesIO(content))
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid image: {e}")

    try:
        best, top = classifier.predict(img, top_k=5)
        return {
            "best": best,
            "top_k": top,
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=int(os.environ.get("PORT", "8000")))
