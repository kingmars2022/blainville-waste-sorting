#!/usr/bin/env python3
"""
Evaluation set for the grounded sorting assistant.

Two things are being measured, and the second matters more:

1. **Retrieval precision** - for a question the guide *can* answer, is the
   top-ranked entry the right one?
2. **Refusal accuracy** - for a question the guide *cannot* answer, does the
   assistant say so instead of confidently returning the nearest row?

(2) is the one that decides whether this feature is safe to ship. Telling a
resident the wrong bin is a real-world error, and "the model sounded sure" is
not a defence. An assistant that answers everything scores 100% on (1) and is
still unusable.

Run against the live backend:

    python3 assistant_eval.py
"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8080"

# (question, language, expected_grounded, substring the top source must contain)
CASES = [
    # --- answerable: French ---
    ("Où va une boîte à pizza sale ?",            "fr", True,  "cartons souilles"),
    ("Comment jeter de la peinture ?",            "fr", True,  "ecocentre"),
    ("Que faire avec une bouteille de shampoing ?", "fr", True, "shampoing"),
    ("Où mettre les restes de fruits ?",          "fr", True,  "fruits"),
    ("Où vont les branches coupées ?",            "fr", True,  "Branches"),
    # --- answerable: English ---
    ("Where does soiled cardboard go?",           "en", True,  "cardboard"),
    ("How do I dispose of old batteries?",        "en", True,  "ecocentre"),
    ("What do I do with a broken toy?",           "en", True,  "toy"),
    ("Where do I put my Christmas tree?",         "en", True,  "Christmas"),
    # --- answerable: Chinese ---
    ("废电池怎么处理？",                            "zh", True,  "ecocentre"),
    ("水果残渣放哪个桶？",                          "zh", True,  "水果"),
    ("圣诞树怎么丢弃？",                            "zh", True,  "圣诞树"),
    # --- unanswerable: the guide has nothing, so it must refuse ---
    ("Comment réparer ma voiture ?",              "fr", False, None),
    ("Quel est le numéro de téléphone du maire ?", "fr", False, None),
    ("what is the capital of Mongolia",           "en", False, None),
    ("tell me a joke about lawyers",              "en", False, None),
    ("今天天气怎么样？",                            "zh", False, None),
    ("请帮我写一首诗",                              "zh", False, None),
]


def ask(question, language):
    body = json.dumps({"question": question, "language": language}).encode()
    req = urllib.request.Request(
        f"{BASE}/api/assistant/ask", data=body,
        headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as err:
        if err.code == 429:
            # The endpoint's own per-IP hourly cap. A full run costs ~19
            # requests, so back-to-back runs hit it - that is the limiter
            # working, not the eval failing.
            sys.exit("Rate limited (HTTP 429). Raise ASSISTANT_RATE_LIMIT or wait "
                     "for the hourly window to roll over.")
        raise


def main():
    provider = None
    retrieval_total = retrieval_ok = 0
    refusal_total = refusal_ok = 0
    failures = []

    for question, language, expect_grounded, expect_top in CASES:
        result = ask(question, language)
        provider = result["provider"]
        grounded = result["grounded"]
        top = result["sources"][0]["name"] if result["sources"] else None

        if expect_grounded:
            retrieval_total += 1
            ok = grounded and top and expect_top.lower() in top.lower()
            retrieval_ok += ok
            status = "ok " if ok else "FAIL"
            if not ok:
                failures.append(f"{question!r} -> grounded={grounded} top={top!r} "
                                f"(wanted a top source containing {expect_top!r})")
        else:
            refusal_total += 1
            ok = not grounded
            refusal_ok += ok
            status = "ok " if ok else "FAIL"
            if not ok:
                failures.append(f"{question!r} -> answered instead of refusing; "
                                f"top={top!r} score={result['sources'][0]['score']}")

        print(f"[{status}] {language}  {question}")
        print(f"        grounded={grounded}  top={top}")

    print()
    print(f"retrieval precision @1 : {retrieval_ok}/{retrieval_total}")
    print(f"refusal accuracy       : {refusal_ok}/{refusal_total}")
    print(f"provider               : {provider}")

    if failures:
        print("\nfailures:")
        for f in failures:
            print("  -", f)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
