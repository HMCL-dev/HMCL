#include "stdafx.h"
#include "main.h"
#include "os.h"
#include "java.h"

using namespace std;


void LaunchJVM(const wstring &javaPath, const wstring &jarPath)
{
	if (MyCreateProcess(L"\"" + javaPath + L"\" -XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=15 -jar \"" + jarPath + L"\""))
		exit(EXIT_SUCCESS);
}

int APIENTRY wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPWSTR lpCmdLine, int nCmdShow)
{
	wstring path, exeName;

	// Since Jar file is appended to this executable, we should first get the location of JAR file.
	if (ERROR_SUCCESS != MyGetModuleFileName(NULL, exeName))
		return 1;

	// TODO: check whether the bundled JRE is valid.
	// First try the Java packaged together.
	bool is64Bit = false;
	GetArch(is64Bit); // if failed to determine architecture of operating system, consider 32-bit.

	if (is64Bit)
	{
		LaunchJVM(L"jre-x64\\bin\\javaw.exe", exeName);
	}
	else
	{
		LaunchJVM(L"jre-x86\\bin\\javaw.exe", exeName);
	}

	if (FindJava(path))
		LaunchJVM(path + L"\\bin\\javaw.exe", exeName);

	// Try java in PATH
	LaunchJVM(L"javaw", exeName);

	// Or we try to search Java in C:\Program Files.
	{
		WIN32_FIND_DATA data;
		HANDLE hFind = FindFirstFile(L"C:\\Program Files\\Java\\*", &data);      // Search all subdirectory

		if (hFind != INVALID_HANDLE_VALUE) {
			do {
				wstring javaw = wstring(L"C:\\Program Files\\Java\\") + data.cFileName + wstring(L"\\bin\\javaw.exe");
				if (FindFirstFileExists(javaw.c_str(), 0)) {
					LaunchJVM(javaw, exeName);
				}
			} while (FindNextFile(hFind, &data));
			FindClose(hFind);
		}
	}

	// Consider C:\Program Files (x86)\Java
	{
		WIN32_FIND_DATA data;
		HANDLE hFind = FindFirstFile(L"C:\\Program Files (x86)\\Java\\*", &data);      // Search all subdirectory

		if (hFind != INVALID_HANDLE_VALUE) {
			do {
				wstring javaw = wstring(L"C:\\Program Files (x86)\\Java\\") + data.cFileName + L"\\bin\\javaw.exe";
				if (FindFirstFileExists(javaw.c_str(), 0)) {
					LaunchJVM(javaw, exeName);
				}
			} while (FindNextFile(hFind, &data));
			FindClose(hFind);
		}
	}

	MessageBox(NULL, L"Java installation cannot be found in this computer, please download it from https://java.com \n"
		L"未能在这台电脑上找到Java 8~Java 10，请从 https://java.com 下载安装Java", L"Error", MB_ICONERROR | MB_OK);
	ShellExecute(0, 0, L"https://java.com/", 0, 0, SW_SHOW);
	return 1;
}
