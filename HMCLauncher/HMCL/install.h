#include "stdafx.h"

#define HMCL_ERR_INVALID_JRE_PACK -129

int InstallHMCLJRE(const std::wstring &home, const std::wstring &domain,
                   const std::wstring &file);

int DownloadHMCLJRE(std::wstring &target, const std::wstring &domain,
                   const std::wstring &file);

int UncompressHMCLJRE(std::wstring jreDownloadedFile, std::wstring jreDownloadedDirectory);