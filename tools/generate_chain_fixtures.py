#!/usr/bin/env python3
"""Synthetic, non-Google test certificates. Keys exist only in memory; never ship a device key."""
from pathlib import Path
from datetime import datetime, timezone
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa
out=Path(__file__).resolve().parents[1]/'app/src/test/resources/chains'
out.mkdir(parents=True,exist_ok=True)
for family, depth in [('new',3),('old',2)]:
    parent_cert=parent_key=None
    for index in range(depth+1):
        key=rsa.generate_private_key(public_exponent=65537,key_size=2048) if family=='old' and index==0 else ec.generate_private_key(ec.SECP256R1())
        name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,f'SYNTHETIC {family} CA {index}')])
        cert=(x509.CertificateBuilder().subject_name(name).issuer_name(parent_cert.subject if parent_cert else name)
              .public_key(key.public_key()).serial_number(index+10).not_valid_before(datetime(2025,1,1,tzinfo=timezone.utc))
              .not_valid_after(datetime(2035,1,1,tzinfo=timezone.utc)).add_extension(x509.BasicConstraints(ca=True,path_length=None),True)
              .sign(parent_key or key,hashes.SHA256()))
        (out/f'{family}-{index}.der').write_bytes(cert.public_bytes(serialization.Encoding.DER))
        parent_cert,parent_key=cert,key
    for leaf in ['leaf','attest','forged']:
        key=ec.generate_private_key(ec.SECP256R1())
        name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,f'SYNTHETIC {family} {leaf}')])
        signing_key=ec.generate_private_key(ec.SECP256R1()) if leaf=='forged' else parent_key
        cert=(x509.CertificateBuilder().subject_name(name).issuer_name(parent_cert.subject).public_key(key.public_key())
              .serial_number({'leaf':1,'attest':2,'forged':3}[leaf]).not_valid_before(datetime(2025,1,1,tzinfo=timezone.utc))
              .not_valid_after(datetime(2035,1,1,tzinfo=timezone.utc)).sign(signing_key,hashes.SHA256()))
        (out/f'{family}-{leaf}.der').write_bytes(cert.public_bytes(serialization.Encoding.DER))
print('Synthetic chain fixtures generated. No private keys written.')
