#pragma once

#include <string>

class Version
{
public:
	int ver[4];

	Version(const std::wstring &rawString);

	template <typename T>
	Version(std::initializer_list<T> ver_list)
	{
		int i = 0;
		for (const auto &data : ver_list)
		{
			if (i >= 4)
				break;
			ver[i++] = data;
		}
	}

	bool operator<(const Version &other) const
	{
		for (int i = 0; i < 4; ++i)
			if (ver[i] != other.ver[i])
				return ver[i] < other.ver[i];
		return false;
	}

	bool operator<=(const Version &other) const
	{
		for (int i = 0; i < 4; ++i)
			if (ver[i] != other.ver[i])
				return ver[i] < other.ver[i];
		return true;
	}
};

