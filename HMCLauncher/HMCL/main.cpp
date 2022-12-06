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

LPCWSTR VENDOR_DIRS[] = {
  L"Java", L"Microsoft", L"BellSoft", L"Zulu", L"Eclipse Foundation", L"AdoptOpenJDK", L"Semeru"
};

void RawLaunchJVM(const std::wstring &javaPath, const std::wstring &workdir,
                  const std::wstring &jarPath, const std::wstring &jvmOptions) {
  if (MyCreateProcess(L"\"" + javaPath + L"\" " + jvmOptions + L" -jar \"" + jarPath + L"\"", workdir))
    exit(EXIT_SUCCESS);
}

void LaunchJVM(const std::wstring &javaPath, const std::wstring &workdir,
               const std::wstring &jarPath, const std::wstring &jvmOptions) {
  Version javaVersion(L"");
  if (!MyGetFileVersionInfo(javaPath, javaVersion)) return;

  if (J8 <= javaVersion) {
    RawLaunchJVM(javaPath, workdir, jarPath, jvmOptions);
  }
}

void FindJavaInDirAndLaunchJVM(const std::wstring &baseDir, const std::wstring &workdir,
                               const std::wstring &jarPath, const std::wstring &jvmOptions) {
  std::wstring pattern = baseDir + L"*";

  WIN32_FIND_DATA data;
  HANDLE hFind = FindFirstFile(pattern.c_str(), &data);  // Search all subdirectory

  if (hFind != INVALID_HANDLE_VALUE) {
    do {
      std::wstring javaw = baseDir + data.cFileName + std::wstring(L"\\bin\\javaw.exe");
      if (FindFirstFileExists(javaw.c_str(), 0)) {
        LaunchJVM(javaw, workdir, jarPath, jvmOptions);
      }
    } while (FindNextFile(hFind, &data));
    FindClose(hFind);
  }
}

int APIENTRY wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance,
                      LPWSTR lpCmdLine, int nCmdShow) {
  std::wstring path, exeName, jvmOptions;

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
  bool isWin7OrLater = VerifyVersionInfo(&osvi, VER_MAJORVERSION | VER_MINORVERSION, dwlConditionMask);

  SYSTEM_INFO systemInfo;
  GetNativeSystemInfo(&systemInfo);
  // TODO: check whether the bundled JRE is valid.
  // First try the Java packaged together.
  bool isX64   = (systemInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64);
  bool isARM64 = (systemInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_ARM64);

  if (isARM64) {
    RawLaunchJVM(L"jre-arm64\\bin\\javaw.exe", workdir, exeName, jvmOptions);
  }
  if (isX64) {
    RawLaunchJVM(L"jre-x64\\bin\\javaw.exe", workdir, exeName, jvmOptions);
  }
  RawLaunchJVM(L"jre-x86\\bin\\javaw.exe", workdir, exeName, jvmOptions);

  if (FindJava(path)) LaunchJVM(path + L"\\bin\\javaw.exe", workdir, exeName, jvmOptions);

  std::wstring programFiles;

  // Or we try to search Java in C:\Program Files
  if (!SUCCEEDED(MySHGetFolderPath(CSIDL_PROGRAM_FILES, programFiles))) programFiles = L"C:\\Program Files\\";
  for (LPCWSTR vendorDir : VENDOR_DIRS) {
    std::wstring dir = programFiles;
    MyPathAppend(dir, vendorDir);
    MyPathAddBackslash(dir);

    FindJavaInDirAndLaunchJVM(dir, workdir, exeName, jvmOptions);
  }

  // Consider C:\Program Files (x86)
  if (!SUCCEEDED(MySHGetFolderPath(CSIDL_PROGRAM_FILESX86, programFiles))) programFiles = L"C:\\Program Files (x86)\\";
  for (LPCWSTR vendorDir : VENDOR_DIRS) {
    std::wstring dir = programFiles;
    MyPathAppend(dir, vendorDir);
    MyPathAddBackslash(dir);

    FindJavaInDirAndLaunchJVM(dir, workdir, exeName, jvmOptions);
  }

  // Try java in PATH
  RawLaunchJVM(L"javaw", workdir, exeName, jvmOptions);

  std::wstring hmclJavaDir;
  {
    std::wstring buffer;
    if (SUCCEEDED(MySHGetFolderPath(CSIDL_APPDATA, buffer)) || SUCCEEDED(MySHGetFolderPath(CSIDL_PROFILE, buffer))) {
      MyPathAppend(buffer, L".hmcl");
      MyPathAppend(buffer, L"java");
      if (isARM64) {
        MyPathAppend(buffer, L"windows-arm64");
      } else if (isX64) {
        MyPathAppend(buffer, L"windows-x86_64");
      } else {
        MyPathAppend(buffer, L"windows-x86");
      }
      MyPathAddBackslash(buffer);
      hmclJavaDir = buffer;
    }
  }

  if (!hmclJavaDir.empty()) {
    FindJavaInDirAndLaunchJVM(hmclJavaDir, workdir, exeName, jvmOptions);
  }

error:
  LPCWSTR downloadLink;

  if (isWin7OrLater) {
    if (isARM64) {
      downloadLink = L"https://docs.hmcl.net/downloads/windows/arm64.html";
    } if (isX64) {
      downloadLink = L"https://docs.hmcl.net/downloads/windows/x86_64.html";
    } else {
      downloadLink = L"https://docs.hmcl.net/downloads/windows/x86.html";
    }
  } else {
    downloadLink = L"https://docs.hmcl.net/downloads/java.html";
  }

  if (IDOK == MessageBox(NULL, useChinese ? ERROR_PROMPT_ZH : ERROR_PROMPT, useChinese ? ERROR_TITLE_ZH : ERROR_TITLE, MB_ICONWARNING | MB_OKCANCEL)) {
    ShellExecute(0, 0, downloadLink, 0, 0, SW_SHOW);
  }
  return 1;
}
