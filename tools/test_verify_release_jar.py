import json
import shutil
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

from verify_release_jar import verify_jar

METADATA = """modLoader="javafml"
loaderVersion="[1,)"
license="LGPL-2.1-only"

[[mods]]
modId="mineturn"
version="1.0.0"
displayName="MineTurn"

[[mixins]]
config="mineturn.mixins.json"
"""

# The metadata this repository actually ships annotates its headers, which once defeated the parser.
METADATA_WITH_HEADER_COMMENTS = """modLoader="javafml" #mandatory
loaderVersion="[1,)" #mandatory
license="LGPL-2.1-only" #mandatory

[[mods]] #mandatory
modId="mineturn" #mandatory
version="1.0.0" #mandatory
displayName="MineTurn" #mandatory
description='''A description with a # hash and a [[mods]] mention inside quotes.'''

[[mixins]]
config="mineturn.mixins.json"
"""

MIXINS = {
    "required": True,
    "package": "com.matuvent.mineturn.mixin",
    "compatibilityLevel": "JAVA_21",
    "mixins": ["FoodMixin"],
    "client": ["BattleCameraMixin"],
    "injectors": {"defaultRequire": 1},
}


def valid_entries():
    """A minimal archive that satisfies every check, so each test can break exactly one thing."""
    return {
        "META-INF/neoforge.mods.toml": METADATA,
        "mineturn.mixins.json": json.dumps(MIXINS),
        "com/matuvent/mineturn/mixin/FoodMixin.class": "bytecode",
        "com/matuvent/mineturn/mixin/BattleCameraMixin.class": "bytecode",
        "assets/mineturn/lang/en_us.json": '{"a":"b"}',
        "assets/mineturn/lang/zh_cn.json": '{"a":"b"}',
        "assets/mineturn/mineturn/animation_camera.json": '{"camera":{}}',
        "data/mineturn/mineturn/actions/melee.json": '{"name":"melee"}',
    }


class VerifyReleaseJarTests(unittest.TestCase):
    def setUp(self):
        test_root = Path(__file__).resolve().parents[1] / 'build' / 'jar-verify-tests'
        test_root.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=test_root)
        self.root = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)
        self.addCleanup(shutil.rmtree, self.root, True)

    def build(self, name, entries=None, *, duplicates=(), raw=None):
        path = self.root / name
        if raw is not None:
            path.write_bytes(raw)
            return path
        with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as archive:
            for member, content in (entries if entries is not None else valid_entries()).items():
                archive.writestr(member, content)
            for member in duplicates:
                # Writing the same name twice is the point of these cases; zipfile warns about it.
                with warnings.catch_warnings():
                    warnings.simplefilter('ignore', UserWarning)
                    archive.writestr(member, 'duplicate')
        return path

    def failures(self, path):
        return verify_jar(path).failures

    def test_valid_jar_passes(self):
        self.assertEqual([], self.failures(self.build('ok.jar')))

    def test_missing_file_fails(self):
        self.assertTrue(any('not a file' in f for f in self.failures(self.root / 'absent.jar')))

    def test_corrupt_zip_fails(self):
        path = self.root / 'broken.jar'
        path.write_bytes(b'PK\x03\x04 this is not really a zip archive')
        self.assertTrue(any('not a valid ZIP' in f for f in self.failures(path)))

    def test_duplicate_entry_fails(self):
        path = self.build('dup.jar', duplicates=('data/mineturn/mineturn/actions/melee.json',))
        self.assertTrue(any('duplicate archive entries' in f for f in self.failures(path)))

    def test_missing_metadata_fails(self):
        entries = valid_entries()
        del entries['META-INF/neoforge.mods.toml']
        self.assertTrue(any('missing mod metadata' in f for f in self.failures(self.build('nometa.jar', entries))))

    def test_wrong_mod_id_fails(self):
        entries = valid_entries()
        entries['META-INF/neoforge.mods.toml'] = METADATA.replace('modId="mineturn"', 'modId="other"')
        self.assertTrue(any('mod id' in f for f in self.failures(self.build('wrongid.jar', entries))))

    def test_declared_mixin_config_absent_fails(self):
        entries = valid_entries()
        del entries['mineturn.mixins.json']
        self.assertTrue(any('not in the archive' in f for f in self.failures(self.build('noconf.jar', entries))))

    def test_missing_mixin_class_fails(self):
        entries = valid_entries()
        del entries['com/matuvent/mineturn/mixin/FoodMixin.class']
        problems = self.failures(self.build('noclass.jar', entries))
        self.assertTrue(any('mixin class(es) missing' in f for f in problems), problems)

    def test_missing_client_mixin_class_fails(self):
        entries = valid_entries()
        del entries['com/matuvent/mineturn/mixin/BattleCameraMixin.class']
        problems = self.failures(self.build('noclient.jar', entries))
        self.assertTrue(any('BattleCameraMixin' in f for f in problems), problems)

    def test_bad_json_member_fails(self):
        entries = valid_entries()
        entries['data/mineturn/mineturn/actions/melee.json'] = '{"name": '
        problems = self.failures(self.build('badjson.jar', entries))
        self.assertTrue(any('invalid JSON' in f for f in problems), problems)

    def test_missing_required_resource_fails(self):
        entries = valid_entries()
        del entries['assets/mineturn/mineturn/animation_camera.json']
        self.assertTrue(any('required resource missing' in f for f in self.failures(self.build('nores.jar', entries))))

    def test_missing_data_namespace_fails(self):
        entries = {k: v for k, v in valid_entries().items() if not k.startswith('data/mineturn/')}
        self.assertTrue(any('no data resources' in f for f in self.failures(self.build('nodata.jar', entries))))

    def test_mixin_package_mismatch_fails(self):
        """A class present under the wrong package must not satisfy the declaration."""
        entries = valid_entries()
        entries['com/other/FoodMixin.class'] = entries.pop('com/matuvent/mineturn/mixin/FoodMixin.class')
        self.assertTrue(any('mixin class(es) missing' in f for f in self.failures(self.build('wrongpkg.jar', entries))))

    def test_path_with_spaces_and_chinese_is_accepted(self):
        """The release checklist is meant to work from any path a maintainer happens to use."""
        folder = self.root / '构建 产物'
        folder.mkdir(parents=True, exist_ok=True)
        path = folder / 'mineturn 1.0.0.jar'
        with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as archive:
            for member, content in valid_entries().items():
                archive.writestr(member, content)
        self.assertEqual([], self.failures(path))

    def test_report_records_size_and_hash(self):
        report = verify_jar(self.build('hash.jar'))
        joined = '\n'.join(report.notes)
        self.assertIn('sha256:', joined)
        self.assertIn('size:', joined)
        self.assertIn('version: 1.0.0', joined)

    def test_metadata_with_annotated_headers_parses(self):
        """The shipped template annotates headers; a comment must not hide the table or the value."""
        entries = valid_entries()
        entries['META-INF/neoforge.mods.toml'] = METADATA_WITH_HEADER_COMMENTS
        report = verify_jar(self.build('annotated.jar', entries))
        self.assertEqual([], report.failures)
        joined = '\n'.join(report.notes)
        self.assertIn('mod id: mineturn', joined)
        self.assertIn('version: 1.0.0', joined)


if __name__ == '__main__':
    unittest.main()
