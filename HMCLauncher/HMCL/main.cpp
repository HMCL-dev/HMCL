#include "stdafx.h"
#include "main.h"
#include "os.h"
#include "java.h"
#include "lang.h"
#include <windows.h>

Version J8(TEXT("8"));

extern "C" {
_declspec(dllexport) DWORD NvOptimusEnablement = 0x00000001;
_declspec(dllexport) DWORD AmdPowerXpressRequestHighPerformance = 0x00000001;
}

void LaunchHMCLDirect(const std::wstring &javaPath, const std::wstring &workdir,
                  const std::wstring &jarPath, const std::wstring &jvmOptions) {
  if (MyCreateProcess(L"\"" + javaPath + L"\" " + jvmOptions + L" -jar \"" + jarPath + L"\"", workdir))
    exit(EXIT_SUCCESS);
}

void LaunchHMCL(const std::wstring &javaPath, const std::wstring &workdir,
               const std::wstring &jarPath, const std::wstring &jvmOptions) {
  Version javaVersion(L"");
  if (!MyGetFileVersionInfo(javaPath, javaVersion)) return;

  if (J8 <= javaVersion) {
    LaunchHMCLDirect(javaPath, workdir, jarPath, jvmOptions);
  }
}

void OpenHelpPage() {
    ShellExecute(0, 0, L"https://docs.hmcl.net/help.html", 0, 0, SW_SHOW);
}

int APIENTRY wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance,
                      LPWSTR lpCmdLine, int nCmdShow) {
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

  if (ERROR_SUCCESS != MyGetEnvironmentVariable(L"HMCL_JAVA_OPTS", jvmOptions)) {
    jvmOptions = L"-XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=15"; // Default Options
  }

  bool useChinese = GetUserDefaultUILanguage() == 2052; // zh-CN

  SYSTEM_INFO systemInfo;
  GetNativeSystemInfo(&systemInfo);
  // TODO: check whether the bundled JRE is valid.

  // First, try the Java packaged together.
  bool isX64   = (systemInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64);
  bool isARM64 = (systemInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_ARM64);

  if (isARM64) {
    LaunchHMCLDirect(L"jre-arm64\\bin\\javaw.exe", workdir, exeName, jvmOptions);
  }
  if (isX64) {
    LaunchHMCLDirect(L"jre-x64\\bin\\javaw.exe", workdir, exeName, jvmOptions);
  }
  LaunchHMCLDirect(L"jre-x86\\bin\\javaw.exe", workdir, exeName, jvmOptions);

  // Next, try the Java installed on thi computer.
  std::wstring path;
  int status;

  JavaScanner::scan(path, status);

  if (status != JavaScanner::JAVA_STATUS_NOT_FOUND) {
    MyPathAppend(path, std::wstring(L"bin\\javaw.exe"));
    LaunchHMCL(path, workdir, exeName, jvmOptions);
  }

  // Try java in PATH
  LaunchHMCLDirect(L"javaw", workdir, exeName, jvmOptions);

  LPCWSTR downloadLink;

  if (isARM64) {
    downloadLink = L"https://docs.hmcl.net/downloads/windows/arm64.html";
  } if (isX64) {
    downloadLink = L"https://docs.hmcl.net/downloads/windows/x86_64.html";
  } else {
    downloadLink = L"https://docs.hmcl.net/downloads/windows/x86.html";
  }

  if (IDOK == MessageBox(NULL, useChinese ? ERROR_PROMPT_ZH : ERROR_PROMPT, useChinese ? ERROR_TITLE_ZH : ERROR_TITLE, MB_ICONWARNING | MB_OKCANCEL)) {
    ShellExecute(0, 0, downloadLink, 0, 0, SW_SHOW);
  }
  return 1;
}
