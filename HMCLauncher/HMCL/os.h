#pragma once
#include <string>
#include <windows.h>
#include "Version.h"

const int MAX_KEY_LENGTH = 255;
const int MAX_VALUE_NAME = 16383;

// Query registry value of class root hKey, key path subKey, stores result in
// parameter out.
LSTATUS MyRegQueryValue(HKEY hKey, LPCWSTR subKey, DWORD dwType,
                        std::wstring &out);

// Get module file name, stores result in parameter out.
LSTATUS MyGetModuleFileName(HMODULE hModule, std::wstring &out);

// Get environment variable by name, C++ style, stores the value in parameter
// out.
LSTATUS MyGetEnvironmentVariable(LPCWSTR name, std::wstring &out);

// Create process by invoking CreateProcess, only pass command.
bool MyCreateProcess(const std::wstring &command, const std::wstring &workdir);

// Check if file lpPath exists.
bool FindFirstFileExists(LPCWSTR lpPath, DWORD dwFilter);

bool MyGetFileVersionInfo(const std::wstring &filePath, Version &version);