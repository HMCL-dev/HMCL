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

void FindJavaInDirAndLaunchJVM(const std::wstring &baseDir, const std::wstring &workdir, const std::wstring &jarPath) {
  std::wstring pattern = baseDir + L"*";

  WIN32_FIND_DATA data;
  HANDLE hFind = FindFirstFile(pattern.c_str(), &data);  // Search all subdirectory

  if (hFind != INVALID_HANDLE_VALUE) {
    do {
      std::wstring javaw = baseDir + data.cFileName + std::wstring(L"\\bin\\javaw.exe");
      if (FindFirstFileExists(javaw.c_str(), 0)) {
        LaunchJVM(javaw, workdir, jarPath);
      }
    } while (FindNextFile(hFind, &data));
    FindClose(hFind);
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

  SYSTEM_INFO systemInfo;
  GetNativeSystemInfo(&systemInfo);

  // TODO: check whether the bundled JRE is valid.
  // First try the Java packaged together.
  bool isX64 = (systemInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64);

  if (isX64) {
    RawLaunchJVM(L"jre-x64\\bin\\javaw.exe", workdir, exeName);
  }
  RawLaunchJVM(L"jre-x86\\bin\\javaw.exe", workdir, exeName);

  if (FindJava(path)) LaunchJVM(path + L"\\bin\\javaw.exe", workdir, exeName);

  // Or we try to search Java in C:\Program Files\Java.
  FindJavaInDirAndLaunchJVM(L"C:\\Program Files\\Java\\", workdir, exeName);

  // Consider C:\Program Files (x86)\Java
  FindJavaInDirAndLaunchJVM(L"C:\\Program Files (x86)\\Java\\", workdir, exeName);

  // Try java in PATH
  RawLaunchJVM(L"javaw", workdir, exeName);

  if (isX64) {
    OSVERSIONINFOEX osvi;
    DWORDLONG dwlConditionMask = 0;
    int op = VER_GREATER_EQUAL;

    // Initialize the OSVERSIONINFOEX structure.
    ZeroMemory(&osvi, sizeof(OSVERSIONINFOEX));
    osvi.dwOSVersionInfoSize = sizeof(OSVERSIONINFOEX);
    osvi.dwMajorVersion = 6;
    osvi.dwMinorVersion = 1;

    // Initialize the condition mask.
    VER_SET_CONDITION(dwlConditionMask, VER_MAJORVERSION, op);
    VER_SET_CONDITION(dwlConditionMask, VER_MINORVERSION, op);

    // Try downloading Java on Windows 7 or later
    if (VerifyVersionInfo(&osvi, VER_MAJORVERSION | VER_MINORVERSION, dwlConditionMask)) {
        // TODO
    }
  }


  MessageBox(NULL, ERROR_PROMPT, L"Error", MB_ICONERROR | MB_OK);
  ShellExecute(0, 0, L"https://www.microsoft.com/openjdk", 0, 0, SW_SHOW);
  return 1;
}
