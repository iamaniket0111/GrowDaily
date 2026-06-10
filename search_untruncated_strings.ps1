$logPath = "C:\Users\anike\.gemini\antigravity\brain\6acc173c-cb80-4f75-bb69-22fce94600d2\.system_generated\logs\transcript.jsonl"
$output = Get-Content -Path $logPath -Encoding utf8
foreach ($line in $output) {
    if ($line -like "*battery_optimization_content_description*") {
        try {
            $json = ConvertFrom-Json $line
            # check content
            if ($json.content -like "*battery_optimization_content_description*" -and $json.content -notlike "*truncated*") {
                if ($json.step_index -ne 3234) {
                    Write-Host "Step: $($json.step_index) (content)"
                    Write-Host $json.content
                }
            }
            if ($json.tool_calls) {
                foreach ($call in $json.tool_calls) {
                    $args = $call.args
                    if ($args.ReplacementContent -like "*battery_optimization_content_description*") {
                        if ($args.ReplacementContent -notlike "*truncated*") {
                            Write-Host "Step: $($json.step_index) (tool: $($call.name))"
                            Write-Host $args.ReplacementContent
                        }
                    }
                }
            }
        } catch {}
    }
}
