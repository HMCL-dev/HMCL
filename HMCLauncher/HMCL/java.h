#pragma once
#include <windows.h>
#include <string>
#include "Version.h"

// Find Java installation in system registry
bool FindJavaInRegistry(std::wstring &path);

// Find Java Installation in registry and environment variable
bool FindJava(std::wstring &path);
