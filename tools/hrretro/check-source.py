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
print("HR Retro source and Android XML checks passed")
