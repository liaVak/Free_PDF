#!/usr/bin/env python3
"""Black-box release/upgrade checks on an explicitly selected disposable emulator."""
import argparse
import json
import re
import subprocess
import time
from pathlib import Path
from xml.etree import ElementTree as ET

PACKAGE = 'ru.ilyavakarin.pdfreader'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--upgrade-from', type=Path, required=True)
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args()
    if not args.serial.startswith('emulator-'):
        parser.error('Use a disposable local emulator, never a personal phone')
    for apk in (args.apk, args.upgrade_from):
        if not apk.is_file():
            parser.error(f'APK missing: {apk}')
    output = Path('output/release-smoke')
    output.mkdir(parents=True, exist_ok=True)
    checks = []

    def run(*command, timeout=60):
        result = subprocess.run([args.adb, '-s', args.serial, *map(str, command)],
                                capture_output=True, text=True, timeout=timeout)
        with (output / 'commands.log').open('a') as log:
            log.write(' '.join(map(str, command)) + '\n' + result.stdout + result.stderr + '\n')
        if result.returncode:
            raise RuntimeError(f'ADB command failed: {command}; see {output}/commands.log')
        return result.stdout

    def hierarchy():
        run('shell', 'uiautomator', 'dump', '/sdcard/release-smoke.xml', timeout=30)
        xml = run('exec-out', 'cat', '/sdcard/release-smoke.xml')
        (output / 'latest-ui.xml').write_text(xml)
        return ET.fromstring(xml)

    def find(label, root):
        return next((node for node in root.iter('node')
                     if node.get('text') == label or node.get('content-desc') == label), None)

    def wait(label, seconds=35):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            root = hierarchy()
            node = find(label, root)
            if node is not None:
                return node
            time.sleep(0.5)
        raise AssertionError(f'UI did not show {label!r}; see latest-ui.xml')

    def tap_node(node):
        values = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
        if len(values) != 4:
            raise AssertionError('No tappable bounds')
        x1, y1, x2, y2 = values
        run('shell', 'input', 'tap', (x1+x2)//2, (y1+y2)//2)

    def tap(label):
        tap_node(wait(label))

    def note(name):
        checks.append(name)
        (output / 'results.json').write_text(json.dumps({'passed': checks}, ensure_ascii=False, indent=2))
        print('PASS:', name, flush=True)

    def screenshot(name):
        with (output / name).open('wb') as stream:
            subprocess.run([args.adb, '-s', args.serial, 'exec-out', 'screencap', '-p'], stdout=stream,
                           timeout=30, check=True)

    def jump_to_three():
        tap('1 / 3')
        root = hierarchy()
        field = next(n for n in root.iter('node') if n.get('class') == 'android.widget.EditText')
        tap_node(field)
        run('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
        run('shell', 'input', 'keyevent', 'KEYCODE_DEL')
        run('shell', 'input', 'text', '3')
        tap('Перейти')
        wait('3 / 3')
        wait('Страница 3')

    if run('shell', 'getprop', 'sys.boot_completed').strip() != '1':
        raise RuntimeError('Android must be booted before running this script')
    run('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    run('shell', 'wm', 'dismiss-keyguard')
    run('shell', 'logcat', '-c', '-b', 'crash')
    run('install', '-r', args.upgrade_from)
    run('push', 'app/src/androidTest/assets/pdf/mixed.pdf', '/sdcard/Download/upgrade-v020.pdf')
    run('shell', 'am', 'broadcast', '-a', 'android.intent.action.MEDIA_SCANNER_SCAN_FILE',
        '-d', 'file:///sdcard/Download/upgrade-v020.pdf')
    run('shell', 'am', 'start', '-W', '-n', f'{PACKAGE}/.MainActivity')
    tap('Открыть PDF')
    root = hierarchy()
    if find('upgrade-v020.pdf', root) is None:
        drawer = find('Show roots', root)
        if drawer is None:
            drawer = find('Открыть меню', root)
        if drawer is None:
            raise AssertionError('Cannot locate DocumentsUI roots drawer; see latest-ui.xml')
        tap_node(drawer)
        tap('Downloads')
    tap('upgrade-v020.pdf')
    wait('1 / 3')
    jump_to_three()
    tap('К документам')
    wait('upgrade-v020.pdf')
    run('shell', 'am', 'force-stop', PACKAGE)
    run('install', '-r', args.apk)
    run('shell', 'am', 'start', '-W', '-n', f'{PACKAGE}/.MainActivity')
    tap('upgrade-v020.pdf')
    wait('3 / 3')
    wait('Страница 3')
    note('Signed 0.1.0 → 0.2.0 update preserves history, URI permission and page 3')

    sender = f'{PACKAGE}.test/ru.ilyavakarin.pdfreader.ExternalPdfSenderActivity'
    run('shell', 'am', 'force-stop', PACKAGE)
    run('shell', 'am', 'start', '-W', '-n', sender, '--es', 'fixture', 'embedded')
    wait('embedded.pdf'); wait('1 / 1'); wait('Страница 1')
    note('Minified release cold-opens an external content URI with a temporary read grant')
    run('shell', 'am', 'start', '-W', '-n', sender, '--es', 'fixture', 'mixed')
    wait('mixed.pdf'); wait('1 / 3'); wait('Страница 1')
    note('Existing reader accepts a second external PDF')
    tap('Увеличить'); wait('150%')
    tap('Уменьшить'); wait('100%')
    jump_to_three()
    tap('К документам'); tap('mixed.pdf'); wait('3 / 3')
    note('Release zoom, page jump and history reopening work after R8')
    screenshot('reader-v0.2.0.png')
    tap('Поделиться PDF')
    root = hierarchy()
    if not any('resolver' in n.get('resource-id', '') or 'chooser' in n.get('resource-id', '')
               for n in root.iter('node')):
        raise AssertionError('System share UI did not appear; see latest-ui.xml')
    screenshot('share-v0.2.0.png')
    run('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    note('Release prepares the shared PDF and opens the Android sharesheet')
    activity = run('shell', 'cmd', 'package', 'query-activities', '--brief', '-a',
                   'android.intent.action.VIEW', '-t', 'application/pdf', '-d',
                   f'content://{PACKAGE}.test.fixtures/fixtures/mixed.pdf')
    if f'{PACKAGE}/.MainActivity' not in activity and f'{PACKAGE}/{PACKAGE}.MainActivity' not in activity:
        raise AssertionError('Release missing from system PDF handlers')
    note('Android lists the release application among PDF handlers')
    crashes = run('shell', 'logcat', '-d', '-b', 'crash')
    if crashes.strip():
        raise AssertionError('Crash buffer is not empty; inspect commands.log')
    note('No Android crashes during release smoke checks')
    print('Release smoke checks complete. Evidence:', output)


if __name__ == '__main__':
    main()
