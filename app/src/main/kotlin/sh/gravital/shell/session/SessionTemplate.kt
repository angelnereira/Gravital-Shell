package sh.gravital.shell.session

enum class SessionTemplate(val label: String, val bootstrapScript: String?) {
    Base("Base Ubuntu", null),

    DevToolkit(
        "Dev Toolkit",
        """#!/bin/bash
set -e
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y --no-install-recommends \
    git curl wget vim nano bash build-essential \
    gcc g++ make cmake python3 python3-pip openssh-client
echo "Dev toolkit ready."
""",
    ),

    ClaudeCode(
        "Claude Code",
        """#!/bin/bash
set -e
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y --no-install-recommends curl git nodejs npm
npm install -g @anthropic-ai/claude-code 2>/dev/null || true
echo "Claude Code CLI installed. Run: claude"
""",
    ),

    GeminiCli(
        "Gemini CLI",
        """#!/bin/bash
set -e
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y --no-install-recommends curl git nodejs npm
npm install -g @google/gemini-cli 2>/dev/null || true
echo "Gemini CLI installed. Run: gemini"
""",
    ),
}
