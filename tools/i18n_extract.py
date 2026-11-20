#!/usr/bin/env python3
import os, re, io, sys, xml.etree.ElementTree as ET
from collections import OrderedDict

ROOT = os.path.abspath(os.path.dirname(__file__) + '/..')
SRC_DIR = os.path.join(ROOT, 'app', 'src')
VALUES = os.path.join(ROOT, 'res', 'values', 'strings.xml')
VALUES_EN_DIR = os.path.join(ROOT, 'res', 'values-en')
VALUES_EN = os.path.join(VALUES_EN_DIR, 'strings.xml')

java_files = []
for dirpath, dirnames, filenames in os.walk(SRC_DIR):
    for f in filenames:
        if f.endswith('.java'):
            java_files.append(os.path.join(dirpath, f))
java_files.sort()

# parse existing strings.xml
if not os.path.exists(VALUES):
    print('strings.xml missing', file=sys.stderr); sys.exit(1)

tree = ET.parse(VALUES)
root = tree.getroot()
existing_val_to_key = {}
existing_keys = set()
for elem in root.findall('string'):
    name = elem.get('name')
    text = elem.text or ''
    existing_val_to_key[text] = name
    existing_keys.add(name)

# find all string literals containing Hangul in java files
lit_pattern = re.compile(r'"((?:[^"\\]|\\.)*)"')
hangul_re = re.compile('[\uac00-\ud7a3]')
unique_literals = OrderedDict()  # decoded -> raw_literal_form

# helper to decode Java string escapes
import codecs

def decode_java(s):
    # s is the inside of quotes, with Java escapes
    # Replace Java-specific unicode escapes first (\uXXXX)
    def replace_uni(m):
        return chr(int(m.group(1),16))
    s2 = re.sub(r'\\u([0-9a-fA-F]{4})', replace_uni, s)
    # Now handle common escapes: \\ \", \n, \t, etc.
    s2 = s2.encode('utf-8').decode('unicode_escape')
    return s2

# Collect occurrences with raw literal (including surrounding quotes) for precise replacement
occurrences = []  # (file, raw_literal, decoded)
for jf in java_files:
    with open(jf, 'r', encoding='utf-8') as fh:
        txt = fh.read()
    for m in lit_pattern.finditer(txt):
        raw = m.group(0)  # includes quotes
        inner = m.group(1)
        # fast path: if the raw inner contains Hangul characters, take it; otherwise decode and check
        if hangul_re.search(inner):
            try:
                dec = decode_java(inner)
            except Exception:
                dec = inner
            occurrences.append((jf, raw, dec))
            if dec not in unique_literals:
                unique_literals[dec] = raw
        else:
            # maybe escaped unicode like 가; decode and test
            try:
                dec2 = decode_java(inner)
            except Exception:
                dec2 = inner
            if hangul_re.search(dec2):
                dec = dec2
                occurrences.append((jf, raw, dec))
                if dec not in unique_literals:
                    unique_literals[dec] = raw

print(f'Found {len(occurrences)} occurrences, {len(unique_literals)} unique strings')

# Build mapping from decoded string to resource key, reusing existing values where possible
mapping = {}  # decoded -> key
next_id = 1
# start index after any existing i18n_ keys
for k in existing_keys:
    if k.startswith('i18n_'):
        try:
            n = int(k.split('_',1)[1])
            next_id = max(next_id, n+1)
        except:
            pass

for dec in unique_literals.keys():
    if dec in existing_val_to_key:
        mapping[dec] = existing_val_to_key[dec]
    else:
        # generate new key
        while True:
            key = f'i18n_{next_id:04d}'
            next_id += 1
            if key not in existing_keys:
                break
        mapping[dec] = key
        existing_keys.add(key)

# Append new entries to res/values/strings.xml (text insert before </resources>)
with open(VALUES, 'r', encoding='utf-8') as fh:
    orig = fh.read()

insert_pos = orig.rfind('</resources>')
if insert_pos == -1:
    print('malformed strings.xml', file=sys.stderr); sys.exit(1)

new_entries = []
for dec, key in mapping.items():
    if dec in existing_val_to_key:
        continue
    # escape for XML
    esc = (dec.replace('&','&amp;').replace('<','&lt;').replace('>','&gt;'))
    # also preserve quotes as is
    new_entries.append(f'    <string name="{key}">{esc}</string>')

if new_entries:
    new_block = '\n'.join(new_entries) + '\n'
    new_content = orig[:insert_pos] + new_block + orig[insert_pos:]
    with open(VALUES, 'w', encoding='utf-8') as fh:
        fh.write(new_content)
    print(f'Appended {len(new_entries)} entries to {VALUES}')
else:
    print('No new entries to append')

# Create values-en strings file (copy all keys -> same Korean text as placeholder)
os.makedirs(VALUES_EN_DIR, exist_ok=True)
all_keys = {}
# reload strings.xml to include newly added
tree = ET.parse(VALUES)
root = tree.getroot()
for elem in root.findall('string'):
    name = elem.get('name')
    text = elem.text or ''
    all_keys[name] = text

lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
for k, v in all_keys.items():
    esc = (v.replace('&','&amp;').replace('<','&lt;').replace('>','&gt;'))
    lines.append(f'    <string name="{k}">{esc}</string>')
lines.append('</resources>')
with open(VALUES_EN, 'w', encoding='utf-8') as fh:
    fh.write('\n'.join(lines) + '\n')
print(f'Wrote {VALUES_EN} with {len(all_keys)} entries')

# Write AppStrings.java with fallback map
pkg = 'io.github.crashedsystem.fterm'
appstr = os.path.join(ROOT, 'app', 'src', 'io', 'github', 'crashedsystem', 'fterm', 'AppStrings.java')
with open(appstr, 'w', encoding='utf-8') as fh:
    fh.write(f'package {pkg};\n\n')
    fh.write('import android.content.Context;\n')
    fh.write('import java.util.Map;\nimport java.util.HashMap;\n\n')
    fh.write('public final class AppStrings {\n')
    fh.write('    private static Context ctx = null;\n')
    fh.write('    private static final Map<Integer, String> FALLBACK = new HashMap<>();\n')
    fh.write('    static {\n')
    for name, val in all_keys.items():
        # escape double quotes and backslashes for Java string literal
        jval = val.replace('\\', '\\\\').replace('"', '\\"')
        fh.write(f'        FALLBACK.put(R.string.{name}, "{jval}");\n')
    fh.write('    }\n\n')
    fh.write('    public static void init(Context c) { ctx = c.getApplicationContext(); }\n')
    fh.write('    public static String s(int id) {\n')
    fh.write('        if (ctx != null) return ctx.getString(id);\n')
    fh.write('        String v = FALLBACK.get(id);\n')
    fh.write('        return v != null ? v : "";\n')
    fh.write('    }\n')
    fh.write('}\n')
print(f'Wrote {appstr}')

# Now replace literals in java files with AppStrings.s(R.string.key)
replacements = 0
for jf in java_files:
    with open(jf, 'r', encoding='utf-8') as fh:
        txt = fh.read()
    changed = txt
    for raw_literal, dec in unique_literals.items():
        key = mapping.get(dec)
        if not key:
            continue
        # raw_literal is the original raw form from first file; but different files may have different escaped forms
        # So match any Java string literal whose decoded value equals dec.
        # We'll replace by scanning all string literals and decode them
    
    # Build new content by iterating matches
    def replace_match(m):
        inner = m.group(1)
        dec2 = decode_java(inner)
        if dec2 in mapping:
            return 'AppStrings.s(R.string.' + mapping[dec2] + ')'
        else:
            return m.group(0)

    changed2 = re.sub(r'"((?:[^"\\]|\\.)*)"', replace_match, txt)
    if changed2 != txt:
        with open(jf, 'w', encoding='utf-8') as fh:
            fh.write(changed2)
        replacements += 1
        print(f'Patched {jf}')

print(f'Replaced literals in {replacements} files')

# Git: create branch and commit (idempotent)
import subprocess
try:
    subprocess.check_call(['git', 'rev-parse', '--verify', 'i18n/extract-strings'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.check_call(['git', 'checkout', 'i18n/extract-strings'])
except subprocess.CalledProcessError:
    subprocess.check_call(['git', 'checkout', '-b', 'i18n/extract-strings'])
subprocess.check_call(['git', 'add', '.'])
# commit only if there are staged changes
st = subprocess.check_output(['git', 'diff', '--cached', '--name-only']).decode().strip()
if st:
    subprocess.check_call(['git', 'commit', '-m', 'i18n: extract Korean literals to res/values and AppStrings helper (auto)'])
    print('Committed to branch i18n/extract-strings')
else:
    print('No changes to commit')

