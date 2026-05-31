#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/install-deps.sh

Installs the tools needed by Video Oven on macOS:
  - OpenJDK
  - Maven
  - yt-dlp
  - pipx
  - openai-whisper

ffmpeg is not installed by this script. Video Oven uses tool/ffmpeg from this repo.
EOF
}

die() {
  echo "Error: $*" >&2
  echo >&2
  usage >&2
  exit 1
}

info() {
  echo "==> $*"
}

has_command() {
  command -v "$1" >/dev/null 2>&1
}

install_brew_package() {
  local package=$1
  local command_name=${2:-$1}

  if has_command "$command_name"; then
    info "$command_name already installed"
    return
  fi

  info "Installing $package"
  brew install "$package"
}

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(cd "$script_dir/.." && pwd)

if [[ ${1:-} == "-h" || ${1:-} == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -gt 0 ]]; then
  die "unexpected arguments"
fi

if [[ "$(uname -s)" != "Darwin" ]]; then
  die "this installer currently supports macOS with Homebrew"
fi

if ! has_command brew; then
  die "Homebrew is required. Install it first: https://brew.sh/"
fi

if [[ ! -x "$project_dir/tool/ffmpeg" ]]; then
  die "local ffmpeg is missing or not executable: $project_dir/tool/ffmpeg"
fi

install_brew_package openjdk java
install_brew_package maven mvn
install_brew_package yt-dlp yt-dlp
install_brew_package pipx pipx

info "Ensuring pipx path"
pipx ensurepath

if has_command whisper; then
  info "whisper already installed"
else
  if pipx list --short 2>/dev/null | awk '{print $1}' | grep -qx "openai-whisper"; then
    info "openai-whisper is installed by pipx, but whisper is not on PATH"
    echo "Restart your terminal or run: source ~/.zshrc" >&2
  else
    info "Installing openai-whisper"
    pipx install openai-whisper
  fi
fi

info "Checking installed commands"
java -version
mvn -version
yt-dlp --version

if has_command whisper; then
  whisper --help >/dev/null
  info "whisper is available"
else
  echo "Warning: whisper is still not on PATH. Restart your terminal and run: whisper --help" >&2
fi

info "Done"
