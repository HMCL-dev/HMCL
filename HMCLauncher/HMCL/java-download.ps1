param(
    [string]$JavaDir
)

$url = 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip'
$chinese = (Get-WinSystemLocale).Name -eq 'zh-CN'

[Reflection.Assembly]::LoadWithPartialName("System.Windows.Forms")

$result = if ($chinese) {
    [System.Windows.Forms.MessageBox]::Show('HMCL 需要 Java 运行时环境才能正常运行，是否自动下载安装 Java？', 'HMCL', [System.Windows.Forms.MessageBoxButtons]::YesNo)
} else {
    [System.Windows.Forms.MessageBox]::Show('Running HMCL requires a Java runtime environment. Do you want to download and install Java automatically?', 'HMCL', [System.Windows.Forms.MessageBoxButtons]::YesNo)
}

if ($result -ne [System.Windows.Forms.DialogResult]::Yes) {
    exit 0
}

do {
    $tempFileName = "hmcl-java-$(Get-Random).zip"
    $script:tempFile = Join-Path ([System.IO.Path]::GetTempPath()) $tempFileName
} while (Test-Path $script:tempFile)

$form = New-Object System.Windows.Forms.Form
$form.AutoSize = $true
$form.AutoSizeMode = [System.Windows.Forms.AutoSizeMode]::GrowAndShrink
if ($chinese) {
    $form.Text = '正在下载 Java。这需要一段时间，请耐心等待。'
} else {
    $form.Text = 'Downloading Java. Please wait patiently for the download to complete.'
}

$tip = New-Object System.Windows.Forms.Label
if ($chinese) {
    $tip.Text = '正在下载 Java 中'
} else {
    $tip.Text = 'Downloading Java'
}

$layout = New-Object System.Windows.Forms.FlowLayoutPanel
$layout.AutoSize = $true
$layout.FlowDirection = [System.Windows.Forms.FlowDirection]::TopDown
$layout.AutoSizeMode = [System.Windows.Forms.AutoSizeMode]::GrowAndShrink

$progressBar = New-Object System.Windows.Forms.ProgressBar
$progressBar.Maximum = 100

$label = New-Object System.Windows.Forms.Label
$label.Anchor = [System.Windows.Forms.AnchorStyles]::Bottom

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
        $form.DialogResult = [System.Windows.Forms.DialogResult]::OK
    }
}

$client = New-Object System.Net.WebClient
$client.add_DownloadProgressChanged($progressChangedEventHandler)
$client.add_DownloadFileCompleted($downloadFileCompletedEventHandler)

$client.DownloadFileAsync($url, $script:tempFile)

$result = $form.ShowDialog()
$form.Dispose()

if ($result -eq [System.Windows.Forms.DialogResult]::OK) {
    $null = New-Item -Type Directory -Force $JavaDir
    $app = New-Object -ComObject Shell.Application
    $items = $app.NameSpace($script:tempFile).items()
    foreach ($item in $items) {
        $app.NameSpace($JavaDir).copyHere($item)
    }
}

$client.CancelAsync()
if ([System.IO.File]::Exists($script:tempFile)) {
    try {
        [System.IO.File]::Delete($script:tempFile)
    } catch {
        Write-Error $_
    }
}