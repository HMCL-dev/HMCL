#!/bin/bash
set -e

modules=(base controls fxml graphics media web)
arches=(linux mac win)
version=16

echo '['
for module in ${modules[@]}; do
	if [[ ! "$module" == "${modules[0]}" ]]; then
		echo ','
	fi
	echo '  {'
	echo '    "module": "javafx.'$module'",'
	echo '    "groupId": "org.openjfx",'
	echo '    "artifactId": "javafx-'$module'",'
	echo '    "version": "'$version'",'
	echo '    "sha1": {'
	for arch in ${arches[@]}; do
		if [[ ! "$arch" == "${arches[0]}" ]]; then
			echo ','
		fi
		echo -n '      "'$arch'": "'$(curl -Ss "https://repo1.maven.org/maven2/org/openjfx/javafx-$module/$version/javafx-$module-$version-$arch.jar.sha1")'"'
	done
	echo
	echo '    }'
	echo -n '  }'
done
echo
echo ']'
