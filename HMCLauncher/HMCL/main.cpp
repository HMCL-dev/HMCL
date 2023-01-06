#include "stdafx.h"
#include "main.h"
#include "os.h"
#include "java.h"
#include "lang.h"
#include "debug.h"

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
  DEBUG_LOG("Check the Java path: %ls", javaPath.c_str())
  std::wstring command = L"\"" + javaPath + L"\" " + jvmOptions + L" -jar \"" + jarPath + L"\"";
  if (MyCreateProcess(command, workdir)) {
    DEBUG_LOG("Start Process: %ls", command.c_str())
    if (debugEnabled) system("pause");
    exit(EXIT_SUCCESS);
  }
}

void LaunchJVM(const std::wstring &javaPath, const std::wstring &workdir,
               const std::wstring &jarPath, const std::wstring &jvmOptions) {
  Version javaVersion(L"");
  if (!MyGetFileVersionInfo(javaPath, javaVersion)) {
    DEBUG_LOG("Failed to get the java version of path %ls", javaPath.c_str());
    return;
  }

  if (J8 <= javaVersion) {
    RawLaunchJVM(javaPath, workdir, jarPath, jvmOptions);
  } else {
    DEBUG_LOG("Ignore %ls because the version is lower than Java 8", javaPath.c_str());
  }
}

void FindJavaInDirAndLaunchJVM(const std::wstring &baseDir, const std::wstring &workdir,
                               const std::wstring &jarPath, const std::wstring &jvmOptions) {
  std::wstring pattern = baseDir + L"*";

  WIN32_FIND_DATA data;
  HANDLE hFind = FindFirstFile(pattern.c_str(), &data);  // Search all subdirectory

  if (hFind != INVALID_HANDLE_VALUE) {
    do {
      std::wstring java = baseDir + data.cFileName + std::wstring(L"\\bin\\java.exe");
      if (FindFirstFileExists(java.c_str(), 0)) {
        LaunchJVM(java, workdir, jarPath, jvmOptions);
      }
    } while (FindNextFile(hFind, &data));
    FindClose(hFind);
  }
}

int APIENTRY wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance,
                      LPWSTR lpCmdLine, int nCmdShow) {
  if (__argc == 2 && lstrcmp(L"--debug", __wargv[1]) == 0) {
    EnableDebug();
    DEBUG_LOG("Debug Mode: true")
  }

  std::wstring path, exeName, jvmOptions;

  // Since Jar file is appended to this executable, we should first get the
  // location of JAR file.
  if (ERROR_SUCCESS != MyGetModuleFileName(NULL, exeName)) {
    if (debugEnabled) {
      DEBUG_LOG("Unable to determine the path of exe")
      system("pause");
    }
    return 1;
  }

  std::wstring workdir;
  size_t last_slash = exeName.find_last_of(L"/\\");
  if (last_slash != std::wstring::npos && last_slash + 1 < exeName.size()) {
    workdir = exeName.substr(0, last_slash);
    exeName = exeName.substr(last_slash + 1);
  }

  DEBUG_LOG("EXE Path: %ls", exeName.c_str())
  DEBUG_LOG("Working Directory: %ls", workdir.c_str())

  if (ERROR_SUCCESS == MyGetEnvironmentVariable(L"HMCL_JAVA_OPTS", jvmOptions)) {
    DEBUG_LOG("HMCL_JAVA_OPTS: %ls", jvmOptions.c_str());
  } else {
    jvmOptions = L"-Xmx1G -XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=15";  // Default Options
    DEBUG_LOG("HMCL_JAVA_OPTS not set, use default value");
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
  DEBUG_LOG("Windows 7 or later: %ls", isWin7OrLater ? L"true" : L"false")

  SYSTEM_INFO systemInfo;
  GetNativeSystemInfo(&systemInfo);
  // TODO: check whether the bundled JRE is valid.
  // First try the Java packaged together.
  bool isX64   = (systemInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64);
  bool isARM64 = (systemInfo.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_ARM64);
  DEBUG_LOG("OS Architecture: %ls", isARM64 ? L"ARM64" : isX64 ? L"x64" : L"x86")

  if (isARM64) {
    RawLaunchJVM(L"jre-arm64\\bin\\java.exe", workdir, exeName, jvmOptions);
  }
  if (isX64) {
    RawLaunchJVM(L"jre-x64\\bin\\java.exe", workdir, exeName, jvmOptions);
  }
  RawLaunchJVM(L"jre-x86\\bin\\java.exe", workdir, exeName, jvmOptions);

  if (ERROR_SUCCESS == MyGetEnvironmentVariable(L"HMCL_JAVA_HOME", path)) {
    DEBUG_LOG("HMCL_JAVA_HOME: %ls", path.c_str());
    RawLaunchJVM(path + L"\\bin\\java.exe", workdir, exeName, jvmOptions);
  } else {
    DEBUG_LOG("HMCL_JAVA_HOME not set");
  }

  if (ERROR_SUCCESS == MyGetEnvironmentVariable(L"JAVA_HOME", path)) {
    DEBUG_LOG("JAVA_HOME: %ls", path.c_str());
    LaunchJVM(path + L"\\bin\\java.exe", workdir, exeName, jvmOptions);
  } else {
    DEBUG_LOG("JAVA_HOME not set");
  }

  if (FindJavaInRegistry(path)) {
    DEBUG_LOG("Found Java in the registry: %ls", path.c_str());
    LaunchJVM(path + L"\\bin\\java.exe", workdir, exeName, jvmOptions);
  } else {
    DEBUG_LOG("No Java found in the registry");
  }

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
  RawLaunchJVM(L"java", workdir, exeName, jvmOptions);

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
      downloadLink = L"https://aka.ms/download-jdk/microsoft-jdk-17-windows-aarch64.msi";
    } else if (isX64) {
      downloadLink = L"https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.msi";
    } else {
      downloadLink = L"https://download.bell-sw.com/java/17.0.5+8/bellsoft-jre17.0.5+8-windows-i586-full.msi";
    }
  } else {
    downloadLink = L"https://www.java.com";
  }
  DEBUG_LOG("Unable to find Java, guide the user to access %ls", downloadLink)

  if (IDOK == MessageBox(NULL, useChinese ? ERROR_PROMPT_ZH : ERROR_PROMPT, useChinese ? ERROR_TITLE_ZH : ERROR_TITLE, MB_ICONWARNING | MB_OKCANCEL)) {
    ShellExecute(0, 0, downloadLink, 0, 0, SW_SHOW);
  }

  if (debugEnabled) system("pause");
  return 1;
}
