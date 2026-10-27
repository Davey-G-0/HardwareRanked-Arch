/* Compile as: cc -std=c99 -Wall -Wextra -Werror
 * tools/hrretro/test-device.c -o /tmp/hrretro-device-test
 * /tmp/hrretro-device-test
 */
#include "../../frontend/drivers/hr_retro_android_device.h"
#include <stdio.h>

typedef struct { const char *model; bool expected; } test_case;
int main(void)
{
   const test_case cases[] = {
      {"Retroid Pocket 6", true},
      {"RETROID Pocket 5", true},
      {"AYN Odin 3", true},
      {"Odin 2 Mini", true},
      {"KONKR Pocket Fit", true},
      {"AYANEO Pocket S2", true},
      {"ANBERNIC RG476H", true},
      {"RG35XX", true},
      {"RG Cube", true},
      {"MANGMI AIR X", true},
      {"Logitech G Cloud", true},
      {"Razer Edge", true},
      {"Pixel 10 Pro", false},
      {"Samsung SM-S928U", false},
      {"Galaxy S26", false},
      {"Pocketbook eReader", false},
      {"Generic Android TV", false},
      {"", false},
      {NULL, false}
   };
   size_t i;
   for (i = 0; i < sizeof(cases) / sizeof(cases[0]); ++i)
   {
      bool actual = hr_retro_is_android_handheld_model(cases[i].model);
      if (actual != cases[i].expected)
      {
         fprintf(stderr, "model test %lu failed (%s): got %d, expected %d\n",
               (unsigned long)i, cases[i].model ? cases[i].model : "NULL",
               (int)actual, (int)cases[i].expected);
         return 1;
      }
   }
   puts("HR Retro handheld model tests passed");
   return 0;
}
