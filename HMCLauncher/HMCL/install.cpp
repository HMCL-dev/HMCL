#include "stdafx.h"
#include "install.h"

int InstallHMCLJRE(const std::wstring &home, const std::wstring &domain,
                   const std::wstring &file) {
      WIN32_FIND_DATA ffd;
  std::wstring runtimeDirectory, jreDownloadedFile, jreDownloadedDirectory;

  runtimeDirectory = home + L"runtime";
  jreDownloadedFile = home + L".hmclauncher-jre-";
  jreDownloadedFile.append(std::to_wstring(rand()));
  jreDownloadedDirectory = jreDownloadedFile + L"";
  jreDownloadedFile.append(L".zip");

  HANDLE hFind = INVALID_HANDLE_VALUE;

  int err = 0;

  err = DownloadHMCLJRE(jreDownloadedFile, domain, file);
  if (err != 0) {
    goto cleanup;
  }

  if (!CreateDirectory(jreDownloadedDirectory.c_str(), NULL)) {
    err = GetLastError();
    goto cleanup;
  }

  UncompressHMCLJRE(jreDownloadedFile, jreDownloadedDirectory);

  hFind = FindFirstFile((jreDownloadedDirectory + L"\\*").c_str(), &ffd);
  if (hFind == INVALID_HANDLE_VALUE) {
    err = GetLastError();
    goto cleanup;
  }

  {
    std::wstring targetFile = std::wstring();
    do {
      std::wstring fileName = std::wstring(ffd.cFileName);
      if (fileName.size() <= 2 && fileName[0] == L'.') {
        continue;
      }
      if (ffd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
        if (targetFile.size() != 0) {
          err = HMCL_ERR_INVALID_JRE_PACK;
          goto cleanup;
        }

        targetFile.append(fileName);
      }
    } while (FindNextFile(hFind, &ffd) != 0);
    err = GetLastError();
    if (err != ERROR_NO_MORE_FILES) {
      goto cleanup;
    }
    err = 0;

    if (targetFile.size() == 0) {
      err = HMCL_ERR_INVALID_JRE_PACK;
      goto cleanup;
    }

    if (!MoveFile((jreDownloadedDirectory + L'\\' + targetFile).c_str(), runtimeDirectory.c_str())) {
      err = GetLastError();
      goto cleanup;
    }
  }

  cleanup:
  if (hFind != INVALID_HANDLE_VALUE) {
    CloseHandle(hFind);
  }
  RemoveDirectory(jreDownloadedDirectory.c_str());
  DeleteFile(jreDownloadedFile.c_str());
  return err;
}

int DownloadHMCLJRE(std::wstring &jreDownloadedFile, const std::wstring &domain,
                    const std::wstring &file) {
  int err = 0;
  HINTERNET hInternet = NULL, hConnection = NULL, hRequest = NULL;
  byte buffer[4096];
  HANDLE fd = NULL;

  {
    hInternet = InternetOpen(L"HMCLauncher", INTERNET_OPEN_TYPE_PRECONFIG, NULL,
                             NULL, 0);
    if (hInternet == NULL) {
      err = GetLastError();
      goto cleanup;
    }
    hConnection =
        InternetConnect(hInternet, domain.c_str(), INTERNET_DEFAULT_HTTPS_PORT,
                        NULL, NULL, INTERNET_SERVICE_HTTP, 0, 0);
    if (hConnection == NULL) {
      err = GetLastError();
      goto cleanup;
    }

    const wchar_t *ACCEPTS[] = {L"application/zip", NULL};
    hRequest = HttpOpenRequest(hConnection, L"GET", file.c_str(), NULL, NULL,
                               ACCEPTS, INTERNET_FLAG_SECURE, 0);
    if (hRequest == NULL) {
      err = GetLastError();
      goto cleanup;
    }

    if (!HttpSendRequest(hRequest, NULL, 0, NULL, 0)) {
      err = GetLastError();
      goto cleanup;
    }

    {
      DWORD receivedCount, unused;

      fd = CreateFile(jreDownloadedFile.c_str(), GENERIC_WRITE, 0, NULL,
                             CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
      if (fd == INVALID_HANDLE_VALUE) {
        err = GetLastError();
        goto cleanup;
      }

      while (InternetReadFile(hRequest, buffer, 4096, &receivedCount) &&
             receivedCount) {
        if (!WriteFile(fd, buffer, receivedCount, &unused, NULL)) {
          err = GetLastError();
          goto cleanup;
        }
      }
    }
  }

cleanup:
  if (hRequest != NULL) {
    InternetCloseHandle(hRequest);
  }
  if (hConnection != NULL) {
    InternetCloseHandle(hConnection);
  }
  if (hInternet != NULL) {
    InternetCloseHandle(hInternet);
  }
  if (fd != NULL) {
    CloseHandle(fd);
  }
  return err;
}

int UncompressHMCLJRE(std::wstring jreDownloadedFile, std::wstring jreDownloadedDirectory) {
  std::wstring command = L"tar.exe -xf \"" + jreDownloadedFile + L"\"";
  int err = 0;

  STARTUPINFO si;
  PROCESS_INFORMATION pi;

  si.cb = sizeof(si);
  ZeroMemory(&si, sizeof(si));
  ZeroMemory(&pi, sizeof(pi));
  if (!CreateProcess(NULL, &command[0], NULL, NULL, false,
                           NORMAL_PRIORITY_CLASS | CREATE_NO_WINDOW, NULL, jreDownloadedDirectory.c_str(), &si,
                           &pi)) {
    return GetLastError();
  }

  while (true) {
    DWORD exitCode;
    if (!GetExitCodeProcess(pi.hProcess, &exitCode)) {
      err = GetLastError();
      goto cleanup;
    }
    if (exitCode != STILL_ACTIVE) {
      if (exitCode != 0) {
        err = exitCode;
        goto cleanup;
      }
      break;
    }
  }

  cleanup:
  CloseHandle(si.hStdInput);
  CloseHandle(si.hStdOutput);
  CloseHandle(si.hStdError);
  CloseHandle(pi.hProcess);
  CloseHandle(pi.hThread);

  return err;
}