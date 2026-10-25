from pathlib import Path
import shutil
root=Path(__file__).resolve().parents[1]/'.tools'
jdk=root/'jdk'
for p in jdk.resolve().rglob('*'):
    if p.is_symlink():
        target=p.readlink()
        if str(target).startswith('/etc/java-21-openjdk/'):
            src=root/'jdk-packages/unpacked'/str(target).lstrip('/')
            if src.is_file():p.unlink();shutil.copyfile(src,p)
p=jdk/'lib/security/cacerts'
if p.is_symlink():
    p.unlink();shutil.copyfile(root/'android-studio/jbr/lib/security/cacerts',p)
