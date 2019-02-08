#pragma once

#include <string>

class Version
{
public:
	int ver[4];

	Version(const std::wstring &rawString);

	bool operator<(const Version &other) const
	{
		for (int i = 0; i < 4; ++i)
			if (ver[i] != other.ver[i])
				return ver[i] < other.ver[i];
		return false;
	}
};

