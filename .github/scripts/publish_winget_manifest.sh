#!/usr/bin/env bash

mkdir $PACKAGE_IDENTIFIER

cat > $PACKAGE_IDENTIFIER/$PACKAGE_IDENTIFIER.yaml << EOF
PackageIdentifier: $PACKAGE_IDENTIFIER
PackageVersion: $PACKAGE_VERSION
DefaultLocale: en-US
ManifestType: version
ManifestVersion: 1.10.0
EOF

echo "$PACKAGE_IDENTIFIER.yaml"
cat $PACKAGE_IDENTIFIER/$PACKAGE_IDENTIFIER.yaml

cat > $PACKAGE_IDENTIFIER/$PACKAGE_IDENTIFIER.locale.en-US.yaml << EOF
PackageIdentifier: $PACKAGE_IDENTIFIER
PackageVersion: $PACKAGE_VERSION
PackageLocale: en-US
Publisher: huanghongxun
PublisherUrl: https://github.com/HMCL-dev
PublisherSupportUrl: https://github.com/HMCL-dev/HMCL/issues
PackageName: $PACKAGE_NAME
PackageUrl: https://github.com/HMCL-dev/HMCL
License: GPL-3.0
Copyright: Copyright (C) 2025 huangyuhui
ShortDescription: A Minecraft Launcher which is multi-functional, cross-platform and popular
Tags:
- javafx
- minecraft
- minecraft-launcher
ReleaseNotesUrl: https://docs.hmcl.net/changelog/$PACKAGE_CHANNEL.html#HMCL-$PACKAGE_VERSION
Documentations:
- DocumentLabel: Documentation
  DocumentUrl: https://docs.hmcl.net
ManifestType: defaultLocale
ManifestVersion: 1.10.0
EOF

echo "$PACKAGE_IDENTIFIER.locale.en-US.yaml"
cat $PACKAGE_IDENTIFIER/$PACKAGE_IDENTIFIER.locale.en-US.yaml

cat > $PACKAGE_IDENTIFIER/$PACKAGE_IDENTIFIER.installer.yaml << EOF
PackageIdentifier: $PACKAGE_IDENTIFIER
PackageVersion: $PACKAGE_VERSION
InstallerType: portable
Commands:
- hmcl-$PACKAGE_CHANNEL
Installers:
- Architecture: neutral
  InstallerUrl: $PACKAGE_INSTALLER_URL
  InstallerSha256: $PACKAGE_INSTALLER_SHA256
ManifestType: installer
ManifestVersion: 1.10.0
EOF

echo "$PACKAGE_IDENTIFIER.installer.yaml"
cat $PACKAGE_IDENTIFIER/$PACKAGE_IDENTIFIER.installer.yaml

komac submit --token $KOMAC_TOKEN $PACKAGE_IDENTIFIER

rm -rf $PACKAGE_IDENTIFIER
