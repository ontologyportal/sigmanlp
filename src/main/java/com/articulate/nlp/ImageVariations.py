#!/usr/bin/env python3
"""
image_edit_gpt_image_1_5.py
 Reads an OpenAI API key from a local text file, then edits an input PNG using gpt-image-1.5.
 Prereqs:
  pip install --upgrade openai
 Usage:
  python ImageVariations.py \
    --prompt "Remove the background; keep the product unchanged." \
    --image-name "closer" \
    --key-file "C:/Users/YourUser/secrets/openai_api_key.txt"

"""
import argparse
import base64
import json
from datetime import datetime
from pathlib import Path
from typing import List, Optional
from openai import OpenAI

 # ---- Dummy default path (edit this to your real path) ----
DEFAULT_KEY_FILE = Path("./key/image_api_key.txt")
#  /home/jpsherwoodjpsherwood/workspace/sigmanlp/key
def read_api_key(file_path: Path) -> str:

    """
    Read an API key from a text file.
     Expected formats (either is fine):
      - the file contains ONLY the key
      - or lines like: OPENAI_API_KEY=sk-...
    """
    try:
        with open(file_path, "r") as f:
            api_key = f.read().strip()
            # print(api_key)
        return api_key
    except FileNotFoundError:
        raise FileNotFoundError(f"API key file not found: {file_path}")
    except Exception as e:
        raise RuntimeError(f"Error reading API key: {e}")


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)
 
def save_b64_png(b64_json: str, out_path: Path) -> None:
    out_path.write_bytes(base64.b64decode(b64_json))
  
def edit_image(
    prompt: str,
    image_path: str, *,
    key_file: Path = DEFAULT_KEY_FILE,
    outdir: str,
    out_filename: str,
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
    # print("."+api_key+".")
    client = OpenAI(base_url="https://llm-agents-east-resource.openai.azure.com/openai/v1",
                    api_key=api_key)
    out_dir = Path(outdir)
    # print(out_dir)
    # print(out_filename)
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

def read_json(filepath):
    """Read JSON file and return Python object."""
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
        # print(data)
    return data

def edit_json(data, edited_image_name: str, record_id: int):
    """
    Example edit: modify fields in the JSON objects.
    You can customize this logic as needed.
    """
    for item in data:
        # Example: modify the language description
        # if "language_description" in item:
        #     item["language_description"] = item["language_description"].upper()

        # Example: add another image to the list
        if "image_list" in item and item["id"]==record_id:
            item["image_list"].append(edited_image_name)

        # Example: modify logical description
        # if "logical_description" in item:
        #     item["logical_description"] += "  ; edited"

    return data


def save_json(data, filepath):
    """Save Python object back to JSON file."""
    with open(filepath, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)


# def process_json(input_file, output_file):
#     """Full pipeline: read, edit, save."""
#     data = read_json(input_file)
#     edited_data = edit_json(data)
#     save_json(edited_data, output_file)



def main() -> None:
    p = argparse.ArgumentParser(description="Edit a PNG using gpt-image-1.5 (text + image -> image).")
    # p.add_argument("--image", required=True, help="Path to input .png")
    p.add_argument("--prompt", required=True, help="Edit prompt/instructions")
    p.add_argument("--image-name", required=True, help="Name to be appended to new images")
    p.add_argument("--key-file", default=str(DEFAULT_KEY_FILE), help="Path to a .txt file containing your API key")
    p.add_argument("--outdir", default="./output_images", help="Output directory")
    p.add_argument("--out", default=None, help="Output filename (default: auto timestamp). If n>1, suffixes are added.")
    p.add_argument("--model", default="gpt-image-1.5", help="Model name (default: gpt-image-1.5)")
    p.add_argument("--quality", default="high", choices=["low", "medium", "high", "auto"], help="Output quality")
    p.add_argument("--input-fidelity", default="high", choices=["low", "high"], help="How strongly to preserve input image features")
    p.add_argument("--background", default="auto", choices=["auto", "transparent", "opaque"], help="Background handling")
    p.add_argument("--n", type=int, default=1, help="Number of images to generate (1-10)")
    args = p.parse_args()
    # process_json("./data.json", "data_updated.json")
    json_data = read_json("./data.json")
    print(f"\njson data:  {json_data}\n")
    for data_point in json_data:
        print(f"data point: {data_point}\n")
        # temp list to store original list of images to avoid infinite iteration 
        temp_list = list(data_point["image_list"])
        for image in temp_list:
            print(f"id: {data_point["id"]}\n")
            print(f"temp list: {temp_list}\n")
            print(f"image list: {data_point["image_list"]}\n")
            print(f"image: {image}\n")
            image_to_edit_path = "./"+image
            print(f"image to edit path: {image_to_edit_path}\n")
           
            edited_image_name = image[0:-4]+"_"+args.image_name+image[-4:]
            print(f"edited image name: {edited_image_name} \n")
            paths = edit_image(
                args.prompt,
                image_to_edit_path,
                key_file=Path(args.key_file),
                # where to save the edited image
                outdir="./images",
                # output of file name
                out_filename=edited_image_name,
                model=args.model,
                quality=args.quality,
                input_fidelity=args.input_fidelity,
                background=args.background,
                n=args.n,
            )
            edited_data = edit_json(json_data, edited_image_name, data_point["id"] )
            save_json(edited_data, "./data.json")
            # print("images/"+args.out)
            print("Saved:\n")
            for path in paths:
                print(f"  {path}\n")
        
 
if __name__ == "__main__":
    main()
 