#pragma once
#include <windows.h>
#include <string>

// Find Java installation in system registry
bool FindJavaInRegistry(std::wstring &path);
