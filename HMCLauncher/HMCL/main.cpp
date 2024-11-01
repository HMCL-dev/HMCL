#include "stdafx.h"
#include "main.h"
#include "os.h"
#include "java.h"
#include "lang.h"
#include "install.h"
#include <windows.h>

Version J8(TEXT("8"));

extern "C" {
#ifdef _MSC_VER
__declspec(dllexport) DWORD NvOptimusEnablement = 0x00000001;
__declspec(dllexport) DWORD AmdPowerXpressRequestHighPerformance = 0x00000001;
#else
__attribute__ ((dllexport)) DWORD NvOptimusEnablement = 0x00000001;
__attribute__ ((dllexport)) DWORD AmdPowerXpressRequestHighPerformance = 0x00000001;
#endif
}

void LaunchHMCLDirect(const std::wstring &javaPath, const std::wstring &workdir,
                      const std::wstring &jarPath,
                      const std::wstring &jvmOptions) {
  HANDLE hProcess = MyCreateProcess(L"\"" + javaPath + L"\" " + jvmOptions + L" -jar \"" +
                                              jarPath + L"\"", workdir);
  if (hProcess == INVALID_HANDLE_VALUE) {
    return;
  }

  Sleep(5000);
  DWORD exitCode;
  if (!GetExitCodeProcess(hProcess, &exitCode)) {
    GetLastError();
    CloseHandle(hProcess);
    return;
  }
  CloseHandle(hProcess);
  if (exitCode == STILL_ACTIVE || exitCode == 0) {
    exit(EXIT_SUCCESS);
  }
}

void LaunchHMCL(const std::wstring &javaPath, const std::wstring &workdir,
                const std::wstring &jarPath, const std::wstring &jvmOptions) {
  Version javaVersion(L"");
  if (!MyGetFileVersionInfo(javaPath, javaVersion)) return;

  if (J8 <= javaVersion) {
    LaunchHMCLDirect(javaPath, workdir, jarPath, jvmOptions);
  }
}

int APIENTRY wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance,
                      LPWSTR lpCmdLine, int nCmdShow) {
  srand(GetTickCount64());

  std::wstring exeName, jvmOptions;

  // Since Jar file is appended to this executable, we should first get the
  // location of JAR file.
  if (ERROR_SUCCESS != MyGetModuleFileName(NULL, exeName)) return 1;

  std::wstring workdir;
  size_t last_slash = exeName.find_last_of(L"/\\");
  if (last_slash != std::wstring::npos && last_slash + 1 < exeName.size()) {
    workdir = exeName.substr(0, last_slash);
    exeName = exeName.substr(last_slash + 1);
  }

  if (ERROR_SUCCESS !=
      MyGetEnvironmentVariable(L"HMCL_JAVA_OPTS", jvmOptions)) {
    jvmOptions =
        L"-XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=15";  // Default Options
  }

  bool useChinese = GetUserDefaultUILanguage() == 2052;  // zh-CN

  SYSTEM_INFO systemInfo;
  GetNativeSystemInfo(&systemInfo);
  // TODO: check whether the bundled JRE is valid.

  // First, try the Java packaged together.
  bool isX64 =
      (systemInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64);
  bool isARM64 =
      (systemInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_ARM64);

  if (isARM64) {
    LaunchHMCLDirect(L"jre-arm64\\bin\\java.exe", workdir, exeName,
                     jvmOptions);
  }
  if (isX64) {
    LaunchHMCLDirect(L"jre-x64\\bin\\java.exe", workdir, exeName, jvmOptions);
  }
  LaunchHMCLDirect(L"jre-x86\\bin\\java.exe", workdir, exeName, jvmOptions);

  // Next, try the Java installed on this computer.
  std::wstring path;
  int status;

  ScanJava(systemInfo, path, status);

  if (status != JAVA_STATUS_NOT_FOUND) {
    MyPathAppend(path, std::wstring(L"bin\\java.exe"));
    LaunchHMCL(path, workdir, exeName, jvmOptions);
  }

  // Try java in PATH
  LaunchHMCLDirect(L"java", workdir, exeName, jvmOptions);

  std::wstring home;
  if (SUCCEEDED(MySHGetFolderPath(CSIDL_APPDATA, home)) ||
      SUCCEEDED(MySHGetFolderPath(CSIDL_PROFILE, home))) {
    MyPathNormalize(home);
    MyPathAppend(home, L".hmcl\\");

    std::wstring file = L"";
    if (isARM64) {
      file = L"/java/11.0.24+9/bellsoft-jre11.0.24+9-windows-aarch64-full.zip";
    } else if (isX64) {
      file = L"/java/11.0.24+9/bellsoft-jre11.0.24+9-windows-i586-full.zip";
    } else {
      file = L"/java/11.0.24+9/bellsoft-jre11.0.24+9-windows-amd64-full.zip";
    }
    InstallHMCLJRE(home, L"download.bell-sw.com", file);

    LaunchHMCLDirect(home + L"runtime\\bin\\java.exe", workdir, exeName, jvmOptions);
  }

  return 0;
}
