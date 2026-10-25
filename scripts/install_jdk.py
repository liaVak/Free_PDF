"""Install the pinned Ubuntu JDK locally, without root or global configuration."""
from pathlib import Path
import subprocess,shutil,os
root=Path(__file__).resolve().parents[1]/'.tools'
packages=root/'jdk-packages';packages.mkdir(parents=True,exist_ok=True)
version='21.0.12.1+1-1'
if not (root/'jdk/bin/java').exists():
    subprocess.run(['apt-get','download',f'openjdk-21-jdk-headless={version}',f'openjdk-21-jre-headless={version}'],cwd=packages,check=True)
    for p in packages.glob('*.deb'):
        subprocess.run(['dpkg-deb','-x',str(p),str(packages/'unpacked')],check=True)
        p.unlink()
    (root/'jdk').symlink_to('jdk-packages/unpacked/usr/lib/jvm/java-21-openjdk-amd64',target_is_directory=True)
jdk=(root/'jdk').resolve()
for p in jdk.rglob('*'):
    if p.is_symlink():
        target=p.readlink()
        if str(target).startswith('/etc/java-21-openjdk/'):
            src=packages/'unpacked'/str(target).lstrip('/')
            if src.is_file():p.unlink();shutil.copyfile(src,p)
p=jdk/'lib/security/cacerts'
if p.is_symlink():
    p.unlink();shutil.copyfile(root/'android-studio/jbr/lib/security/cacerts',p)
(root/'jdk-version.txt').write_text('Ubuntu OpenJDK '+version+'\n')
print('JDK ready:',jdk)
