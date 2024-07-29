#pragma once
#include <windows.h>
#include <string>

// These increasing values represent the priority of the Java.
// Be careful while changing their values!
const int JAVA_STATUS_NOT_FOUND = 0;
const int JAVA_STATUS_USABLE = 1;  // Java 8 - 11 (Exclude)
const int JAVA_STATUS_BEST = 2;    // Java 11 (Include) - ++

void ScanJava(SYSTEM_INFO& systemInfo, std::wstring& result, int& status);

void ScanJavaRegistry(HKEY rootKey, LPCWSTR subKey, std::wstring& path,
                      int& status);

void ScanJavaFolder(std::wstring root, std::wstring& result, int& status);

boolean DeterminJavaHome(std::wstring target, std::wstring& result,
                         int& status);