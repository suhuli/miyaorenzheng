#!/usr/bin/env python3
"""Mirror public Google attestation data. No credentials or device data in output."""
import hashlib,json,sys,time,urllib.request
from pathlib import Path
from datetime import datetime,timezone
from cryptography import x509
from cryptography.hazmat.primitives.asymmetric import rsa,ec,padding
ROOT='https://android.googleapis.com/attestation/root'
STATUS='https://android.googleapis.com/attestation/status'
LIMIT=8*1024*1024

def download(url):
    req=urllib.request.Request(url,headers={'Accept':'application/json','User-Agent':'miyaorenzheng-data-sync'})
    with urllib.request.urlopen(req,timeout=40) as response:
        if response.geturl()!=url: raise ValueError('Unexpected source redirect')
        data=response.read(LIMIT+1)
    if len(data)>LIMIT: raise ValueError('Response too large')
    return json.loads(data)

def validate(roots,status):
    if not isinstance(roots,list) or not 1<=len(roots)<=100:raise ValueError('Invalid roots')
    for pem in roots:
        cert=x509.load_pem_x509_certificate(pem.encode())
        if not cert.extensions.get_extension_for_class(x509.BasicConstraints).value.ca:raise ValueError('Not a CA')
        key=cert.public_key()
        if isinstance(key,rsa.RSAPublicKey):key.verify(cert.signature,cert.tbs_certificate_bytes,padding.PKCS1v15(),cert.signature_hash_algorithm)
        elif isinstance(key,ec.EllipticCurvePublicKey):key.verify(cert.signature,cert.tbs_certificate_bytes,ec.ECDSA(cert.signature_hash_algorithm))
        else:raise ValueError('Unsupported official root algorithm')
    entries=status['entries']
    if not isinstance(entries,dict):raise ValueError('Invalid revocations')
    for serial,entry in entries.items():
        if not serial or any(c not in '0123456789abcdefABCDEF' for c in serial):raise ValueError('Invalid serial')
        if entry['status'] not in ('REVOKED','SUSPENDED'):raise ValueError('Invalid status')

def sync(folder):
    roots,status=download(ROOT),download(STATUS)
    validate(roots,status) # No output is replaced until BOTH official downloads validate.
    fetched=int(time.time()*1000)
    payload={'schema':1,'fetched_at':fetched,'fetched_utc':datetime.now(timezone.utc).isoformat(),'root_source':ROOT,'status_source':STATUS,'roots':roots,'status':status}
    data=json.dumps(payload,ensure_ascii=False,separators=(',',':')).encode()
    if len(data)>LIMIT:raise ValueError('Bundle too large')
    folder=Path(folder);snaps=folder/'snapshots';snaps.mkdir(parents=True,exist_ok=True)
    name=f'snapshots/{fetched}.json'
    (folder/name).write_bytes(data)
    manifest={'schema':1,'fetched_at':fetched,'bundle_path':name,'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data)}
    temp=folder/'manifest.json.tmp';temp.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');temp.replace(folder/'manifest.json')
    for old in sorted(snaps.glob('*.json'),key=lambda p:int(p.stem))[:-64]:old.unlink()
    print(f'Cached {len(roots)} roots, {len(status["entries"])} entries at {fetched}; {len(data)} bytes')
if __name__=='__main__':sync(sys.argv[1] if len(sys.argv)>1 else 'mirror')
