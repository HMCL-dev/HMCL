#include "stdafx.h"
#include "java.h"
#include "os.h"
#include "version.h"

const Version JAVA_8(L"1.8"), JAVA_11(L"11");

const LPCWSTR JDK_NEW = L"SOFTWARE\\JavaSoft\\JDK";
const LPCWSTR JRE_NEW = L"SOFTWARE\\JavaSoft\\JRE";
const LPCWSTR JDK_OLD = L"SOFTWARE\\JavaSoft\\Java Development Kit";
const LPCWSTR JRE_OLD = L"SOFTWARE\\JavaSoft\\Java Runtime Environment";

bool oldJavaFound = false;

bool FindJavaByRegistryKey(HKEY rootKey, LPCWSTR subKey, std::wstring& path) {
  WCHAR javaVer[MAX_KEY_LENGTH];  // buffer for subkey name, special for
                                  // JavaVersion
  DWORD cbName;                   // size of name string
  DWORD cSubKeys = 0;             // number of subkeys
  DWORD cbMaxSubKey;              // longest subkey size
  DWORD cValues;                  // number of values for key
  DWORD cchMaxValue;              // longest value name
  DWORD cbMaxValueData;           // longest value data
  LSTATUS result;

  HKEY hKey;
  if (ERROR_SUCCESS !=
      (result =
           RegOpenKeyEx(rootKey, subKey, 0, KEY_WOW64_64KEY | KEY_READ, &hKey)))
    return false;

  RegQueryInfoKey(hKey,             // key handle
                  NULL,             // buffer for class name
                  NULL,             // size of class string
                  NULL,             // reserved
                  &cSubKeys,        // number of subkeys
                  &cbMaxSubKey,     // longest subkey size
                  NULL,             // longest class string
                  &cValues,         // number of values for this key
                  &cchMaxValue,     // longest value name
                  &cbMaxValueData,  // longest value data
                  NULL,             // security descriptor
                  NULL);            // last write time

  if (!cSubKeys) return false;

  bool flag = false;
  for (DWORD i = 0; i < cSubKeys; ++i) {
    cbName = MAX_KEY_LENGTH;
    if (ERROR_SUCCESS != (result = RegEnumKeyEx(hKey, i, javaVer, &cbName, NULL,
                                                NULL, NULL, NULL)))
      continue;

    HKEY javaKey;
    if (ERROR_SUCCESS != RegOpenKeyEx(hKey, javaVer, 0, KEY_READ, &javaKey))
      continue;

    if (ERROR_SUCCESS == MyRegQueryValue(javaKey, L"JavaHome", REG_SZ, path)) {
      if (Version(javaVer) < JAVA_8)
        oldJavaFound = true;
      else
        flag = true;
    }

    if (flag) break;
  }

  RegCloseKey(hKey);

  return flag;
}

bool FindJavaInRegistry(std::wstring& path) {
  return FindJavaByRegistryKey(HKEY_LOCAL_MACHINE, JDK_NEW, path) ||
         FindJavaByRegistryKey(HKEY_LOCAL_MACHINE, JRE_NEW, path) ||
         FindJavaByRegistryKey(HKEY_LOCAL_MACHINE, JDK_OLD, path) ||
         FindJavaByRegistryKey(HKEY_LOCAL_MACHINE, JRE_OLD, path);
}
