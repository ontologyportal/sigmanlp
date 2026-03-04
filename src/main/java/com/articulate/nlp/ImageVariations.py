#!/usr/bin/env python3

"""

image_edit_gpt_image_1_5.py
 
Reads an OpenAI API key from a local text file, then edits an input PNG using gpt-image-1.5.
 
Prereqs:

  pip install --upgrade openai
 
Usage:

  python image_edit_gpt_image_1_5.py \

    --image /path/to/input.png \

    --prompt "Remove the background; keep the product unchanged." \

    --key-file "C:/Users/YourUser/secrets/openai_api_key.txt"

"""
 
import argparse

import base64

from datetime import datetime

from pathlib import Path

from typing import List, Optional
 
from openai import OpenAI
 
 
# ---- Dummy default path (edit this to your real path) ----

DEFAULT_KEY_FILE = Path("key/image_api_key.txt")
 
 
def read_api_key(key_file: Path) -> str:

    """

    Read an API key from a text file.
 
    Expected formats (either is fine):

      - the file contains ONLY the key

      - or lines like: OPENAI_API_KEY=sk-...

    """

    if not key_file.exists():

        raise FileNotFoundError(f"API key file not found: {key_file}")
    
    raw = 
    print(raw)
    print(type(raw))
    return raw
 
 
def ensure_dir(path: Path) -> None:

    path.mkdir(parents=True, exist_ok=True)
 
 
def save_b64_png(b64_json: str, out_path: Path) -> None:

    out_path.write_bytes(base64.b64decode(b64_json))
 
 
def edit_image(

    prompt: str,

    image_path: str,

    *,

    key_file: Path = DEFAULT_KEY_FILE,

    outdir: str = "./output_images",

    out_filename: Optional[str] = None,

    model: str = "gpt-image-1.5",

    quality: str = "high",

    input_fidelity: str = "high",

    background: str = "auto",

    n: int = 1,

) -> List[str]:

    """

    Edit an image using gpt-image-1.5 and save results as PNG files.

    """

    if not prompt or not prompt.strip():

        raise ValueError("prompt must be a non-empty string")
 
    img_path = Path(image_path)

    if not img_path.exists():

        raise FileNotFoundError(f"image_path not found: {image_path}")
 
    if img_path.suffix.lower() != ".png":

        raise ValueError(f"Expected a .png input (got: {img_path.suffix}).")
 
    if n < 1 or n > 10:

        raise ValueError("n must be between 1 and 10")
 
    api_key = read_api_key(key_file)
    print("."+api_key+".")
    client = OpenAI(api_key=api_key)
 
    out_dir = Path(outdir)

    ensure_dir(out_dir)
 
    if out_filename is None:

        ts = datetime.now().strftime("%Y%m%d_%H%M%S")

        out_filename = f"edited_{ts}.png"
 
    base_name = Path(out_filename).name

    if not base_name.lower().endswith(".png"):

        base_name = f"{base_name}.png"
 
    with img_path.open("rb") as f:

        result = client.images.edit(

            model=model,

            image=[f],

            prompt=prompt,

            quality=quality,

            input_fidelity=input_fidelity,

            background=background,

            n=n,

        )
 
    if not getattr(result, "data", None):

        raise RuntimeError("No image data returned by the API.")
 
    saved_paths: List[str] = []

    stem = Path(base_name).stem
 
    for i, item in enumerate(result.data, start=1):

        if not getattr(item, "b64_json", None):

            raise RuntimeError("Missing b64_json in response item; cannot save image.")
 
        out_path = out_dir / (f"{stem}.png" if n == 1 else f"{stem}_{i}.png")

        save_b64_png(item.b64_json, out_path)

        saved_paths.append(str(out_path.resolve()))
 
    return saved_paths
 
 
def main() -> None:

    p = argparse.ArgumentParser(description="Edit a PNG using gpt-image-1.5 (text + image -> image).")

    p.add_argument("--image", required=True, help="Path to input .png")

    p.add_argument("--prompt", required=True, help="Edit prompt/instructions")

    p.add_argument("--key-file", default=str(DEFAULT_KEY_FILE), help="Path to a .txt file containing your API key")

    p.add_argument("--outdir", default="./output_images", help="Output directory")

    p.add_argument("--out", default=None, help="Output filename (default: auto timestamp). If n>1, suffixes are added.")

    p.add_argument("--model", default="gpt-image-1.5", help="Model name (default: gpt-image-1.5)")

    p.add_argument("--quality", default="high", choices=["low", "medium", "high", "auto"], help="Output quality")

    p.add_argument("--input-fidelity", default="high", choices=["low", "high"], help="How strongly to preserve input image features")

    p.add_argument("--background", default="auto", choices=["auto", "transparent", "opaque"], help="Background handling")

    p.add_argument("--n", type=int, default=1, help="Number of images to generate (1-10)")

    args = p.parse_args()
 
    paths = edit_image(

        args.prompt,

        args.image,

        key_file=Path(args.key_file),

        outdir=args.outdir,

        out_filename=args.out,

        model=args.model,

        quality=args.quality,

        input_fidelity=args.input_fidelity,

        background=args.background,

        n=args.n,

    )
 
    print("Saved:")

    for path in paths:

        print(f"  {path}")
 
 
if __name__ == "__main__":

    main()
 