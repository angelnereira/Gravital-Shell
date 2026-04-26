package sh.gravital.shell.session

enum class SessionTemplate(val label: String, val bootstrapScript: String?) {
    Base("Base Alpine", null),

    DevToolkit(
        "Dev Toolkit",
        """#!/bin/sh
set -e
apk update
apk add --no-cache git curl wget vim nano bash gcc musl-dev make
echo "Dev toolkit ready."
""",
    ),

    ClaudeCode(
        "Claude Code",
        """#!/bin/sh
set -e
apk update
apk add --no-cache curl bash nodejs npm git
npm install -g @anthropic-ai/claude-code 2>/dev/null || true
echo "Claude Code CLI installed. Run: claude"
""",
    ),

    GeminiCli(
        "Gemini CLI",
        """#!/bin/sh
set -e
apk update
apk add --no-cache curl bash nodejs npm git
npm install -g @google/gemini-cli 2>/dev/null || true
echo "Gemini CLI installed. Run: gemini"
""",
    ),
}
