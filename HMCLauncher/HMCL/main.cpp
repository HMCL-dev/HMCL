#include "stdafx.h"
#include "main.h"
#include "os.h"
#include "java.h"
#include "lang.h"

Version J8(TEXT("8"));

extern "C" {
_declspec(dllexport) DWORD NvOptimusEnablement = 0x00000001;
_declspec(dllexport) DWORD AmdPowerXpressRequestHighPerformance = 0x00000001;
}

void RawLaunchJVM(const std::wstring &javaPath, const std::wstring &workdir,
                  const std::wstring &jarPath) {
  if (MyCreateProcess(
          L"\"" + javaPath +
              L"\" -XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=15 -jar \"" +
              jarPath + L"\"",
          workdir))
    exit(EXIT_SUCCESS);
}

void LaunchJVM(const std::wstring &javaPath, const std::wstring &workdir,
               const std::wstring &jarPath) {
  Version javaVersion(L"");
  if (!MyGetFileVersionInfo(javaPath, javaVersion)) return;

  if (J8 <= javaVersion) {
    RawLaunchJVM(javaPath, workdir, jarPath);
  }
}

int APIENTRY wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance,
                      LPWSTR lpCmdLine, int nCmdShow) {
  std::wstring path, exeName;

  // Since Jar file is appended to this executable, we should first get the
  // location of JAR file.
  if (ERROR_SUCCESS != MyGetModuleFileName(NULL, exeName)) return 1;

  std::wstring workdir;
  size_t last_slash = exeName.find_last_of(L"/\\");
  if (last_slash != std::wstring::npos && last_slash + 1 < exeName.size()) {
    workdir = exeName.substr(0, last_slash);
    exeName = exeName.substr(last_slash + 1);
  }

  // TODO: check whether the bundled JRE is valid.
  // First try the Java packaged together.
  bool is64Bit = false;
  GetArch(is64Bit);  // if failed to determine architecture of operating system,
                     // consider 32-bit.

  if (is64Bit) {
    RawLaunchJVM(L"jre-x64\\bin\\javaw.exe", workdir, exeName);
  } else {
    RawLaunchJVM(L"jre-x86\\bin\\javaw.exe", workdir, exeName);
  }

  if (FindJava(path)) LaunchJVM(path + L"\\bin\\javaw.exe", workdir, exeName);

  // Or we try to search Java in C:\Program Files.
  {
    WIN32_FIND_DATA data;
    HANDLE hFind = FindFirstFile(L"C:\\Program Files\\Java\\*",
                                 &data);  // Search all subdirectory

    if (hFind != INVALID_HANDLE_VALUE) {
      do {
        std::wstring javaw = std::wstring(L"C:\\Program Files\\Java\\") +
                             data.cFileName + std::wstring(L"\\bin\\javaw.exe");
        if (FindFirstFileExists(javaw.c_str(), 0)) {
          LaunchJVM(javaw, workdir, exeName);
        }
      } while (FindNextFile(hFind, &data));
      FindClose(hFind);
    }
  }

  // Consider C:\Program Files (x86)\Java
  {
    WIN32_FIND_DATA data;
    HANDLE hFind = FindFirstFile(L"C:\\Program Files (x86)\\Java\\*",
                                 &data);  // Search all subdirectory

    if (hFind != INVALID_HANDLE_VALUE) {
      do {
        std::wstring javaw = std::wstring(L"C:\\Program Files (x86)\\Java\\") +
                             data.cFileName + L"\\bin\\javaw.exe";
        if (FindFirstFileExists(javaw.c_str(), 0)) {
          LaunchJVM(javaw, workdir, exeName);
        }
      } while (FindNextFile(hFind, &data));
      FindClose(hFind);
    }
  }

  // Try java in PATH
  RawLaunchJVM(L"javaw", workdir, exeName);

  MessageBox(NULL, ERROR_PROMPT, L"Error", MB_ICONERROR | MB_OK);
  ShellExecute(0, 0, L"https://java.com/", 0, 0, SW_SHOW);
  return 1;
}
