#include "stdafx.h"
#include "java.h"
#include "os.h"
#include "version.h"

const Version JAVA_8(L"1.8"), JAVA_11(L"11");

const LPCWSTR JDK_HKEYS[] = {L"SOFTWARE\\JavaSoft\\JDK",
                             L"SOFTWARE\\JavaSoft\\JRE",
                             L"SOFTWARE\\JavaSoft\\Java Development Kit",
                             L"SOFTWARE\\JavaSoft\\Java Runtime Environment"};

const LPCWSTR VENDOR_DIRS[] = {L"Java",  L"Microsoft",          L"BellSoft",
                               L"Zulu",  L"Eclipse Foundation", L"AdoptOpenJDK",
                               L"Semeru"};

const LPCWSTR PROGRAM_DIRS[] = {L"ProgramFiles", L"ProgramFiles(x86)",
                                L"ProgramW6432"};

/* Here we find the java from Environment Variable and Registry, which is fast,
 * and store the result to 'path'. */
void ScanJava(SYSTEM_INFO& systemInfo, std::wstring& result, int& status) {
  status = JAVA_STATUS_NOT_FOUND;

  // If 'HMCL_JAVA_HOME' is settled, this value MUST be used without any check as it can be used.
  if (ERROR_SUCCESS == MyGetEnvironmentVariable(L"HMCL_JAVA_HOME", result)) {
    MyPathNormalize(result);
    status = JAVA_STATUS_BEST;
    return;
  }

  {
    std::wstring buffer;
    if (SUCCEEDED(MySHGetFolderPath(CSIDL_APPDATA, buffer)) ||
        SUCCEEDED(MySHGetFolderPath(CSIDL_PROFILE, buffer))) {
      MyPathNormalize(buffer);
      MyPathAppend(buffer, L".hmcl\\runtime");
      MyPathAddBackslash(buffer);

      if (DeterminJavaHome(buffer, result, status) &&
        status != JAVA_STATUS_NOT_FOUND) {
      return;
    }
    }
  }

  std::wstring currentResult;
  if (ERROR_SUCCESS == MyGetEnvironmentVariable(L"JAVA_HOME", currentResult)) {
    MyPathNormalize(result);
    if (DeterminJavaHome(currentResult, result, status) &&
        status == JAVA_STATUS_BEST) {
      return;
    }
  }

  for (LPCWSTR hkey : JDK_HKEYS) {
    ScanJavaRegistry(HKEY_LOCAL_MACHINE, hkey, result, status);
    if (status == JAVA_STATUS_BEST) {
      return;
    }
  }

  std::wstring envPath;
  if (ERROR_SUCCESS == MyGetEnvironmentVariable(L"PATH", envPath)) {
    int length = envPath.size();
    int lastI = 0;
    for (int i = 0; i < length; i++) {
      if (envPath[i] == L';') {
        int partL = i - lastI;
        if (partL > 0) {
          std::wstring part = envPath.substr(lastI, partL);
          MyPathNormalize(part);
          MyPathAddBackslash(part);
          int partL2 = part.size();
          if (part[partL2 - 5] == L'\\' && part[partL2 - 4] == L'b' &&
              part[partL2 - 3] == L'i' && part[partL2 - 2] == L'n' &&
              part[partL2 - 1] == L'\\') {
            part.resize(partL2 - 5);
            if (DeterminJavaHome(part, result, status) &&
                status == JAVA_STATUS_BEST) {
              return;
            }
          }
        }

        lastI = i + 1;
      }
    }
  }

  std::wstring root;
  for (LPCWSTR program : PROGRAM_DIRS) {
    if (ERROR_SUCCESS != MyGetEnvironmentVariable(program, root)) {
      continue;
    }

    MyPathNormalize(root);
    for (LPCWSTR vender : VENDOR_DIRS) {
      std::wstring currentRoot = root + L"";
      MyPathAppend(currentRoot, vender);
      MyPathAddBackslash(currentRoot);
      ScanJavaFolder(currentRoot, result, status);
      if (status == JAVA_STATUS_BEST) {
        return;
      }
    }
  }
}

void ScanJavaRegistry(HKEY rootKey, LPCWSTR subKey, std::wstring& path,
                      int& status) {
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
      (result = RegOpenKeyEx(rootKey, subKey, 0, KEY_WOW64_64KEY | KEY_READ,
                             &hKey))) {
    return;
  }

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

  if (!cSubKeys) {
    return;
  }

  for (DWORD i = 0; i < cSubKeys; ++i) {
    cbName = MAX_KEY_LENGTH;
    if (ERROR_SUCCESS != (result = RegEnumKeyEx(hKey, i, javaVer, &cbName, NULL,
                                                NULL, NULL, NULL)))
      continue;

    HKEY javaKey;
    if (ERROR_SUCCESS != RegOpenKeyEx(hKey, javaVer, 0, KEY_READ, &javaKey))
      continue;

    std::wstring currentPath;
    if (ERROR_SUCCESS ==
        MyRegQueryValue(javaKey, L"JavaHome", REG_SZ, currentPath)) {
      MyPathNormalize(currentPath);
      Version v = Version(javaVer);

      if (status < JAVA_STATUS_BEST && v >= JAVA_11) {
        path = currentPath;
        status = JAVA_STATUS_BEST;
        break;
      } else if (status < JAVA_STATUS_USABLE && v >= JAVA_8) {
        path = currentPath;
        status = JAVA_STATUS_USABLE;
      }
    }
  }

  RegCloseKey(hKey);
}

void ScanJavaFolder(std::wstring root, std::wstring& result, int& status) {
  WIN32_FIND_DATA data;
  HANDLE hFind =
      FindFirstFile((root + L"*").c_str(), &data);  // Search all subdirectory

  if (hFind != INVALID_HANDLE_VALUE) {
    do {
      if (DeterminJavaHome(root + data.cFileName, result, status) &&
          status == JAVA_STATUS_BEST) {
        goto done;
      }
    } while (FindNextFile(hFind, &data));

  done:
    FindClose(hFind);
  }
}

boolean DeterminJavaHome(std::wstring target, std::wstring& result,
                         int& status) {
  Version version(L"");
  std::wstring currentRoot = target + L"";
  MyPathAppend(currentRoot, std::wstring(L"bin\\javaw.exe"));

  if (!MyGetFileVersionInfo(currentRoot, version)) return false;

  if (status < JAVA_STATUS_BEST && version >= JAVA_11) {
    result = target;
    status = JAVA_STATUS_BEST;
    return true;
  } else if (status < JAVA_STATUS_USABLE && version >= JAVA_8) {
    result = target;
    status = JAVA_STATUS_USABLE;
    return true;
  }
  return false;
}