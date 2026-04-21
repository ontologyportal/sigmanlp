# Sentence Analysis

## Overview

This folder contains the sentence analysis and mention extraction stage for the SUMO grounding pipeline. It takes an English sentence, analyzes its token-level structure, identifies entity heads and event heads, attaches semantic modifiers to those heads, records named entities, and builds retrieval strings that the next stage can send to the retrieval layer. This stage does not perform FAISS retrieval yet; it prepares analyzed sentence data, structured mentions, named-entity metadata, and retrieval strings for downstream grounding.

Overview block diagram:

```text
Sentence
  |
  v
sentence_analyzer.py
  |
  v
AnalyzedSentence
  |
  v
mention_extractor.py
  |
  v
Head mentions with modifiers
  |
  v
retrieval_query_builder.py
  |
  v
Context-aware retrieval queries ready for retrieval
```

## Running It

Before running the demo:

- `python3` must be available
- `spaCy` must be installed
- the spaCy English model `en_core_web_sm` must be installed

Install the default English model with:

```bash
python3 -m spacy download en_core_web_sm
```

Interactive demo command:

```bash
python3 src/main/java/com/articulate/nlp/semanticRetrieval/sentenceAnalysis/scripts/run_sentence_analysis_demo.py
```

One-shot demo command:

```bash
python3 src/main/java/com/articulate/nlp/semanticRetrieval/sentenceAnalysis/scripts/run_sentence_analysis_demo.py \
  "The very quick fox moved silently."
```

Inside the interactive session:

- type a sentence to analyze it immediately
- `help` shows the available commands
- `list models` shows the known English spaCy models and whether they are installed
- `set model=en_core_web_sm` changes the active spaCy model
- `quit` or `exit` leaves the session

Optional:

- `--spacy-model` sets the initial spaCy model before one-shot or interactive execution
- common English pipeline names are `en_core_web_sm`, `en_core_web_md`, `en_core_web_lg`, and `en_core_web_trf`

## Analyze Sentence

The stable sentence-analysis interface is:

```text
analyze_sentence(sentence: str) -> AnalyzedSentence
```

The current implementation is `SpacySentenceAnalyzer`, which uses spaCy for tokenization, lemmatization, POS tagging, dependency parsing, noun chunk extraction, named-entity recognition, root-token detection, and syntactic head-token links. The analyzer is intentionally separated behind the `SentenceAnalyzer` interface so a future backend can be added without changing downstream mention extraction or retrieval query building.

`AnalyzedSentence` contains:

- original sentence text
- tokens
- lemmas
- POS tags
- dependency labels
- head token indices
- noun chunks
- named entities
- root token index

Relevant interfaces and types:

- `SentenceAnalyzer`
- `SpacySentenceAnalyzer`
- `analyze_sentence(...)`
- `AnalyzedSentence`

## Extract Mentions

The current extractor is `RuleBasedMentionExtractor`. It produces structured head mentions from the analyzed sentence using deterministic rules.

The extractor currently includes:

- entity heads:
  - pronouns
  - noun-chunk heads
  - uncovered nouns outside noun chunks
- event heads:
  - the sentence root verb when the root token is a verb or auxiliary
  - conjunct verbs
  - clausal verbs such as `xcomp`, `ccomp`, `advcl`, and `relcl` when their POS is `VERB` or `AUX`
- noun modifiers:
  - attributive adjectives such as `wild` in `the wild elephant`
  - adverbs attached to those adjectives, such as `very` in `the very quick fox`
  - predicate adjectives attached through weak `be` support verbs, such as `scary` in `animals were scary`
- verb modifiers:
  - adverbs directly attached to the verb, such as `silently` in `moved silently`
- named-entity metadata:
  - head mentions that overlap spaCy named entities are marked with the entity label
  - `PERSON` mentions are marked so the retrieval layer can later bypass dense retrieval

Deduplication is deterministic and span-based. If more than one rule captures the same head token span, only one head mention is kept.

Head `mention_type` values:

- `pronoun`
- `noun`
- `verb`

Modifier `mention_type` values:

- `adjective`
- `adverb`

Head `source` values:

- `named_entity`
- `pronoun_rule`
- `noun_chunk`
- `noun_token`
- `root_verb`
- `verb_token`

Modifier `source` values:

- `adjective_token`
- `predicate_adjective`
- `adverb_token`

Current noun-head behavior:

- retrieval runs on the noun head, not the full noun phrase
- this is why `the bank` becomes the head `bank`
- this is why `the very quick fox` becomes the head `fox`
- noun compounds can still become a lexicalized entity target, so `the coffee table` retrieves as `coffee table` while keeping `table` as the syntactic head internally
- chained compounds are supported, so a phrase like `the kitchen cabinet door` can retrieve as `kitchen cabinet door`
- parallel compounds attached directly to the same head are also supported, so `the soda pop cap` can retrieve as `soda pop cap`
- modifiers are attached to the head instead of being emitted as separate retrieval units

Current deduplication preference:

- `named_entity` is preferred when a head overlaps a spaCy named entity
- `noun_chunk` is preferred over `noun_token`
- `root_verb` is reserved as the highest-priority verb source
- `verb_token` is used for non-root verbal mentions such as conjunct and clausal verbs

Current support-verb behavior:

- weak forms of `be` used mainly as auxiliary or copular support are not treated as the primary semantic mention
- in sentences like `animals were scary`, the extractor keeps the entity head `animals`
- the predicate adjective `scary` is attached as a modifier to that entity head
- the weak support verb `were` is omitted so retrieval stays focused on the semantically important head terms

Relevant interfaces and types:

- `Mention`
- `ModifierMention`
- `RuleBasedMentionExtractor`
- `extract_mentions(...)`

## Build Retrieval Queries

The current retrieval string builder returns only the resolved term for each mention. This is an intentionally simple retrieval input used by the next stage.

For entity heads, the builder prefers:

- the entity `retrieval_target` when present
- otherwise the surface text

For event heads, the builder prefers:

- the gerund-style `retrieval_target` when available
- otherwise normalized text
- otherwise surface form

Verb target selection still prefers a gerund-like retrieval target intended to better match SUMO termFormats. Examples include:

- `sit` -> `sitting`
- `walk` -> `walking`
- `ride` -> `riding`
- `kick` -> `kicking`

If a gerund-like target cannot be derived safely, the builder falls back to the normalized verb text.

Relevant interface:

- `build_context_aware_retrieval_query(...)`
- `build_verb_retrieval_target(...)`

## Example Output

Example sentence:

```text
He sat on the bank of the river.
```

Expected head mentions approximately:

- `He`
- `sat`
- `bank`
- `river`

Example retrieval strings:

`He`

```text
He
```

`sat`

```text
sitting
```

`bank`

```text
bank
```

`river`

```text
river
```

Example named-entity behavior:

```text
John kicked the ball.
```

- `John` is detected as `PERSON`
- the head mention keeps `named_entity=PERSON`
- the next retrieval stage can use that metadata to bypass dense retrieval and map directly to `Human`
- pronouns such as `I`, `we`, and `they` are not handled by spaCy NER; they remain pronoun heads and can be mapped later by rule in retrieval orchestration

Another example sentence:

```text
I put the book on the coffee table.
```

Expected structured output includes at least:

- entity head `book`
- entity head `coffee table`
- event head `putting`

Another example sentence:

```text
The soda pop cap twisted counter-clockwise.
```

Expected structured output includes at least:

- entity head `soda pop cap`

Another example sentence:

```text
The kitchen cabinet door broke.
```

Expected structured output includes at least:

- entity head `kitchen cabinet door`

Another example sentence:

```text
The wild elephant was ridden on by an anthropologist who thought animals were scary.
```

Expected structured output includes at least:

- entity head `elephant` with modifier `wild`
- event head `riding`
- event head `thinking`
- entity head `animals` with modifier `scary`

Another example sentence:

```text
The very quick fox moved silently.
```

Expected structured output includes at least:

- entity head `fox` with modifiers `quick` and `very`
- event head `moving` with modifier `silently`

## Current Constraints

- `spaCy` is required and is not bundled with this repo
- the default English model `en_core_web_sm` must be installed separately
- there is no GLiNER backend yet
- lexical scoring is not implemented in this stage
- FAISS retrieval is not implemented in this stage
- the current implementation expects a single sentence input
- retrieval queries are only built for entity and event heads, not for standalone modifiers
- this stage is designed to feed the later retrieval layer directly
