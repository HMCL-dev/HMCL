#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
#

##############################################################################
#
# Gradle Wrapper Neo startup script for Windows.
#
# This file is maintained by the Gradle Wrapper Neo project. It compiles
# gradle/wrapper/GradleWrapperNeo.java on demand and launches the Wrapper
# without storing a Wrapper JAR in the project.
#
# Documentation and updates: https://github.com/Glavo/gradle-wrapper-neo
#
##############################################################################

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$script:WrapperExitCode = 1

$script:AppBaseName = [IO.Path]::GetFileNameWithoutExtension($MyInvocation.MyCommand.Name)
$script:LauncherHome = [IO.Path]::GetFullPath($PSScriptRoot)

function ConvertFrom-WindowsCommandLine {
    param([string] $CommandLine)

    if ($null -eq ('GradleWrapperNeo.NativeCommandLine' -as [type])) {
        Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

namespace GradleWrapperNeo {
    public static class NativeCommandLine {
        [DllImport("shell32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
        public static extern IntPtr CommandLineToArgvW(string commandLine, out int argumentCount);

        [DllImport("kernel32.dll")]
        public static extern IntPtr LocalFree(IntPtr memory);
    }
}
"@
    }

    $nativeCommandLine = 'gradlew.exe ' + $CommandLine
    $argumentCount = 0
    $argumentVector = [GradleWrapperNeo.NativeCommandLine]::CommandLineToArgvW(
        $nativeCommandLine,
        [ref] $argumentCount
    )
    if ($argumentVector -eq [IntPtr]::Zero) {
        throw [ComponentModel.Win32Exception]::new([Runtime.InteropServices.Marshal]::GetLastWin32Error())
    }

    try {
        $result = New-Object 'System.Collections.Generic.List[string]'
        for ($index = 1; $index -lt $argumentCount; $index++) {
            $argumentPointer = [Runtime.InteropServices.Marshal]::ReadIntPtr(
                $argumentVector,
                $index * [IntPtr]::Size
            )
            [void] $result.Add([Runtime.InteropServices.Marshal]::PtrToStringUni($argumentPointer))
        }
        return $result.ToArray()
    } finally {
        [void] [GradleWrapperNeo.NativeCommandLine]::LocalFree($argumentVector)
    }
}

function Split-ArgumentString {
    param([string] $Value)

    if ($null -eq $Value) {
        return @()
    }

    $result = New-Object 'System.Collections.Generic.List[string]'
    $currentOption = New-Object Text.StringBuilder
    [char] $currentQuote = [char] 0
    $insideQuote = $false
    $hasOption = $false

    for ($index = 0; $index -lt $Value.Length; $index++) {
        [char] $character = $Value[$index]
        if ((-not $insideQuote) -and [char]::IsWhiteSpace($character)) {
            if ($hasOption) {
                [void] $result.Add($currentOption.ToString())
                [void] $currentOption.Clear()
                $hasOption = $false
            }
        } elseif ((-not $insideQuote) -and (($character -eq [char] 34) -or ($character -eq [char] 39))) {
            $currentQuote = $character
            $insideQuote = $true
            $hasOption = $true
        } elseif ($insideQuote -and ($character -eq $currentQuote)) {
            $insideQuote = $false
        } else {
            [void] $currentOption.Append($character)
            $hasOption = $true
        }
    }

    if ($hasOption) {
        [void] $result.Add($currentOption.ToString())
    }

    return $result.ToArray()
}

function Resolve-JavaExecutable {
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $javaHome = $env:JAVA_HOME.Trim([char] 34)
        $javaExecutable = Join-Path $javaHome 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
            throw "JAVA_HOME is set to an invalid directory: $javaHome"
        }
        return [IO.Path]::GetFullPath($javaExecutable)
    }

    $javaCommand = Get-Command -Name 'java.exe' -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $javaCommand) {
        throw "JAVA_HOME is not set and no 'java' command could be found in PATH."
    }
    return $javaCommand.Path
}

function Resolve-JavacExecutable {
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $javaHome = $env:JAVA_HOME.Trim([char] 34)
        $javacExecutable = Join-Path $javaHome 'bin\javac.exe'
        if (Test-Path -LiteralPath $javacExecutable -PathType Leaf) {
            return [IO.Path]::GetFullPath($javacExecutable)
        }
    } else {
        $javacCommand = Get-Command -Name 'javac.exe' -CommandType Application -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($null -ne $javacCommand) {
            return $javacCommand.Path
        }
    }

    throw 'GradleWrapperNeo.java exists, but javac could not be found. Run this wrapper with a JDK so the source can be compiled.'
}

function Find-AppHome {
    param(
        [string] $LauncherHome,
        [string] $WorkingDirectory
    )

    $launcherProperties = Join-Path $LauncherHome 'gradle\wrapper\gradle-wrapper.properties'
    if (Test-Path -LiteralPath $launcherProperties -PathType Leaf) {
        return $LauncherHome
    }

    $searchDirectory = Get-Item -LiteralPath $WorkingDirectory
    $searchStart = $searchDirectory.FullName
    while ($null -ne $searchDirectory) {
        $propertiesFile = Join-Path $searchDirectory.FullName 'gradle\wrapper\gradle-wrapper.properties'
        if (Test-Path -LiteralPath $propertiesFile -PathType Leaf) {
            return $searchDirectory.FullName
        }
        $searchDirectory = $searchDirectory.Parent
    }

    throw "Could not find gradle/wrapper/gradle-wrapper.properties searching from '$searchStart'."
}

function ConvertTo-JavaPath {
    param([string] $Path)

    return $Path.Replace('\', '/')
}

function ConvertTo-WindowsCommandLineArgument {
    param([AllowEmptyString()][string] $Argument)

    if (($Argument.Length -gt 0) -and ($Argument -notmatch '[\s"]')) {
        return $Argument
    }

    $result = New-Object Text.StringBuilder
    [void] $result.Append([char] 34)
    $backslashCount = 0
    for ($index = 0; $index -lt $Argument.Length; $index++) {
        [char] $character = $Argument[$index]
        if ($character -eq [char] 92) {
            $backslashCount++
            continue
        }

        if ($character -eq [char] 34) {
            [void] $result.Append(([string] [char] 92) * (($backslashCount * 2) + 1))
        } elseif ($backslashCount -gt 0) {
            [void] $result.Append(([string] [char] 92) * $backslashCount)
        }
        $backslashCount = 0
        [void] $result.Append($character)
    }

    if ($backslashCount -gt 0) {
        [void] $result.Append(([string] [char] 92) * ($backslashCount * 2))
    }
    [void] $result.Append([char] 34)
    return $result.ToString()
}

function Invoke-NativeApplication {
    param(
        [string] $Executable,
        [string[]] $Arguments,
        [string] $WorkingDirectory
    )

    $quotedArguments = New-Object 'System.Collections.Generic.List[string]'
    foreach ($argument in $Arguments) {
        [void] $quotedArguments.Add((ConvertTo-WindowsCommandLineArgument -Argument $argument))
    }

    $startInfo = New-Object Diagnostics.ProcessStartInfo
    $startInfo.FileName = $Executable
    $startInfo.Arguments = [string]::Join(' ', $quotedArguments.ToArray())
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false

    $process = New-Object Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) {
            throw "Could not start '$Executable'."
        }
        $process.WaitForExit()
        return $process.ExitCode
    } finally {
        $process.Dispose()
    }
}

function Compile-WrapperSource {
    param(
        [string] $SourceFile,
        [string] $ClassesDirectory,
        [string] $BootstrapDirectory
    )

    $javacExecutable = Resolve-JavacExecutable
    [void] [IO.Directory]::CreateDirectory($ClassesDirectory)

    $versionOutput = & $javacExecutable -version 2>&1
    $versionExitCode = $LASTEXITCODE
    if ($versionExitCode -ne 0) {
        Remove-Item -LiteralPath $BootstrapDirectory -Recurse -Force -ErrorAction SilentlyContinue
        throw "Could not run javac at '$javacExecutable'."
    }
    $versionText = ($versionOutput | Out-String).Trim()

    if ($versionText.StartsWith('javac 1.')) {
        [string[]] $targetArguments = @('-source', '8', '-target', '8')
    } else {
        [string[]] $targetArguments = @('--release', '8')
    }

    [string[]] $compileArguments = @(
        $targetArguments
        '-Xlint:-options'
        '-encoding'
        'UTF-8'
        '-d'
        $ClassesDirectory
        $SourceFile
    )

    & $javacExecutable @compileArguments
    $compileExitCode = $LASTEXITCODE
    if ($compileExitCode -ne 0) {
        Remove-Item -LiteralPath $BootstrapDirectory -Recurse -Force -ErrorAction SilentlyContinue
        throw "Could not compile '$SourceFile'."
    }
}

function Invoke-GradleWrapper {
    param([string[]] $GradleArguments)

    $currentLocation = Get-Location
    if ($currentLocation.Provider.Name -ne 'FileSystem') {
        throw "The current location is not a file system directory: $currentLocation"
    }
    $workingDirectory = $currentLocation.ProviderPath

    $appHome = Find-AppHome -LauncherHome $script:LauncherHome -WorkingDirectory $workingDirectory
    $launcherWrapperDirectory = Join-Path $script:LauncherHome 'gradle\wrapper'
    $projectSource = Join-Path $appHome 'gradle\wrapper\GradleWrapperNeo.java'
    if (Test-Path -LiteralPath $projectSource -PathType Leaf) {
        $sourceFile = $projectSource
    } else {
        $sourceFile = Join-Path $launcherWrapperDirectory 'GradleWrapperNeo.java'
    }
    if (-not (Test-Path -LiteralPath $sourceFile -PathType Leaf)) {
        throw "Wrapper source file '$sourceFile' was not found."
    }

    $workDirectory = Join-Path $appHome '.gradle\wrapper-neo'
    $jarFile = Join-Path $workDirectory 'gradle-wrapper-neo.jar'
    $bootstrapDirectory = Join-Path (Join-Path $workDirectory 'bootstrap') ([Guid]::NewGuid().ToString('N'))
    $classesDirectory = Join-Path $bootstrapDirectory 'classes'

    $javaExecutable = Resolve-JavaExecutable
    [string[]] $jvmArguments = @('-Xmx64m', '-Xms64m')
    foreach ($optionSource in @($env:JAVA_OPTS, $env:GRADLE_OPTS)) {
        foreach ($option in @(Split-ArgumentString -Value $optionSource)) {
            $jvmArguments += $option
        }
    }

    $javaAppHome = ConvertTo-JavaPath -Path $appHome
    $javaSourceFile = ConvertTo-JavaPath -Path $sourceFile
    $javaJarFile = ConvertTo-JavaPath -Path $jarFile
    $javaClassesDirectory = ConvertTo-JavaPath -Path $classesDirectory

    [string[]] $javaArguments = @(
        $jvmArguments
        "-Dorg.gradle.appname=$script:AppBaseName"
        "-Dorg.gradle.wrapper.neo.app-home=$javaAppHome"
        "-Dorg.gradle.wrapper.neo.source-file=$javaSourceFile"
        "-Dorg.gradle.wrapper.neo.jar-file=$javaJarFile"
    )

    $cachedJar = Get-Item -LiteralPath $jarFile -ErrorAction SilentlyContinue
    if (($null -ne $cachedJar) -and ($cachedJar.Length -eq 0)) {
        Remove-Item -LiteralPath $jarFile -Force -ErrorAction SilentlyContinue
    }

    if (Test-Path -LiteralPath $jarFile -PathType Leaf) {
        $javaArguments += @('-jar', $javaJarFile)
    } else {
        Compile-WrapperSource -SourceFile $sourceFile -ClassesDirectory $classesDirectory -BootstrapDirectory $bootstrapDirectory
        $javaArguments += @(
            '-Dgradle.wrapper.neo.bootstrap=true'
            '-cp'
            $javaClassesDirectory
            'GradleWrapperNeo'
        )
    }

    $javaArguments += $GradleArguments
    $script:WrapperExitCode = Invoke-NativeApplication `
        -Executable $javaExecutable `
        -Arguments $javaArguments `
        -WorkingDirectory $workingDirectory
}

try {
    if ($env:GRADLE_WRAPPER_NEO_BATCH_LAUNCH -eq '1') {
        $rawArguments = $env:GRADLE_WRAPPER_NEO_BATCH_ARGUMENTS
        Remove-Item Env:GRADLE_WRAPPER_NEO_BATCH_LAUNCH -ErrorAction SilentlyContinue
        Remove-Item Env:GRADLE_WRAPPER_NEO_BATCH_ARGUMENTS -ErrorAction SilentlyContinue
        [string[]] $gradleArguments = @(ConvertFrom-WindowsCommandLine -CommandLine $rawArguments)
    } else {
        [string[]] $gradleArguments = @($args)
    }
    Invoke-GradleWrapper -GradleArguments $gradleArguments
    exit $script:WrapperExitCode
} catch {
    [Console]::Error.WriteLine()
    [Console]::Error.WriteLine('ERROR: ' + $_.Exception.Message)
    [Console]::Error.WriteLine()
    exit 1
}
