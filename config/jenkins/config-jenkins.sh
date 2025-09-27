#!/usr/bin/env bash

_HMCL_PROJECT_ROOT=$(dirname "${BASH_SOURCE[0]}")

sed -i 's,//services.gradle.org/distributions/,//mirrors.cloud.tencent.com/gradle/,g' "$_HMCL_PROJECT_ROOT/gradle/wrapper/gradle-wrapper.properties"
