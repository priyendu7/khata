#!/usr/bin/env python3
"""Fail on user-facing text written directly in Compose code instead of strings.xml.

Every string the user sees must be a resource so it has a Hindi version (values-hi/). Android lint
catches a missing translation, but not a literal like Text("Hello"). This script flags, in
src/main Kotlin files that use Compose:
  - Text("..."), Text(text = "...")
  - text/title/body/label/placeholder/contentDescription = "..."
Only literals containing a letter count, ignoring templates and format codes ("%d:%02d", "$n" and
"-" are fine). Skipped: tests, files named
*Preview.kt, and lines ending with `// allow-hardcoded-text: <reason>`.

Usage: check-hardcoded-text.py [root]   (default: current directory)
"""
import pathlib
import re
import sys

ARGS = r"(?:text|title|body|label|placeholder|contentDescription)"
PATTERNS = [
    re.compile(r'\bText\(\s*(?:text\s*=\s*)?("(?:[^"\\\n]|\\.)*")'),
    re.compile(r'\b' + ARGS + r'\s*=\s*("(?:[^"\\\n]|\\.)*")'),
]
LETTER = re.compile(r"[A-Za-zऀ-ॿ]")
TEMPLATE = re.compile(r"\$\{[^}]*\}|\$[A-Za-z_]\w*")
FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")
ESCAPE_HATCH = "allow-hardcoded-text:"


def is_text(literal: str) -> bool:
    return bool(LETTER.search(FORMAT.sub("", TEMPLATE.sub("", literal[1:-1]))))


def main() -> int:
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    problems = []
    for path in sorted(root.glob("**/src/main/**/*.kt")):
        if "/build/" in path.as_posix() or path.name.endswith("Preview.kt"):
            continue
        source = path.read_text(encoding="utf-8")
        if "androidx.compose" not in source:
            continue
        lines = source.splitlines()
        for pattern in PATTERNS:
            for match in pattern.finditer(source):
                line_no = source.count("\n", 0, match.start(1)) + 1
                if ESCAPE_HATCH in lines[line_no - 1] or not is_text(match.group(1)):
                    continue
                problems.append((path.relative_to(root), line_no, match.group(1)))
    for file, line, literal in sorted(set(problems)):
        print(f"::error file={file},line={line}::Hard-coded UI text {literal}. Put it in res/values/strings.xml "
              f"with a Hindi version in res/values-hi/, and use stringResource().")
    if not problems:
        print("OK: no hard-coded UI text in Compose code.")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
