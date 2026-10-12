"""Read-only, diff-selected documentation map. Python standard library only."""
import argparse
import fnmatch
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def git_paths(root, *args):
    result = subprocess.run(
        ['git', '-C', str(root), *args], check=True, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return result.stdout.decode('utf-8').rstrip('\0').split('\0') if result.stdout else []


def changed_paths(root, base=None):
    paths = git_paths(root, 'diff', '--name-only', '-z', '--')
    paths += git_paths(root, 'diff', '--cached', '--name-only', '-z', '--')
    paths += git_paths(root, 'ls-files', '--others', '--exclude-standard', '-z')
    if base:
        if base.startswith('-'):
            raise ValueError('base must be a Git revision, not an option')
        paths += git_paths(root, 'diff', '--name-only', '-z', base, '--')
    return sorted(set(paths))


def normalize(root, name):
    path = Path(name.replace('\\', '/'))
    absolute = (root / path).resolve()
    return absolute.relative_to(root.resolve()).as_posix()


def select_context(root, paths, rules):
    selected = {'AGENTS.md': {'workspace'}}
    for name in paths:
        parent = (root / name).parent
        while parent != root:
            guide = parent / 'AGENTS.md'
            if guide.is_file():
                selected.setdefault(guide.relative_to(root).as_posix(), set()).add('scope')
            parent = parent.parent
        for rule in rules:
            if any(fnmatch.fnmatchcase(name, p) for p in rule['paths']):
                for doc in rule['docs']:
                    selected.setdefault(doc, set()).add(rule['name'])
    return selected


def check_map(root, rules):
    errors = []
    for rule in rules:
        for name in rule['docs']:
            try:
                if not (root / normalize(root, name)).is_file():
                    errors.append('Missing document: ' + name)
            except ValueError:
                errors.append('Document outside workspace: ' + name)
    # Use Git's inventory to avoid build/runtime/dependency trees; includes new files.
    inventory = git_paths(root, 'ls-files', '--cached', '--others', '--exclude-standard', '-z')
    for name in sorted(set(inventory)):
        if Path(name).name != 'AGENTS.md' or not (root / name).is_file():
            continue
        for target in re.findall(r'\[[^\]]+\]\(([^)]+)\)', (root / name).read_text(encoding='utf-8')):
            if '://' in target or target.startswith('#'):
                continue
            target = target.split('#', 1)[0]
            if not ((root / name).parent / target).is_file():
                errors.append(f'{name}: missing link {target}')
    return errors


def file_attributes(root, names):
    if not names:
        return {}
    result = subprocess.run(
        ['git', '-C', str(root), 'check-attr', '-z', '--stdin', 'text', 'eol'],
        input=('\0'.join(names) + '\0').encode('utf-8'), capture_output=True, check=True,
    )
    fields = result.stdout.decode('utf-8').rstrip('\0').split('\0')
    attributes = {}
    for i in range(0, len(fields), 3):
        name, attribute, value = fields[i:i + 3]
        attributes.setdefault(name, {})[attribute] = value
    return attributes


def check_whitespace(root):
    errors = []
    for args in [('diff', '--check'), ('diff', '--cached', '--check')]:
        result = subprocess.run(['git', '-C', str(root), *args], capture_output=True)
        if result.returncode:
            detail = (result.stdout + result.stderr).decode('utf-8', errors='replace').strip()
            errors.append(f'git {" ".join(args)} failed ({result.returncode}):\n{detail}')
    untracked = set(git_paths(root, 'ls-files', '--others', '--exclude-standard', '-z'))
    names = sorted(set(git_paths(root, 'ls-files', '-z')) | untracked)
    attributes = file_attributes(root, names)
    for name in names:
        attrs = attributes.get(name, {})
        if attrs.get('text') == 'unset' or (name not in untracked and attrs.get('eol') != 'lf'):
            continue
        path = root / normalize(root, name)
        if path.is_symlink() or not path.is_file():
            continue
        data = path.read_bytes()
        if b'\0' in data:
            continue
        try:
            lines = data.decode('utf-8').split('\n')
        except UnicodeDecodeError:
            continue
        if attrs.get('eol') == 'lf' and b'\r\n' in data:
            errors.append(f'{name}: CRLF line endings (declared eol=lf)')
        if name in untracked:
            for number, line in enumerate(lines, 1):
                if line.removesuffix('\r').endswith((' ', '\t')):
                    errors.append(f'{name}:{number}: trailing whitespace in new text file')
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', nargs='+', help='Explicit paths relative to repository root')
    parser.add_argument('--base', help='Include changes against a Git revision, plus local changes')
    parser.add_argument('--check', action='store_true', help='Check links and staged/unstaged/new-file whitespace')
    args = parser.parse_args()
    try:
        rules = json.loads((ROOT / 'tools/context_rules.json').read_text(encoding='utf-8'))
        errors = check_map(ROOT, rules)
        if args.check:
            errors.extend(check_whitespace(ROOT))
        if errors:
            print('\n'.join(errors), file=sys.stderr)
            return 1
        if args.check:
            print('OK: context links and staged/unstaged/untracked-text whitespace checks passed')
            return 0
        paths = sorted({normalize(ROOT, p) for p in (args.files or changed_paths(ROOT, args.base))})
        print(f'{len(paths)} changed/requested files. Read only the guidance relevant to your task:')
        for doc, reasons in select_context(ROOT, paths, rules).items():
            print(f'- {doc} [{", ".join(sorted(reasons))}]')
        return 0
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as error:
        print(f'Context lookup failed: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
