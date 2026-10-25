"""Repair SDK packages left incomplete by a full disk. Downloads are temporary."""
from pathlib import Path
import urllib.request,zipfile,shutil,hashlib
root=Path(__file__).resolve().parents[1]/'.tools/android-sdk'
packages=[
('https://dl.google.com/android/repository/emulator-linux_x64-16428233.zip','cd7362ea55dfb86a418958138dc396e74165dd01',root),
('https://dl.google.com/android/repository/sys-img/android/x86_64-31_r05.zip','58bff3cb182c79bbfe3980fe77b87b51b9f7ad71',root/'system-images/android-31/default'),
]
for i,(url,checksum,target) in enumerate(packages):
    archive=Path(f'/tmp/pdf-reader-package-{i}.zip')
    if not archive.exists():
        print('Downloading',url,flush=True)
        with urllib.request.urlopen(url,timeout=120) as src,archive.open('wb') as dst:shutil.copyfileobj(src,dst)
    with archive.open('rb') as f:assert hashlib.file_digest(f,'sha1').hexdigest()==checksum
    with zipfile.ZipFile(archive) as z:
        print('Extracting',round(sum(x.file_size for x in z.infolist())/1024**2),'MiB',flush=True)
        for item in z.infolist():
            z.extract(item,target)
            p=target/item.filename
            if p.is_file():p.chmod((item.external_attr>>16) or 0o644)
    archive.unlink()
