#include "stdafx.h"
#include "os.h"

LSTATUS MyRegQueryValue(HKEY hKey, LPCWSTR subKey, DWORD dwType,
                        std::wstring &out) {
  DWORD dwSize = 0;
  LSTATUS ret = RegQueryValueEx(hKey, subKey, 0, &dwType, NULL, &dwSize);
  if (ret != ERROR_SUCCESS) return ret;
  WCHAR *buffer = new WCHAR[dwSize];
  ret = RegQueryValueEx(hKey, subKey, 0, &dwType, (LPBYTE)buffer, &dwSize);
  if (ret != ERROR_SUCCESS) return ret;
  out = buffer;
  delete[] buffer;
  return ERROR_SUCCESS;
}

LSTATUS MyGetModuleFileName(HMODULE hModule, std::wstring &out) {
  DWORD res, size = MAX_PATH;
  out = std::wstring();
  out.resize(size);
  while ((res = GetModuleFileName(hModule, &out[0], size)) == size) {
    out.resize(size += MAX_PATH);
  }
  if (res == 0)
    return GetLastError();
  else {
    out.resize(size - MAX_PATH + res);
    return ERROR_SUCCESS;
  }
}

LSTATUS MyGetEnvironmentVariable(LPCWSTR name, std::wstring &out) {
  out = std::wstring();
  int size = MAX_PATH;
  while (true) {
    out.resize(size);
    DWORD res = GetEnvironmentVariable(name, &out[0], size);
    if (res == 0) {
      return GetLastError();
    } else if (res == size) {
      // This should NOT happen.
      // https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-getenvironmentvariable
      return ERROR_INVALID_FUNCTION;
    } else {
      out.resize(res);
      if (res < size) {
        return ERROR_SUCCESS;
      }
      // Allocate enough memory and try again.
      size = res;
    }
  }
}

bool MyCreateProcess(const std::wstring &command, const std::wstring &workdir) {
  std::wstring writable_command = command;
  STARTUPINFO si;
  PROCESS_INFORMATION pi;
  si.cb = sizeof(si);
  ZeroMemory(&si, sizeof(si));
  ZeroMemory(&pi, sizeof(pi));

  if (workdir.empty()) {
    return CreateProcess(NULL, &writable_command[0], NULL, NULL, false,
                         NORMAL_PRIORITY_CLASS, NULL, NULL, &si, &pi);
  } else {
    return CreateProcess(NULL, &writable_command[0], NULL, NULL, false,
                         NORMAL_PRIORITY_CLASS, NULL, workdir.c_str(), &si,
                         &pi);
  }
}

bool FindFirstFileExists(LPCWSTR lpPath, DWORD dwFilter) {
  WIN32_FIND_DATA fd;
  HANDLE hFind = FindFirstFile(lpPath, &fd);
  bool bFilter = (false == dwFilter) ? true : fd.dwFileAttributes & dwFilter;
  bool ret = ((hFind != INVALID_HANDLE_VALUE) && bFilter) ? true : false;
  FindClose(hFind);
  return ret;
}

bool MyGetFileVersionInfo(const std::wstring &filePath, Version &version) {
  DWORD verHandle = 0;
  UINT size = 0;
  LPBYTE lpBuffer = NULL;
  VS_FIXEDFILEINFO *pFileInfo;
  DWORD dwSize = GetFileVersionInfoSize(filePath.c_str(), NULL);

  if (!dwSize) return false;

  LPBYTE data = new BYTE[dwSize];
  if (!GetFileVersionInfo(filePath.c_str(), 0, dwSize, data)) {
    delete[] data;
    return false;
  }

  if (!VerQueryValue(data, TEXT("\\"), (LPVOID *)&pFileInfo, &size)) {
    delete[] data;
    return false;
  }

  version = Version{(pFileInfo->dwFileVersionMS >> 16) & 0xFFFF,
                    (pFileInfo->dwFileVersionMS >> 0) & 0xFFFF,
                    (pFileInfo->dwFileVersionLS >> 16) & 0xFFFF,
                    (pFileInfo->dwFileVersionLS >> 0) & 0xFFFF};
  return true;
}

void MyPathNormalize(std::wstring &path) {
  int size = path.size();
  for (int i = 0; i < size; i++) {
    if (path[size] == L'/') {
      path[size] = L'\\';
    }
  }
}

void MyPathAppend(std::wstring &filePath, const std::wstring &more) {
  if (filePath.back() != L'\\') {
    filePath += L'\\';
  }

  filePath += more;
}

void MyPathAddBackslash(std::wstring &filePath) {
  if (filePath.back() != L'\\') {
    filePath += L'\\';
  }
}

LSTATUS MyGetTempFile(const std::wstring &prefixString, const std::wstring &ext,
                      std::wstring &out) {
  out.resize(MAX_PATH);
  DWORD res = GetTempPath(MAX_PATH, &out[0]);
  if (res == 0) {
    return GetLastError();
  }

  out.resize(res);

  GUID guid;
  CoCreateGuid(&guid);

  WCHAR buffer[MAX_PATH];
  int n = StringFromGUID2(guid, buffer, MAX_PATH);
  if (n == 0) {
    return CO_E_PATHTOOLONG;
  }

  MyPathAddBackslash(out);
  out += prefixString;
  out += buffer;
  out += L'.';
  out += ext;

  return ERROR_SUCCESS;
}

void MyAppendPathToCommandLine(std::wstring &commandLine,
                               const std::wstring &path) {
  commandLine += L'"';
  for (size_t i = 0; i < path.size(); i++) {
    WCHAR ch = path[i];
    if (ch == L'\\' && (i + 1 == path.size() || path[i + 1] == L'"')) {
      commandLine += L"\\\\";
    } else if (ch == L'"') {
      commandLine += L"\\\"";
    } else {
      commandLine += ch;
    }
  }
  commandLine += L'"';
}