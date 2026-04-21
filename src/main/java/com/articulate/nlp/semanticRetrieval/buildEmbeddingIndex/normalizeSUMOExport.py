from __future__ import annotations

import sys
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
UTILS_DIR = SCRIPT_DIR.parent / "utils"
if str(UTILS_DIR) not in sys.path:
    sys.path.insert(0, str(UTILS_DIR))

from normalization import EnglishTextNormalizer


def main(argv: list[str]) -> int:
    if len(argv) > 3:
        print(
            "Usage: python3 normalizeOntologyExport.py [input_jsonl] [output_jsonl]",
            file=sys.stderr,
        )
        return 1

    input_path = argv[1] if len(argv) >= 2 else "ontology-export.jsonl"
    output_path = argv[2] if len(argv) == 3 else "ontology-export.normalized.jsonl"

    EnglishTextNormalizer().write_normalized_ontology_jsonl(
        input_path,
        output_path,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
