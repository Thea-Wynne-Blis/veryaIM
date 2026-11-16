# 把 DTO record 头拆成「一字段一行」，并为每个字段补 @Schema(description)
$ErrorActionPreference = 'Stop'
$root = 'G:\web-ai-code\veryaI-im\veryaI-backend'
$dtoDir = Join-Path $root 'veryal-access\src\main\java\com\veryal\access\http\dto'

. (Join-Path $root 'tools\schema-dict.ps1')

$indent = ' ' * 33
$total = 0
$files = 0

foreach ($file in Get-ChildItem $dtoDir -Filter *.java) {
    $text = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    if ($text -notmatch '(?s)public record (\w+)\s*\(') { continue }
    $recordName = $matches[1]

    # 定位 record 头的括号区间
    $startIdx = $text.IndexOf("public record $recordName")
    $openIdx = $text.IndexOf('(', $startIdx)
    $depth = 0
    $closeIdx = -1
    for ($i = $openIdx; $i -lt $text.Length; $i++) {
        $ch = $text[$i]
        if ($ch -eq '(') { $depth++ }
        elseif ($ch -eq ')') { $depth--; if ($depth -eq 0) { $closeIdx = $i; break } }
    }
    if ($closeIdx -lt 0) { Write-Host "跳过 $($file.BaseName)：括号未闭合" -ForegroundColor Yellow; continue }

    $header = $text.Substring($openIdx + 1, $closeIdx - $openIdx - 1)

    # 顶层逗号切分
    $parts = New-Object System.Collections.Generic.List[string]
    $d = 0; $buf = ''
    foreach ($ch in $header.ToCharArray()) {
        if ($ch -eq '(' -or $ch -eq '<' -or $ch -eq '[') { $d++ }
        elseif ($ch -eq ')' -or $ch -eq '>' -or $ch -eq ']') { $d-- }
        if ($ch -eq ',' -and $d -eq 0) { $parts.Add($buf); $buf = ''; continue }
        $buf += $ch
    }
    if ($buf.Trim()) { $parts.Add($buf) }

    $lines = New-Object System.Collections.Generic.List[string]
    $n = 0
    for ($k = 0; $k -lt $parts.Count; $k++) {
        $comp = ($parts[$k] -replace '\s+', ' ').Trim()
        if (-not $comp) { continue }
        # 最后一个标识符是字段名
        if ($comp -notmatch '([a-z]\w*)$') { Write-Host "  无法解析组件: $comp" -ForegroundColor Yellow; continue }
        $fieldName = $matches[1]
        $hasSchema = $comp -match '@Schema\('
        $desc = if ($dict.ContainsKey($fieldName)) { $dict[$fieldName] } else { $null }
        if ($desc -and -not $hasSchema) {
            $lines.Add("$indent@Schema(description = `"$desc`")")
            $n++
        }
        $suffix = if ($k -lt $parts.Count - 1) { ',' } else { '' }
        $lines.Add("$indent$comp$suffix")
    }

    if ($n -eq 0) { continue }

    $newHeader = "public record $recordName(`n" + ($lines -join "`n") + "`n)"
    $rebuilt = $text.Substring(0, $startIdx) + $newHeader + $text.Substring($closeIdx + 1)

    if ($rebuilt -notmatch 'import io\.swagger\.v3\.oas\.annotations\.media\.Schema;') {
        $rebuilt = $rebuilt -replace '(?m)^(import )', "import io.swagger.v3.oas.annotations.media.Schema;`n`$1" -replace "import io.swagger.v3.oas.annotations.media.Schema;`nimport io.swagger.v3.oas.annotations.media.Schema;", 'import io.swagger.v3.oas.annotations.media.Schema;'
    }

    [System.IO.File]::WriteAllText($file.FullName, $rebuilt, (New-Object System.Text.UTF8Encoding($false)))
    $files++
    $total += $n
    Write-Host "$($file.BaseName) -> $n 个 @Schema" -ForegroundColor Green
}

Write-Host "共 $files 个 DTO，注入 $total 个 @Schema"
