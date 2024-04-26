#pragma once
#include <windows.h>
#include <string>

namespace JavaScanner {
// These increasing values represent the priority of the Java.
// Be careful while changing their values!
const int JAVA_STATUS_NOT_FOUND = 0;
const int JAVA_STATUS_USABLE = 1;  // Java 8 - 11 (Exclude)
const int JAVA_STATUS_BEST = 2;    // Java 11 (Include) - ++

void scan(std::wstring& pathResult, int& status);
void scanRegistry(HKEY rootKey, LPCWSTR subKey, std::wstring& path,
                  int& status);
void scanFolder(std::wstring root, std::wstring& result, int& status);
boolean determine(std::wstring target, std::wstring& result, int& status);
inline void trace(std::wstring result, int status);
inline void trace(std::wstring javaHome);
}  // namespace JavaScanner