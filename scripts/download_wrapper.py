from pathlib import Path
import urllib.request
root=Path(__file__).resolve().parents[1]
base='https://raw.githubusercontent.com/gradle/gradle/v8.13.0/'
for src,dst in [('gradlew','gradlew'),('gradlew.bat','gradlew.bat'),('gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.jar')]:
    with urllib.request.urlopen(base+src,timeout=60) as r:(root/dst).write_bytes(r.read())
(root/'gradlew').chmod(0o755)
with urllib.request.urlopen('https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256',timeout=60) as r:
    digest=r.read().decode().strip()
p=root/'gradle/wrapper/gradle-wrapper.properties'
s=p.read_text()
if 'distributionSha256Sum=' not in s:p.write_text(s+'distributionSha256Sum='+digest+'\n')
