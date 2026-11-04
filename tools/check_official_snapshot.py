#!/usr/bin/env python3
"""Validate bundled official data; no synthetic device results. pip install cryptography"""
import json
from pathlib import Path
from cryptography import x509
from cryptography.hazmat.primitives.asymmetric import rsa, ec, padding
raw = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'
roots = json.loads((raw / 'roots.json').read_text())
assert roots and len(roots) <= 100
algorithms = []
for pem in roots:
    c = x509.load_pem_x509_certificate(pem.encode())
    assert c.extensions.get_extension_for_class(x509.BasicConstraints).value.ca
    key = c.public_key()
    if isinstance(key, rsa.RSAPublicKey):
        key.verify(c.signature, c.tbs_certificate_bytes, padding.PKCS1v15(), c.signature_hash_algorithm)
        algorithms.append('RSA')
    elif isinstance(key, ec.EllipticCurvePublicKey):
        key.verify(c.signature, c.tbs_certificate_bytes, ec.ECDSA(c.signature_hash_algorithm))
        algorithms.append('EC')
    else:
        raise AssertionError('Unexpected official root algorithm')
entries = json.loads((raw / 'status.json').read_text())['entries']
assert isinstance(entries, dict)
for serial, entry in entries.items():
    assert int(serial, 16) >= 0
    assert isinstance(entry['status'], str) and entry['status']
print(f'PASS: {len(roots)} official root certificates, self-signatures verified ({algorithms})')
print(f'PASS: {len(entries)} official revocation entries, serial/status schema verified')
print('NOTE: these are data checks, not on-device attestation or RKP tests.')
