#!/usr/bin/env bash

sed -i 's,//services.gradle.org/distributions/,//mirrors.cloud.tencent.com/gradle/,g' gradle/wrapper/gradle-wrapper.properties
