#pragma once

#include <Windows.h>
#include <string>

extern bool debugEnabled;
extern FILE *debugLogFile;

void EnableDebug();

void debugLog(const std::wstring &log);

#define DEBUG_LOG(__hmcl_message_format__, ...) \
  if (debugEnabled) {                                               \
    fwprintf(stderr, L"[HMCLauncher] " __hmcl_message_format__ "\n", __VA_ARGS__);                   \
    fflush(stdout);                                                 \
    if (debugLogFile != NULL) {                                     \
      fwprintf(debugLogFile, L"[HMCLauncher] " __hmcl_message_format__ "\n", \
               __VA_ARGS__);  \
      fflush(debugLogFile);                                         \
    }                                                               \
  }