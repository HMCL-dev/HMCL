#!/usr/bin/env bash

set -e

# Switch message language
if [ -z "${LANG##zh_*}" ]; then
  _HMCL_USE_CHINESE=true
else
  _HMCL_USE_CHINESE=false
fi

# _HMCL_OS
case "$OSTYPE" in
  linux*)
    _HMCL_OS="linux";;
  darwin*)
    _HMCL_OS="osx";;
  msys*|cygwin*)
    _HMCL_OS="windows";;
  *)
    _HMCL_OS="unknown";;
esac

# Normalize _HMCL_ARCH
case "$(uname -m)" in
  x86_64|x86-64|amd64|em64t|x64)
    _HMCL_ARCH="x86_64";;
  x86_32|x86-32|x86|ia32|i386|i486|i586|i686|i86pc|x32)
    _HMCL_ARCH="x86";;
  arm64|aarch64|armv8*|armv9*)
    _HMCL_ARCH="arm64";;
  arm|arm32|aarch32|armv7*)
    _HMCL_ARCH="arm32";;
  loongarch64)
    _HMCL_ARCH="loongarch64";;
  *)
    _HMCL_ARCH="unknown";;
esac

# Self path
_HMCL_PATH="${BASH_SOURCE[0]}"
_HMCL_DIR=$(dirname "$_HMCL_PATH")

if [ "$_HMCL_OS" == "windows" ]; then
  _HMCL_JAVA_EXE_NAME="java.exe"
else
  _HMCL_JAVA_EXE_NAME="java"
fi

# _HMCL_VM_OPTIONS
if [ -n "${HMCL_JAVA_OPTS+x}" ]; then
  _HMCL_VM_OPTIONS=${HMCL_JAVA_OPTS}
else
  _HMCL_VM_OPTIONS="-XX:MinHeapFreeRatio=5 -XX:MaxHeapFreeRatio=15"
fi

# First, find Java in HMCL_JAVA_HOME
if [ -n "${HMCL_JAVA_HOME+x}" ]; then
  if [ -x "$HMCL_JAVA_HOME/bin/$_HMCL_JAVA_EXE_NAME" ]; then
    "$HMCL_JAVA_HOME/bin/$_HMCL_JAVA_EXE_NAME" $_HMCL_VM_OPTIONS -jar "$_HMCL_PATH"
    exit 0
  else
    if [ "$_HMCL_USE_CHINESE" == true ]; then
      echo "环境变量 HMCL_JAVA_HOME 的值无效，请设置为合法的 Java 路径。" 1>&2
    else
      echo "The value of the environment variable HMCL_JAVA_HOME is invalid, please set it to a valid Java path." 1>&2
    fi
    exit 1
  fi
fi

# Find Java in HMCL_DIR
case "$_HMCL_ARCH" in
  x86_64)
    if [ -x "$_HMCL_DIR/jre-x64/bin/$_HMCL_JAVA_EXE_NAME" ]; then
      "$_HMCL_DIR/jre-x64/bin/$_HMCL_JAVA_EXE_NAME" $_HMCL_VM_OPTIONS -jar "$_HMCL_PATH"
      exit 0
    fi
    if [ -x "$_HMCL_DIR/jre-x86/bin/$_HMCL_JAVA_EXE_NAME" ]; then
      "$_HMCL_DIR/jre-x86/bin/$_HMCL_JAVA_EXE_NAME" $_HMCL_VM_OPTIONS -jar "$_HMCL_PATH"
      exit 0
    fi
    ;;
  x86)
    if [ -x "$_HMCL_DIR/jre-x86/bin/$_HMCL_JAVA_EXE_NAME" ]; then
      "$_HMCL_DIR/jre-x86/bin/$_HMCL_JAVA_EXE_NAME" $_HMCL_VM_OPTIONS -jar "$_HMCL_PATH"
      exit 0
    fi
    ;;
  arm64)
    if [ -x "$_HMCL_DIR/jre-arm64/bin/$_HMCL_JAVA_EXE_NAME" ]; then
      "$_HMCL_DIR/jre-arm64/bin/$_HMCL_JAVA_EXE_NAME" $_HMCL_VM_OPTIONS -jar "$_HMCL_PATH"
      exit 0
    fi
    ;;
  arm32)
    if [ -x "$_HMCL_DIR/jre-arm32/bin/$_HMCL_JAVA_EXE_NAME" ]; then
      "$_HMCL_DIR/jre-arm32/bin/$_HMCL_JAVA_EXE_NAME" $_HMCL_VM_OPTIONS -jar "$_HMCL_PATH"
      exit 0
    fi
    ;;
  loongarch64)
    if [ -x "$_HMCL_DIR/jre-loongarch64/bin/$_HMCL_JAVA_EXE_NAME" ]; then
      "$_HMCL_DIR/jre-loongarch64/bin/$_HMCL_JAVA_EXE_NAME" $_HMCL_VM_OPTIONS -jar "$_HMCL_PATH"
      exit 0
    fi
    ;;
esac

# Find Java in JAVA_HOME
if [ -f "$JAVA_HOME/bin/$_HMCL_JAVA_EXE_NAME" ]; then
  "$JAVA_HOME/bin/$_HMCL_JAVA_EXE_NAME" $_HMCL_VM_OPTIONS -jar "$_HMCL_PATH"
  exit 0
fi

# Find Java in PATH
if [ -x "$(command -v $_HMCL_JAVA_EXE_NAME)" ]; then
  $_HMCL_JAVA_EXE_NAME $_HMCL_VM_OPTIONS -jar "$_HMCL_PATH"
  exit 0
fi

# Java not found

if [[ "$_HMCL_OS" == "unknown" || "$_HMCL_ARCH" == "unknown" ]]; then
  if [ "$_HMCL_USE_CHINESE" == true ]; then
    echo "运行 HMCL 需要 Java 运行时环境，请安装 Java 并设置环境变量后重试。" 1>&2
  else
    echo "The Java runtime environment is required to run HMCL. " 1>&2
    echo "Please install Java and set the environment variables and try again." 1>&2
  fi
  exit 1
fi

if [[ "$_HMCL_ARCH" == "loongarch64" ]]; then
  if [ "$_HMCL_USE_CHINESE" == true ]; then
    echo "运行 HMCL 需要 Java 运行时环境，请安装龙芯 JDK8 (https://docs.hmcl.net/downloads/loongnix.html) 并设置环境变量后重试。" 1>&2
  else
    echo "The Java runtime environment is required to run HMCL." 1>&2
    echo "Please install Loongson JDK8 (https://docs.hmcl.net/downloads/loongnix.html) and set the environment variables, then try again." 1>&2
  fi
  exit 1
fi


case "$_HMCL_OS" in
  linux)
    _HMCL_DOWNLOAD_PAGE_OS="linux";;
  osx)
    _HMCL_DOWNLOAD_PAGE_OS="macos";;
  windows)
    _HMCL_DOWNLOAD_PAGE_OS="windows";;
  *)
    echo "Unknown os: $_HMCL_OS" 1>&2
    exit 1
    ;;
esac

case "$_HMCL_ARCH" in
  arm64)
    _HMCL_DOWNLOAD_PAGE_ARCH="arm64";;
  arm32)
    _HMCL_DOWNLOAD_PAGE_ARCH="arm32";;
  x86_64)
    _HMCL_DOWNLOAD_PAGE_ARCH="x86_64";;
  x86)
    _HMCL_DOWNLOAD_PAGE_ARCH="x86";;
  *)
    echo "Unknown architecture: $_HMCL_ARCH" 1>&2
    exit 1
    ;;
esac

_HMCL_DOWNLOAD_PAGE="https://docs.hmcl.net/downloads/$_HMCL_DOWNLOAD_PAGE_OS/$_HMCL_DOWNLOAD_PAGE_ARCH.html"

if [ "$_HMCL_USE_CHINESE" == true ]; then
  echo "运行 HMCL 需要 Java 运行时环境，请安装 Java 并设置环境变量后重试。" 1>&2
  echo "$_HMCL_DOWNLOAD_PAGE" 1>&2
else
  echo "The Java runtime environment is required to run HMCL. " 1>&2
  echo "Please install Java and set the environment variables and try again." 1>&2
  echo "$_HMCL_DOWNLOAD_PAGE" 1>&2
fi
exit 1
