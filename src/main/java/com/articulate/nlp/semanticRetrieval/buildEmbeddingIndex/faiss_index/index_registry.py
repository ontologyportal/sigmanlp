from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from faiss_store import FaissMetadata, load_metadata


DEFAULT_INDEX_MODEL = "nomic-embed-text"
METADATA_SUFFIX = ".metadata.json"
FAISS_SUFFIX = ".faiss"


class IndexRegistryError(ValueError):
    """Raised when index discovery or selection cannot proceed safely."""


@dataclass(frozen=True)
class IndexEntry:
    """One discovered FAISS index paired with its metadata sidecar."""

    embedding_model: str
    faiss_path: Path
    metadata_path: Path
    vector_count: int
    embedding_dimension: int


@dataclass(frozen=True)
class IndexRegistry:
    """Discovered set of FAISS indexes available inside one directory."""

    index_directory: Path
    entries_by_model: dict[str, list[IndexEntry]]
    warnings: list[str]

    def all_entries(self) -> list[IndexEntry]:
        """Return every discovered entry in stable display order."""

        entries: list[IndexEntry] = []
        for model_name in sorted(self.entries_by_model):
            entries.extend(
                sorted(
                    self.entries_by_model[model_name],
                    key=lambda entry: str(entry.faiss_path),
                )
            )
        return entries

    def model_names(self) -> list[str]:
        """Return discovered embedding model names in stable order."""

        return sorted(self.entries_by_model)

    def has_model(self, model_name: str) -> bool:
        """Return whether the given embedding model exists in the registry."""

        return model_name in self.entries_by_model

    def is_ambiguous(self, model_name: str) -> bool:
        """Return whether a model name maps to multiple discovered indexes."""

        return len(self.entries_by_model.get(model_name, [])) > 1

    def get_entries(self, model_name: str) -> list[IndexEntry]:
        """Return every entry for a model name, possibly empty."""

        return list(self.entries_by_model.get(model_name, []))

    def get_unambiguous_entry(self, model_name: str) -> IndexEntry:
        """Return the single usable entry for a model or raise a clear error."""

        entries = self.entries_by_model.get(model_name)
        if not entries:
            raise IndexRegistryError(
                "No FAISS index found for embedding model '{0}'.".format(model_name)
            )
        if len(entries) > 1:
            candidate_paths = ", ".join(str(entry.faiss_path) for entry in entries)
            raise IndexRegistryError(
                "Embedding model '{0}' is ambiguous across multiple indexes: {1}".format(
                    model_name,
                    candidate_paths,
                )
            )
        return entries[0]

    def get_default_active_model(self) -> str | None:
        """Choose the default active model according to the agreed rules."""

        if self.has_model(DEFAULT_INDEX_MODEL) and not self.is_ambiguous(DEFAULT_INDEX_MODEL):
            return DEFAULT_INDEX_MODEL

        unambiguous_models = [
            model_name
            for model_name, entries in self.entries_by_model.items()
            if len(entries) == 1
        ]
        if len(unambiguous_models) == 1:
            return unambiguous_models[0]
        return None


def discover_index_registry(index_directory: str) -> IndexRegistry:
    """Scan one directory for valid FAISS index + metadata pairs."""

    directory = Path(index_directory)
    if not directory.exists():
        raise IndexRegistryError(
            "Index directory does not exist: {0}".format(directory)
        )
    if not directory.is_dir():
        raise IndexRegistryError(
            "Index directory path is not a directory: {0}".format(directory)
        )

    entries_by_model: dict[str, list[IndexEntry]] = {}
    warnings: list[str] = []

    metadata_paths = sorted(directory.glob("*" + METADATA_SUFFIX))
    for metadata_path in metadata_paths:
        try:
            metadata = load_metadata(str(metadata_path))
            faiss_path = _derive_faiss_path(metadata_path)
            if not faiss_path.exists() or not faiss_path.is_file():
                warnings.append(
                    "Skipping {0}: matching FAISS index not found at {1}".format(
                        metadata_path,
                        faiss_path,
                    )
                )
                continue

            entry = _build_index_entry(metadata, metadata_path, faiss_path)
            entries_by_model.setdefault(entry.embedding_model, []).append(entry)
        except Exception as exc:
            warnings.append(
                "Skipping {0}: {1}".format(metadata_path, exc)
            )

    if not entries_by_model:
        raise IndexRegistryError(
            "No valid FAISS indexes found in directory: {0}".format(directory)
        )

    return IndexRegistry(
        index_directory=directory,
        entries_by_model=entries_by_model,
        warnings=warnings,
    )


def _derive_faiss_path(metadata_path: Path) -> Path:
    if not metadata_path.name.endswith(METADATA_SUFFIX):
        raise IndexRegistryError(
            "Metadata file does not end with '{0}': {1}".format(
                METADATA_SUFFIX,
                metadata_path,
            )
        )
    return metadata_path.with_name(
        metadata_path.name[: -len(METADATA_SUFFIX)] + FAISS_SUFFIX
    )


def _build_index_entry(
    metadata: FaissMetadata,
    metadata_path: Path,
    faiss_path: Path,
) -> IndexEntry:
    if not metadata.embedding_model.strip():
        raise IndexRegistryError(
            "Metadata file {0} has an empty embedding_model.".format(metadata_path)
        )

    return IndexEntry(
        embedding_model=metadata.embedding_model,
        faiss_path=faiss_path,
        metadata_path=metadata_path,
        vector_count=metadata.vector_count,
        embedding_dimension=metadata.embedding_dimension,
    )
