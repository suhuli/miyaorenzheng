import importlib.util,json,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
p=Path(__file__).resolve().parents[1]/'sync_official_data.py'
spec=importlib.util.spec_from_file_location('sync',p);sync=importlib.util.module_from_spec(spec);spec.loader.exec_module(sync)
root=Path(__file__).resolve().parents[2]
class SyncTest(unittest.TestCase):
    def setUp(self):
        self.roots=json.loads((root/'app/src/main/res/raw/roots.json').read_text())
        self.status={'entries':{'a':{'status':'REVOKED'}}}
    def test_real_bundled_root_signatures(self):sync.validate(self.roots,self.status)
    def test_bad_root_rejected(self):
        with self.assertRaises(Exception):sync.validate(['invalid'],self.status)
    def test_unknown_revocation_status_rejected(self):
        with self.assertRaises(ValueError):sync.validate(self.roots,{'entries':{'a':{'status':'OK'}}})
    def test_publish_manifest_matches_payload(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(sync,'download',side_effect=[self.roots,self.status]):
            sync.sync(folder);m=json.loads((Path(folder)/'manifest.json').read_text());b=(Path(folder)/m['bundle_path']).read_bytes()
            self.assertEqual(sync.hashlib.sha256(b).hexdigest(),m['sha256']);self.assertEqual(len(b),m['bytes']);self.assertEqual(json.loads(b)['fetched_at'],m['fetched_at'])
    def test_failed_fetch_does_not_overwrite_previous_manifest(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(sync,'download',side_effect=[self.roots,RuntimeError('offline')]):
            path=Path(folder)/'manifest.json';path.write_text('previous')
            with self.assertRaises(RuntimeError):sync.sync(folder)
            self.assertEqual(path.read_text(),'previous')
if __name__=='__main__':unittest.main()
