#include "stdafx.h"
#include "os.h"

using namespace std;

LSTATUS MyRegQueryValue(HKEY hKey, LPCWSTR subKey, DWORD dwType, wstring &out)
{
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

LSTATUS MyGetModuleFileName(HMODULE hModule, std::wstring &out)
{
	DWORD res, size = MAX_PATH;
	out = wstring();
	out.resize(size);
	while ((res = GetModuleFileName(hModule, &out[0], size)) == size)
	{
		out.resize(size += MAX_PATH);
	}
	if (res == 0)
		return GetLastError();
	else
	{
		out.resize(size - MAX_PATH + res);
		return ERROR_SUCCESS;
	}
}

LSTATUS MyGetEnvironmentVariable(LPCWSTR name, std::wstring & out)
{
	DWORD res, size = MAX_PATH;
	out = wstring();
	out.resize(size);
	while ((res = GetEnvironmentVariable(name, &out[0], size)) == size)
	{
		out.resize(size += MAX_PATH);
	}
	if (res == 0)
		return GetLastError();
	else
	{
		out.resize(size - MAX_PATH + res);
		return ERROR_SUCCESS;
	}
}

bool MyCreateProcess(const std::wstring &command, const std::wstring &workdir)
{
	wstring writable_command = command;
	STARTUPINFO si;
	PROCESS_INFORMATION pi;
	si.cb = sizeof(si);
	ZeroMemory(&si, sizeof(si));
	ZeroMemory(&pi, sizeof(pi));

	if (workdir.empty()) {
		return CreateProcess(NULL, &writable_command[0], NULL, NULL, false, NORMAL_PRIORITY_CLASS, NULL, NULL, &si, &pi);
	}
	else {
		return CreateProcess(NULL, &writable_command[0], NULL, NULL, false, NORMAL_PRIORITY_CLASS, NULL, workdir.c_str(), &si, &pi);
	}
}

bool FindFirstFileExists(LPCWSTR lpPath, DWORD dwFilter)
{
	WIN32_FIND_DATA fd;
	HANDLE hFind = FindFirstFile(lpPath, &fd);
	bool bFilter = (false == dwFilter) ? true : fd.dwFileAttributes & dwFilter;
	bool ret = ((hFind != INVALID_HANDLE_VALUE) && bFilter) ? true : false;
	FindClose(hFind);
	return ret;
}

bool GetArch(bool & is64Bit)
{
#if _WIN64
	is64Bit = true;
	return true;
#elif _WIN32
	typedef BOOL(WINAPI *LPFN_ISWOW64PROCESS) (HANDLE, PBOOL);

	BOOL isWow64 = FALSE;

	// IsWow64Process is not available on all supported versions of Windows.
	// Use GetModuleHandle to get a handle to the DLL that contains the function
	// and GetProcAddress to get a pointer to the function if available.

	LPFN_ISWOW64PROCESS fnIsWow64Process = (LPFN_ISWOW64PROCESS)
		GetProcAddress(GetModuleHandle(TEXT("kernel32")), "IsWow64Process");

	if (fnIsWow64Process)
	{
		if (!fnIsWow64Process(GetCurrentProcess(), &isWow64))
			return false;

		is64Bit = isWow64;
		return true;
	}
	else // IsWow64Process is not supported, fail to detect.
		return false;

#else
#error _WIN64 and _WIN32 are both undefined.
#endif
}

bool MyGetFileVersionInfo(const std::wstring & filePath, Version &version)
{
	DWORD verHandle = 0;
	UINT size = 0;
	LPBYTE lpBuffer = NULL;
	VS_FIXEDFILEINFO *pFileInfo;
	DWORD dwSize = GetFileVersionInfoSize(filePath.c_str(), NULL);

	if (!dwSize)
		return false;

	LPBYTE data = new BYTE[dwSize];
	if (!GetFileVersionInfo(filePath.c_str(), 0, dwSize, data))
	{
		delete[] data;
		return false;
	}

	if (!VerQueryValue(data, TEXT("\\"), (LPVOID*)&pFileInfo, &size))
	{
		delete[] data;
		return false;
	}

	version = Version{
		(pFileInfo->dwFileVersionMS >> 16) & 0xFFFF,
		(pFileInfo->dwFileVersionMS >> 0) & 0xFFFF,
		(pFileInfo->dwFileVersionLS >> 16) & 0xFFFF,
		(pFileInfo->dwFileVersionLS >> 0) & 0xFFFF
	};
	return true;
}
