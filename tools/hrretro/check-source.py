#!/usr/bin/env python3
"""Basic source-level HR Retro regression guard, not a replacement for compilation."""
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(path: str, snippet: str) -> None:
    if snippet not in read(path):
        raise AssertionError(f"{path}: missing {snippet!r}")

def android_default(name: str, android: str, upstream: str) -> None:
    block = (f"#ifdef ANDROID\n"
             f"#define {name} {android}\n"
             f"#else\n"
             f"#define {name} {upstream}\n"
             "#endif")
    require("config.def.h", block)

require("menu/menu_defines.h", "MATERIALUI_THEME_HARDWARE_RANKED")
require("menu/drivers/materialui.c", "case MATERIALUI_THEME_HARDWARE_RANKED:")
require("menu/drivers/ozone_color_themes.h", 'X("hardware_ranked",')
require("menu/drivers/ozone.c", "ozone_theme_hardware_ranked")
require("menu/menu_setting.c", 'strlcpy(s, "Hardware Ranked", len)')
require("menu/menu_displaylist.c", '"Exit Game"')
require("frontend/drivers/platform_unix.c", "hr_retro_is_android_handheld_model(name)")
require("frontend/drivers/hr_retro_android_device.h", '"Retroid"')
android_default("DEFAULT_OZONE_COLOR_THEME", '"hardware_ranked"', '"basic_black"')
android_default("DEFAULT_MATERIALUI_THEME",
                "MATERIALUI_THEME_HARDWARE_RANKED", "MATERIALUI_THEME_OZONE_DARK")
android_default("DEFAULT_QUICK_MENU_SHOW_SAVESTATE_SUBMENU", "false", "true")
android_default("DEFAULT_QUICK_MENU_SHOW_RESTART_CONTENT", "false", "true")
android_default("DEFAULT_SHOW_ADVANCED_SETTINGS", "false", "true")
require("pkg/android/phoenix/build.gradle", 'applicationId "com.hardwareranked.retro"')
require("pkg/android/phoenix/build.gradle", '"app_name", "HR Retro"')
require("pkg/android/phoenix/AndroidManifest.xml", "@drawable/hr_retro_launcher")
require("pkg/android/phoenix/AndroidManifest.xml", "@drawable/hr_retro_banner")
ElementTree.parse(ROOT / "pkg/android/phoenix/AndroidManifest.xml")
for path in (
    "pkg/android/phoenix/res/drawable/hr_retro_launcher.xml",
    "pkg/android/phoenix/res/drawable/hr_retro_banner.xml",
):
    root = ElementTree.parse(ROOT / path).getroot()
    assert root.tag == "vector", f"{path}: expected vector drawable"
    assert root.attrib.get("{http://schemas.android.com/apk/res/android}width"), (
        f"{path}: missing android:width"
    )
assert "GNU GENERAL PUBLIC LICENSE" in read("COPYING")
# One-folder setup contract: the launcher targets the guided Android screen,
# while the existing native activity remains installed for emulation.
setup_path = "pkg/android/phoenix/src/com/retroarch/browser/retroactivity/HRSetupActivity.java"
import_path = "pkg/android/phoenix/src/com/retroarch/browser/retroactivity/HRGameLibraryImporter.java"
class_path = "pkg/android/phoenix/src/com/retroarch/browser/retroactivity/HRGameLibraryClassifier.java"
require(setup_path, "Intent.ACTION_OPEN_DOCUMENT_TREE")
require(setup_path, "takePersistableUriPermission")
require(setup_path, "new File(getFilesDir(), \"playlists\")")
require(import_path, "HRGameLibraryClassifier.systemFor")
require(import_path, "DocumentsContract.buildDocumentUriUsingTree")
require(import_path, "File.createTempFile")
require(class_path, "Needs Review")
require("frontend/drivers/platform_unix.c", '"files/playlists"')
require("pkg/android/phoenix-common/src/com/retroarch/browser/retroactivity/RetroActivityCommon.java",
        'getPackageName().startsWith("com.hardwareranked.retro")')
import xml.etree.ElementTree as ET
tree = ET.parse(ROOT / "pkg/android/phoenix/AndroidManifest.xml")
android_name = "{http://schemas.android.com/apk/res/android}name"
android_target = "{http://schemas.android.com/apk/res/android}targetActivity"
aliases = [e for e in tree.getroot().iter("activity-alias")
           if e.get(android_name) == "com.retroarch.browser.mainmenu.MainMenuActivity"]
assert len(aliases) == 1
assert aliases[0].get(android_target) == (
    "com.retroarch.browser.retroactivity.HRSetupActivity"
)
assert not any("MANAGE_EXTERNAL_STORAGE" in p.get(android_name, "")
               for p in tree.getroot().iter("uses-permission"))
print("HR Retro source and Android XML checks passed")
