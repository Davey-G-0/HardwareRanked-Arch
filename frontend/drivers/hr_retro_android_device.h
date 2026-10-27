/* HR Retro — Android gaming handheld identification.
 * Independent, testable helper. The model is Android ro.product.model.
 * Only used to choose a sensible FIRST LAUNCH menu/overlay default;
 * users can change menu drivers and overlays in Settings.
 *
 * GPL-3.0-or-later. Copyright (C) 2026 Hardware Ranked contributors.
 */
#ifndef HR_RETRO_ANDROID_DEVICE_H
#define HR_RETRO_ANDROID_DEVICE_H
#include <stdbool.h>
#include <stddef.h>
#include <string.h>

/* Keep this conservative: don't match generic "Pocket" or "RG",
 * which would hijack ordinary phones or tablets. */
static inline bool hr_retro_is_android_handheld_model(const char *model)
{
   static const char *const matchers[] = {
      "Retroid", "retroid", "RETROID",
      "AYN", "Odin", "ODIN",
      "KONKR", "Ayaneo", "AYANEO",
      "Anbernic", "ANBERNIC",
      "Mangmi", "MANGMI",
      "RG35", "RG40", "RG47", "RG55", "RG Cube",
      "Logitech G Cloud", "G CLOUD", "G Cloud",
      "Razer Edge", "RAZER EDGE"
   };
   size_t i;
   if (!model || !*model)
      return false;
   for (i = 0; i < sizeof(matchers) / sizeof(matchers[0]); ++i)
      if (strstr(model, matchers[i]))
         return true;
   return false;
}
#endif /* HR_RETRO_ANDROID_DEVICE_H */
