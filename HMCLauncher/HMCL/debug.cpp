#include "stdafx.h"
#include "debug.h"
#include <stdio.h>

bool debugEnabled = false;
FILE *debugLogFile = NULL;

void EnableDebug() {
  if (!debugEnabled) {
    debugEnabled = true;
    AllocConsole();
    freopen("CONOUT$", "w", stdout);
    freopen("CONOUT$", "w", stderr);

    debugLogFile = fopen("hmclauncher-debug.log", "w");

    if (debugLogFile != NULL)
      wprintf(L"[HMCLauncher] Write debug log to file hmclauncher-debug.log\n");
    else 
      wprintf(L"[HMCLauncher] Failed to open the debug log file\n");
  }
}
