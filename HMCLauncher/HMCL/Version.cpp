#include "stdafx.h"
#include "version.h"

Version::Version(const std::wstring &rawString) {
  int idx = 0;
  ver[0] = ver[1] = ver[2] = ver[3] = 0;
  for (auto &i : rawString) {
    if (idx >= 4) break;
    if (i == '.')
      ++idx;
    else if (i == '_')
      ++idx;
    else if (isdigit(i))
      ver[idx] = ver[idx] * 10 + (i - L'0');
  }
}
