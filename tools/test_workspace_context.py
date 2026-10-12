import subprocess
import tempfile
import unittest
from pathlib import Path

from workspace_context import changed_paths, check_map, check_whitespace, normalize, select_context


class WorkspaceContextTests(unittest.TestCase):
    def setUp(self):
        test_root = Path(__file__).resolve().parents[1] / 'build' / 'context-tests'
        test_root.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=test_root)
        self.root = Path(self.temp.name).resolve()
        self.root.relative_to(test_root.resolve())
        self.addCleanup(self.temp.cleanup)
        self.git('init', '-q')

    def git(self, *args):
        return subprocess.run(['git', '-C', str(self.root), *args], check=True, capture_output=True)

    def write(self, name, text=''):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8', newline='\n')

    def test_whitespace_checks_index_even_when_worktree_is_clean(self):
        self.write('staged.md', 'bad \n')
        self.git('add', '--', 'staged.md')
        self.write('staged.md', 'good\n')
        self.assertTrue(any('diff --cached --check failed' in e for e in check_whitespace(self.root)))

    def test_whitespace_checks_unstaged_and_untracked(self):
        self.write('tracked.md', 'good\n')
        self.git('add', '--', 'tracked.md')
        self.write('tracked.md', 'bad\t\n')
        self.write('new.md', 'bad \n')
        errors = check_whitespace(self.root)
        self.assertTrue(any('git diff --check failed' in e for e in errors))
        self.assertTrue(any('new.md:1:' in e for e in errors))

    def test_clean_lf_and_binary_pass_but_new_crlf_fails(self):
        self.write('.gitattributes', '*.md text eol=lf\n')
        self.write('good.md', 'good\n')
        (self.root / 'binary.bin').write_bytes(b'\0binary \r\n')
        self.assertEqual(check_whitespace(self.root), [])
        (self.root / 'new.md').write_bytes(b'line\r\n')
        self.assertTrue(any('new.md: CRLF' in e for e in check_whitespace(self.root)))

    def test_eol_report_is_per_file_and_respects_attributes(self):
        self.write('.gitattributes', '*.md text eol=lf\n*.bat text eol=crlf\n')
        self.write('tracked.md', 'clean\n')
        self.git('add', '--', 'tracked.md')
        (self.root / 'tracked.md').write_bytes(b'first\r\nsecond\r\n')
        (self.root / 'new.bat').write_bytes(b'echo ok\r\n')
        (self.root / 'unconfigured.txt').write_bytes(b'allowed\r\n')
        self.assertEqual(check_whitespace(self.root), ['tracked.md: CRLF line endings (declared eol=lf)'])

    def test_staged_unstaged_untracked_and_unicode(self):
        self.write('暂存 文件.md', 'staged')
        self.git('add', '--', '暂存 文件.md')
        self.write('暂存 文件.md', 'also unstaged')
        self.write('new.md')
        self.assertEqual(changed_paths(self.root), ['new.md', '暂存 文件.md'])

    def test_scoped_guides_and_rules_are_deduplicated(self):
        self.write('src/AGENTS.md')
        self.write('src/client/AGENTS.md')
        rules = [{'name': 'client', 'paths': ['src/client/*'], 'docs': ['docs/client.md']}]
        found = select_context(self.root, ['src/client/A.java', 'src/client/B.java'], rules)
        self.assertEqual(set(found), {'AGENTS.md', 'src/AGENTS.md', 'src/client/AGENTS.md', 'docs/client.md'})
        self.assertNotIn('docs/client.md', select_context(self.root, ['README.md'], rules))

    def test_missing_targets_and_instruction_links_fail(self):
        self.write('AGENTS.md', '[missing](missing.md)')
        errors = check_map(self.root, [{'name': 'bad', 'paths': ['*'], 'docs': ['missing.md']}])
        self.assertEqual(len(errors), 2)
        self.write('missing.md')
        self.assertEqual(check_map(self.root, []), [])

    def test_paths_stay_inside_workspace(self):
        self.assertEqual(normalize(self.root, r'src\client\A.java'), 'src/client/A.java')
        with self.assertRaises(ValueError):
            normalize(self.root, '../outside')
        with self.assertRaises(ValueError):
            changed_paths(self.root, '--help')


if __name__ == '__main__':
    unittest.main()
