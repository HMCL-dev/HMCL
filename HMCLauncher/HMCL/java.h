#pragma once
#include <windows.h>
#include <string>

// Find Java installation in system registry
bool FindJavaInRegistry(std::wstring &path);

// Find Java Installation in registry and environment variable
bool FindJava(std::wstring &path);
