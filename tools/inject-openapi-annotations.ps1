# 把 OpenApiConfig 里集中维护的说明表，转成 Controller 上的 @Tag / @Operation 注解
$ErrorActionPreference = 'Stop'
$root = 'G:\web-ai-code\veryaI-im\veryaI-backend'
$ctlDir = Join-Path $root 'veryal-access\src\main\java\com\veryal\access\http\controller'
$cfg = Join-Path $root 'veryal-access\src\main\java\com\veryal\access\http\docs\OpenApiConfig.java'

# ---------- 1. 解析 DOCS 表（支持跨行） ----------
$all = [System.IO.File]::ReadAllText($cfg, [System.Text.Encoding]::UTF8)
$pattern = '(?s)doc\(\s*"(GET|POST|PUT|DELETE|PATCH)",\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)",\s*"(.*?)"\s*\)'
$docs = @{}
foreach ($m in [regex]::Matches($all, $pattern)) {
    $docs["$($m.Groups[1].Value) $($m.Groups[2].Value)"] = @{
        tag     = $m.Groups[3].Value
        summary = $m.Groups[4].Value
        desc    = ($m.Groups[5].Value -replace '\s+', ' ').Trim()
    }
}
Write-Host "解析到 $($docs.Count) 条接口说明"

# ---------- 2. Controller -> 标签 ----------
$tagOf = @{
    'AuthController' = '认证'; 'UserController' = '用户'; 'UserSocialController' = '用户'
    'FriendController' = '好友'; 'BlockController' = '黑名单'; 'ContactController' = '通讯录'
    'ConversationController' = '会话'; 'SyncController' = '消息同步'
    'GroupController' = '群聊'; 'GroupModerationController' = '群聊'
    'FileController' = '文件'; 'ReportController' = '举报'
    'PushDeviceController' = '推送'; 'WebPushController' = '推送'
    'PlatformAuthController' = '运营认证'; 'PlatformAdminController' = '运营-管理员'
    'OpsUserController' = '运营-用户'; 'OpsGroupController' = '运营-群组'
    'OpsReportController' = '运营-举报'; 'OpsSensitiveWordController' = '运营-敏感词'
    'OpsDashboardController' = '运营-概览'; 'PlatformAuditController' = '运营-审计'
}

function Esc([string]$s) {
    if ($null -eq $s) { return '' }
    $s.Replace('\\', '\\\\').Replace('"', '\"')
}

$totalOps = 0
$unmatched = @()
foreach ($file in Get-ChildItem $ctlDir -Filter *.java) {
    $name = $file.BaseName
    $text = Get-Content $file.FullName -Encoding UTF8
    $base = ''
    if (($text -join "`n") -match '@RequestMapping\("([^"]+)"\)') { $base = $matches[1] }

    $out = New-Object System.Collections.Generic.List[string]
    $inserted = 0

    for ($i = 0; $i -lt $text.Count; $i++) {
        $line = $text[$i]
        if ($line -match '^\s*@(Get|Post|Put|Delete|Patch)Mapping(?:\("([^"]*)"\))?') {
            $verb = $matches[1].ToUpper()
            $sub = $matches[2]
            $key = "$verb $base$sub"
            if ($docs.ContainsKey($key)) {
                $d = $docs[$key]
                $indent = [regex]::Match($line, '^\s*').Value
                $out.Add("$indent@Operation(summary = `"$(Esc $d.summary)`", description = `"$(Esc $d.desc)`")")
                $inserted++
            } else {
                $unmatched += "$name $key"
            }
        }
        $out.Add($line)
    }

    if ($inserted -eq 0) { continue }
    $totalOps += $inserted

    $tagName = $tagOf[$name]
    if ($tagName) {
        $idx = -1
        for ($i = 0; $i -lt $out.Count; $i++) {
            if ($out[$i] -match '^public (final )?class ') { $idx = $i; break }
        }
        if ($idx -gt 0) {
            $ins = $idx
            for ($j = $idx - 1; $j -ge 0; $j--) {
                if ($out[$j] -match '^\s*@') { $ins = $j; continue }
                if ($out[$j].Trim() -eq '') { continue }
                break
            }
            $out.Insert($ins, "@Tag(name = `"$tagName`")")
        }
    }

    $merged = $out -join "`n"
    $needs = @()
    if ($merged -match '@Operation\(' -and $merged -notmatch 'import io\.swagger\.v3\.oas\.annotations\.Operation;') {
        $needs += 'import io.swagger.v3.oas.annotations.Operation;'
    }
    if ($merged -match '@Tag\(' -and $merged -notmatch 'import io\.swagger\.v3\.oas\.annotations\.tags\.Tag;') {
        $needs += 'import io.swagger.v3.oas.annotations.tags.Tag;'
    }
    if ($needs.Count -gt 0) {
        $lastImport = -1
        for ($i = 0; $i -lt $out.Count; $i++) { if ($out[$i] -match '^import ') { $lastImport = $i } }
        for ($k = $needs.Count - 1; $k -ge 0; $k--) { $out.Insert($lastImport + 1, $needs[$k]) }
    }

    [System.IO.File]::WriteAllLines($file.FullName, $out, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "$name -> $inserted 个 @Operation" -ForegroundColor Green
}

Write-Host "总计注入 $totalOps 个 @Operation"
if ($unmatched.Count -gt 0) {
    Write-Host "未匹配 $($unmatched.Count) 条:" -ForegroundColor Yellow
    $unmatched | ForEach-Object { Write-Host "  $_" }
}
