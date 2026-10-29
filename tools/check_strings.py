"""Checks myCar's translations against English: same keys, same placeholders, plurals with the
forms each language needs, apostrophes and quotes escaped. Run after every change to res/values*."""
import re, glob, os, sys
import xml.etree.ElementTree as ET

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")
LANGS = ["it", "es", "de", "fr", "pt", "el", "ru", "zh"]
NEEDED = {"ru": {"one", "few", "many", "other"}, "zh": {"other"}}
FMT = re.compile(r"%(\d+\$)?[sd]")


def load(folder):
    strings, plurals, fixed = {}, {}, set()
    for path in sorted(glob.glob(os.path.join(folder, "strings*.xml"))):
        raw = open(path, encoding="utf-8").read()
        for key, text in re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', raw, re.S):
            strings[key] = text
        for key in re.findall(r'<string name="([^"]+)"[^>]*translatable="false"', raw):
            fixed.add(key)
        root = ET.fromstring(raw.encode("utf-8"))
        for e in root:
            if e.tag == "plurals":
                plurals[e.get("name")] = {i.get("quantity"): i.text or "" for i in e}
    return strings, plurals, fixed


base, base_plurals, fixed = load(os.path.join(RES, "values"))
problems = 0
for key, text in base.items():
    if re.search(r"(?<!\\)'", text) or re.search(r'(?<!\\)"', text):
        print("en", key, "apostrophe or quote without a backslash"); problems += 1
for lang in LANGS:
    strings, plurals, _ = load(os.path.join(RES, f"values-{lang}"))
    expected = set(base) - fixed
    if set(strings) != expected:
        print(lang, "missing:", sorted(expected - set(strings)), "extra:", sorted(set(strings) - expected)); problems += 1
    for key, text in strings.items():
        if key in base and sorted(FMT.findall(text)) != sorted(FMT.findall(base[key])):
            print(lang, key, "placeholders differ:", text); problems += 1
        if re.search(r"(?<!\\)'", text) or re.search(r'(?<!\\)"', text):
            print(lang, key, "apostrophe or quote without a backslash"); problems += 1
    if set(plurals) != set(base_plurals):
        print(lang, "plurals differ:", sorted(set(plurals) ^ set(base_plurals))); problems += 1
    for name, forms in plurals.items():
        need = NEEDED.get(lang, {"one", "other"})
        if not need <= set(forms):
            print(lang, name, "forms", sorted(forms), "need", sorted(need)); problems += 1
print(f"{len(base)} strings, {len(base_plurals)} plurals, {len(LANGS)} languages: " + ("all good" if not problems else f"{problems} problems"))
sys.exit(1 if problems else 0)
