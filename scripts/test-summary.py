#!/usr/bin/env python3
"""Summarise JUnit XML results after a Gradle run.

    python scripts/test-summary.py            # host unit tests of every module
    python scripts/test-summary.py android    # instrumentation results of :app

Prints totals and every failing test with the first line of its message.
"""
import glob
import html
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main() -> int:
    android = len(sys.argv) > 1 and sys.argv[1] == "android"
    pattern = (
        "app/build/outputs/androidTest-results/connected/**/*.xml"
        if android
        else "**/build/test-results/testDebugUnitTest/*.xml"
    )
    files = glob.glob(os.path.join(ROOT, pattern), recursive=True)
    total = failed = 0
    for path in sorted(files):
        text = open(path, encoding="utf8").read()
        head = re.search(r'tests="(\d+)"[^>]*?failures="(\d+)"[^>]*?errors="(\d+)"', text)
        if not head:
            continue
        total += int(head[1])
        failed += int(head[2]) + int(head[3])
        for case in re.finditer(r'<testcase name="([^"]+)"[^>]*>\s*<(?:failure|error)(?: message="([^"]*)")?>?([^<]*)', text):
            message = html.unescape(case[2] or case[3] or "").strip().splitlines()
            print(f"FAIL {os.path.basename(path)}: {case[1]} | {message[0][:240] if message else ''}")
    print(f"{'instrumentation' if android else 'unit'} tests: {total}, failed: {failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
