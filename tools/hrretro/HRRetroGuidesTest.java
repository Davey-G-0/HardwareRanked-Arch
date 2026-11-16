package com.retroarch.browser.retroactivity;

public final class HRRetroGuidesTest {
    private static int checks;
    private static void contains(String label, String value, String part) {
        if (!value.contains(part)) throw new AssertionError(label + " missing: " + part);
        checks++;
    }
    public static void main(String[] args) {
        contains("PS2 guide names BIOS", HRRetroGuides.ps2(false), "BIOS");
        contains("PS2 guide explains user-selected SD BIOS", HRRetroGuides.ps2(false), "select your BIOS file from the SD card");
        contains("PS2 guide does not bundle firmware", HRRetroGuides.ps2(false), "doesn't include or download BIOS");
        contains("Installed PS2 app path", HRRetroGuides.ps2(true), "Open the installed PS2 app");
        contains("Controller menu shortcut", HRRetroGuides.CONTROLS, "START + SELECT once");
        contains("Return shortcut preserved", HRRetroGuides.CONTROLS, "Three quick START + SELECT chords");
        contains("Save and load states accessible", HRRetroGuides.CONTROLS, "Quick Menu → Save States");
        contains("Fast-forward controller binding", HRRetroGuides.CONTROLS, "Fast-Forward (Hold)");
        contains("BIOS setup is accessible later", HRRetroGuides.BIOS, "You can return here and do this later");
        System.out.println("HR Retro setup guides: " + checks + " checks passed");
    }
}
