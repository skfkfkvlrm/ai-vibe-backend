# System Guidelines & Auto-Discovery Rules (Claude Code Workspace Rules)

Before taking any actions or starting any new task in this workspace, Claude Code MUST strictly execute the following steps in order:

## 1. Check Guidelines & Guardrails
You MUST read the `system_guidelines.md` and `common_guardrails.md` files located at:
- `D:\skfkfkvlrm\skfkfkvlrm-json-lib-main\system_guidelines.md`
- `D:\skfkfkvlrm\skfkfkvlrm-json-lib-main\common_guardrails.md`
You must strictly adhere to the guidelines, phases, and error prevention guardrails defined in those files. Never assume the current phase or skip reading the files.
- **Continuous In-Task Verification Mandate:** Reviewing guidelines and guardrails must not be a one-time check at the start of a task. You MUST frequently re-reference and cross-check them throughout the entire work process.

## 2. Check Runnable Local Models
Check which local Ollama models are currently available:
`Invoke-RestMethod -Uri 'http://localhost:11434/api/tags' -Method Get`

## 3. Mandatory Task Report & GitHub Upload Policy
After completing EVERY task:
1. Write a comprehensive markdown report in `D:\skfkfkvlrm\skfkfkvlrm-json-lib-main\docs\50_report\YYYY-MM-DD/YYYY-MM-DD_<task_name>_report.md`.
2. Commit and push the report to GitHub (`origin/main`) for `skfkfkvlrm-json-lib`.
3. Commit and push any code changes in `demo-boot` to its remote GitHub repository as well.
