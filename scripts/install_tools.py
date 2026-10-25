"""Install pinned official tools in ignored .tools (no root required)."""
from pathlib import Path
import urllib.request, tarfile, zipfile, shutil, os
ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
TOOLS.mkdir(exist_ok=True)
def download(url, name):
    target = TOOLS / name
    if not target.exists():
        print('Downloading', name, flush=True)
        with urllib.request.urlopen(url, timeout=120) as src, open(str(target)+'.part','wb') as dst:
            shutil.copyfileobj(src,dst)
        Path(str(target)+'.part').rename(target)
    return target
if not (TOOLS/'android-studio/bin/studio').exists():
    archive=download('https://edgedl.me.gvt1.com/android/studio/ide-zips/2026.2.1.8/android-studio-rabbit1-linux.tar.gz','studio.tar.gz')
    print('Extracting Android Studio',flush=True)
    with tarfile.open(archive) as t:t.extractall(TOOLS,filter='data')
    archive.unlink()
sdk=TOOLS/'android-sdk'
if not (sdk/'cmdline-tools/latest/bin/sdkmanager').exists():
    archive=download('https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip','cmdline.zip')
    sdk.mkdir(exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        z.extractall(sdk/'cmdline-tools')
        for i in z.infolist():
            p=sdk/'cmdline-tools'/i.filename
            if p.is_file():p.chmod((i.external_attr>>16) or 0o644)
    (sdk/'cmdline-tools/cmdline-tools').rename(sdk/'cmdline-tools/latest')
    archive.unlink()
print('Installed', TOOLS,flush=True)
