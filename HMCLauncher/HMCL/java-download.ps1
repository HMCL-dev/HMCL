param(
    [string]$JavaDir,
    [string]$Arch
)

$chinese = [System.Globalization.CultureInfo]::CurrentCulture.Name -eq 'zh-CN'

[Reflection.Assembly]::LoadWithPartialName("System.Windows.Forms")
[System.Windows.Forms.Application]::EnableVisualStyles()

# Choose Source Dialog

$dialog = New-Object System.Windows.Forms.Form
$dialog.AutoSize = $true
$dialog.AutoSizeMode = [System.Windows.Forms.AutoSizeMode]::GrowAndShrink
if ($chinese) {
    $dialog.Text = '未能在这台电脑上找到 Java'
} else {
    $dialog.Text = 'Java not found'
}

$dialogLayout = New-Object System.Windows.Forms.FlowLayoutPanel
$dialogLayout.AutoSize = $true
$dialogLayout.FlowDirection = [System.Windows.Forms.FlowDirection]::TopDown
$dialogLayout.AutoSizeMode = [System.Windows.Forms.AutoSizeMode]::GrowAndShrink

$messageLabel = New-Object System.Windows.Forms.Label
$messageLabel.AutoSize = $true
$messageLabel.Anchor = [System.Windows.Forms.AnchorStyles]::Bottom
if ($chinese) {
    $messageLabel.Text = "HMCL 需要 Java 运行时环境才能正常运行，是否自动下载安装 Java？"
} else {
    $messageLabel.Text = "Running HMCL requires a Java runtime environment. `nDo you want to download and install Java automatically?"
}

$useMirrorCheckBox = New-Object System.Windows.Forms.CheckBox
$useMirrorCheckBox.AutoSize = $true
$useMirrorCheckBox.Anchor = [System.Windows.Forms.AnchorStyles]::Right
$useMirrorCheckBox.Checked = $false
if ($chinese) {
    $useMirrorCheckBox.Text = '启用中国大陆下载加速（无法正常下载时尝试这个选项）'
} else {
    $useMirrorCheckBox.Text = 'Enable download acceleration for Chinese mainland'
}

$selectButtonPanel = New-Object System.Windows.Forms.FlowLayoutPanel
$selectButtonPanel.AutoSize = $true
$selectButtonPanel.Anchor = [System.Windows.Forms.AnchorStyles]::Right
$selectButtonPanel.FlowDirection = [System.Windows.Forms.FlowDirection]::LeftToRight
$selectButtonPanel.AutoSizeMode = [System.Windows.Forms.AutoSizeMode]::GrowAndShrink

$yesButton = New-Object System.Windows.Forms.Button
$noButton  = New-Object System.Windows.Forms.Button
$yesButton.DialogResult = [System.Windows.Forms.DialogResult]::Yes
$noButton.DialogResult  = [System.Windows.Forms.DialogResult]::No

if ($chinese) {
    $yesButton.Text = '是'
    $noButton.Text  = '否'
} else {
    $yesButton.Text = 'Yes'
    $noButton.Text  = 'No'
}
$selectButtonPanel.Controls.Add($yesButton)
$selectButtonPanel.Controls.Add($noButton)

$dialogLayout.Controls.Add($messageLabel)
$dialogLayout.Controls.Add($useMirrorCheckBox)
$dialogLayout.Controls.Add($selectButtonPanel)

$dialog.Controls.Add($dialogLayout)

$result = $dialog.ShowDialog()

if ($result -ne [System.Windows.Forms.DialogResult]::Yes) {
    exit 0
}

if ($useMirrorCheckBox.Checked) {
  switch ($Arch) {
      'x86-64' {
          $script:url = 'https://mirrors.tuna.tsinghua.edu.cn/AdoptOpenJDK/17/jre/x64/windows/OpenJDK17U-jre_x64_windows_hotspot_17.0.2_8.zip'
      }
      'x86' {
          $script:url = 'https://mirrors.tuna.tsinghua.edu.cn/AdoptOpenJDK/17/jre/x32/windows/OpenJDK17U-jre_x86-32_windows_hotspot_17.0.1_12.zip'
      }
      default { exit 1 }
  }
} else {
    switch ($Arch) {
        'x86-64' {
            $script:url = 'https://cdn.azul.com/zulu/bin/zulu17.32.13-ca-fx-jre17.0.2-win_x64.zip'
        }
        'x86' {
            $script:url = 'https://cdn.azul.com/zulu/bin/zulu17.32.13-ca-fx-jre17.0.2-win_i686.zip'
        }
        default { exit 1 }
    }
}

$securityProtocol = [System.Net.ServicePointManager]::SecurityProtocol.value__
if (($securityProtocol -ne 0) -and (($securityProtocol -band 0x00000C00) -eq 0)) { # Try using HTTP when the platform does not support TLS 1.2
    $script:url = $script:url -replace '^https:', 'http:'
}

# Download Winodw

do {
    $tempFileName = "hmcl-java-$(Get-Random).zip"
    $script:tempFile = Join-Path ([System.IO.Path]::GetTempPath()) $tempFileName
} while (Test-Path $script:tempFile)

$form = New-Object System.Windows.Forms.Form
$form.AutoSize = $true
$form.AutoSizeMode = [System.Windows.Forms.AutoSizeMode]::GrowAndShrink
if ($chinese) {
    $form.Text = '正在下载 Java 中'
} else {
    $form.Text = 'Downloading Java'
}

$tip = New-Object System.Windows.Forms.Label
$tip.AutoSize = $true
if ($chinese) {
    $tip.Text = '正在下载 Java。这需要一段时间，请耐心等待。'
} else {
    $tip.Text = 'Downloading Java. Please wait patiently for the download to complete.'
}

$layout = New-Object System.Windows.Forms.FlowLayoutPanel
$layout.AutoSize = $true
$layout.FlowDirection = [System.Windows.Forms.FlowDirection]::TopDown
$layout.AutoSizeMode = [System.Windows.Forms.AutoSizeMode]::GrowAndShrink

$progressBar = New-Object System.Windows.Forms.ProgressBar
$progressBar.Maximum = 100

$label = New-Object System.Windows.Forms.Label
$label.Anchor = [System.Windows.Forms.AnchorStyles]::Bottom

if ($chinese) {
    $label.Text = '准备下载'
} else {
    $label.Text = 'In preparation'
}

$box = New-Object System.Windows.Forms.FlowLayoutPanel
$box.AutoSize = $true
$box.FlowDirection = [System.Windows.Forms.FlowDirection]::LeftToRight
$box.AutoSizeMode = [System.Windows.Forms.AutoSizeMode]::GrowAndShrink
$box.Controls.Add($progressBar)
$box.Controls.Add($label)

$cancelButton = New-Object System.Windows.Forms.Button
$cancelButton.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
$cancelButton.Anchor = [System.Windows.Forms.AnchorStyles]::Right
if ($chinese) {
    $cancelButton.Text = '取消'
} else {
    $cancelButton.Text = 'Cancel'
}

$layout.Controls.Add($tip)
$layout.Controls.Add($box)
$box.Controls.Add($cancelButton)

$form.Controls.Add($layout)

[System.Net.DownloadProgressChangedEventHandler]$progressChangedEventHandler = {
    param($sender, [System.Net.DownloadProgressChangedEventArgs]$ChangedEventArgs)
    $bytesReceived = $ChangedEventArgs.BytesReceived
    $totalBytes = $ChangedEventArgs.TotalBytesToReceive

    $percentage = ([double]$bytesReceived)/([double]$totalBytes) * 100

    $progressBar.Value = [int][System.Math]::Truncate($percentage)
    $label.Text = [string]::Format("{0:0.00}%", $percentage)
}

[System.ComponentModel.AsyncCompletedEventHandler]$downloadFileCompletedEventHandler = {
    param($sender, [System.ComponentModel.AsyncCompletedEventArgs]$CompletedEventArgs)
    if (!$form.IsDisposed) {
        $label.Refresh()
        if ($CompletedEventArgs.Cancelled) {
            $form.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
        } elseif ($CompletedEventArgs.Error -ne $null) {
            if ($chinese) {
                [System.Windows.Forms.MessageBox]::Show($CompletedEventArgs.Error.Message, '下载失败', [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Exclamation)
            } else {
                [System.Windows.Forms.MessageBox]::Show($CompletedEventArgs.Error.Message, 'Download failed', [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Exclamation)
            }
            $form.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
        } else {
            $form.DialogResult = [System.Windows.Forms.DialogResult]::OK
        }
    }
}

$client = New-Object System.Net.WebClient
$client.Headers.Add('User-Agent', 'Wget/1.20.3 (linux-gnu)')
$client.add_DownloadProgressChanged($progressChangedEventHandler)
$client.add_DownloadFileCompleted($downloadFileCompletedEventHandler)

$client.DownloadFileAsync($script:url, $script:tempFile)

$result = $form.ShowDialog()
$client.CancelAsync()
$form.Dispose()

if ($result -eq [System.Windows.Forms.DialogResult]::OK) {
    $null = New-Item -Type Directory -Force $JavaDir
    $app = New-Object -ComObject Shell.Application
    $items = $app.NameSpace($script:tempFile).items()
    foreach ($item in $items) {
        $app.NameSpace($JavaDir).copyHere($item)
    }
}

if ([System.IO.File]::Exists($script:tempFile)) {
    try {
        [System.IO.File]::Delete($script:tempFile)
    } catch {
        Write-Error $_
    }
}