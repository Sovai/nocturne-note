#!/usr/bin/env python3
"""Free, self-hosted OmniVoice bridge for Nocturne Note.

Runs inference on your PC (GPU recommended) and streams 24kHz mono WAV back to
your Android phone over a trusted LAN. Never exposes reference-audio uploads,
filesystem paths or arbitrary model loading to clients.

Setup:
  python -m pip install omnivoice torch soundfile numpy
  python tools/omnivoice_server.py --host 0.0.0.0 --port 8765 --token YOUR_LONG_RANDOM_TOKEN
  # Optionally: --reference-wav C:\voice\reference.wav --reference-text "..."
  # Then set http://PC_LAN_IP:8765 and token under Nocturne Note Settings.

Do NOT port-forward to the internet. Use only a private, trusted network.
OmniVoice model weights carry their own license; verify before commercial use.
"""
import argparse
import io
import json
import os
import secrets
import threading
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import numpy as np

MAX_BODY = 16 * 1024
MAX_TEXT = 600
VOICE_STYLES = {
    "auto": None,
    "male": "male, low pitch",
    "female": "female, medium pitch",
    "elder": "male, elderly, low pitch",
    "soft": "female, soft, low pitch",
}


def synthesize_wave(model, text: str, style: str, speed: float, clone_prompt=None) -> bytes:
    kwargs = {"text": text, "speed": speed, "num_step": 16}
    if clone_prompt is not None:
        kwargs["voice_clone_prompt"] = clone_prompt
    elif VOICE_STYLES[style]:
        kwargs["instruct"] = VOICE_STYLES[style]
    generated = model.generate(**kwargs)
    samples = np.asarray(generated[0], dtype=np.float32).reshape(-1)
    samples = np.clip(np.nan_to_num(samples), -1.0, 1.0)
    pcm = (samples * 32767).astype("<i2").tobytes()
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(24000)
        wav.writeframes(pcm)
    return buffer.getvalue()


def make_handler(model, token: str, clone_prompt=None):
    inference_lock = threading.Lock()

    class Handler(BaseHTTPRequestHandler):
        def send_json(self, status, obj):
            body = json.dumps(obj).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def authorized(self):
            provided = self.headers.get("X-Nocturne-Token", "")
            if not secrets.compare_digest(provided, token):
                self.send_json(401, {"error": "Invalid server token"})
                return False
            return True

        def do_GET(self):
            if not self.authorized():
                return
            if self.path != "/health":
                self.send_json(404, {"error": "Not found"})
                return
            self.send_json(200, {"status": "ready", "engine": "OmniVoice", "clone": clone_prompt is not None})

        def do_POST(self):
            if not self.authorized():
                return
            if self.path != "/synthesize":
                self.send_json(404, {"error": "Not found"})
                return
            try:
                size = int(self.headers.get("Content-Length", "-1"))
                if size <= 0 or size > MAX_BODY:
                    self.send_json(413, {"error": "Invalid request size"})
                    return
                data = json.loads(self.rfile.read(size))
                text = data.get("text")
                style = data.get("voice", "auto")
                speed = float(data.get("speed", 1.0))
                if not isinstance(text, str) or not 0 < len(text) <= MAX_TEXT:
                    self.send_json(400, {"error": "Invalid text"})
                    return
                if style not in VOICE_STYLES or not 0.75 <= speed <= 1.5:
                    self.send_json(400, {"error": "Invalid voice or speed"})
                    return
                with inference_lock:
                    audio = synthesize_wave(model, text, style, speed, clone_prompt)
                self.send_response(200)
                self.send_header("Content-Type", "audio/wav")
                self.send_header("Content-Length", str(len(audio)))
                self.end_headers()
                self.wfile.write(audio)
            except (ValueError, TypeError, json.JSONDecodeError) as exc:
                self.send_json(400, {"error": str(exc)[:200]})
            except (BrokenPipeError, ConnectionResetError):
                pass
            except Exception as exc:
                print("Inference error:", repr(exc), flush=True)
                self.send_json(500, {"error": "Speech generation failed"})

        def log_message(self, format, *args):
            # No request bodies or bearer token in logs.
            print("%s %s" % (self.address_string(), format % args), flush=True)

    return Handler


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--token", default=os.environ.get("NOCTURNE_TTS_TOKEN", ""))
    parser.add_argument("--reference-wav", default=None)
    parser.add_argument("--reference-text", default=None)
    args = parser.parse_args()
    if len(args.token) < 16:
        parser.error("Set --token (at least 16 characters) or NOCTURNE_TTS_TOKEN")
    import torch
    from omnivoice import OmniVoice
    print("Loading OmniVoice onto GPU...", flush=True)
    device = "cuda:0" if torch.cuda.is_available() else "cpu"
    model = OmniVoice.from_pretrained("k2-fsa/OmniVoice", device_map=device,
                                      dtype=torch.float16 if device.startswith("cuda") else torch.float32)
    clone_prompt = None
    if args.reference_wav:
        if not args.reference_text:
            parser.error("--reference-text is required with --reference-wav")
        clone_prompt = model.create_voice_clone_prompt(
            ref_audio=args.reference_wav, ref_text=args.reference_text
        )
    server = ThreadingHTTPServer((args.host, args.port),
                                 make_handler(model, args.token, clone_prompt))
    print("OmniVoice ready on %s:%d (trusted LAN only)" % (args.host, args.port), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.server_close()


if __name__ == "__main__":
    main()
